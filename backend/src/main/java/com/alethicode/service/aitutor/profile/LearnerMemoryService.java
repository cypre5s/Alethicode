package com.alethicode.service.aitutor.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.aitutor.events.LearningEventPublisher;
import com.alethicode.service.rag.RagIndexQueueService;
import com.alethicode.service.rag.dto.RagEntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class LearnerMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LearnerMemoryService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LearningEventPublisher learningEventPublisher;
    private final RagIndexQueueService ragIndexQueue;

    @Autowired
    public LearnerMemoryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                LearningEventPublisher learningEventPublisher,
                                RagIndexQueueService ragIndexQueue) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.learningEventPublisher = learningEventPublisher;
        this.ragIndexQueue = ragIndexQueue;
    }

    public LearnerMemoryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, LearningEventPublisher.NOOP, RagIndexQueueService.NOOP);
    }

    public List<Map<String, Object>> listActiveMemoryRefs(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, memory_key, memory_type, memory_value, memory_payload::text as memory_payload_json,
                       confidence, source_problem_id, expires_at, updated_at,
                       coalesce(last_recalled_at, updated_at) as decay_reference,
                       recall_count
                from ai_learner_memory
                where user_id = ?
                  and enabled = true
                  and (expires_at is null or expires_at > now())
                order by updated_at desc
                limit 20
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    String memoryKey = rs.getString("memory_key");
                    row.put("memory_key", memoryKey);
                    String memoryType = trimToDefault(rs.getString("memory_type"), deriveMemoryType(memoryKey));
                    row.put("memory_type", memoryType);
                    row.put("memory_summary", deriveMemorySummary(rs.getString("memory_value"), rs.getString("memory_payload_json")));
                    row.put("confidence", decayConfidence(
                            rs.getDouble("confidence"), memoryType,
                            rs.getTimestamp("decay_reference"), rs.getInt("recall_count")));
                    row.put("source_problem_id", rs.getObject("source_problem_id"));
                    row.put("expires_at", rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant().toString());
                    row.put("recall_count", rs.getInt("recall_count"));
                    return row;
                },
                userId
        );
        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> ((Number) row.getOrDefault("confidence", 0.0)).doubleValue() >= PRUNE_THRESHOLD)
                .sorted((left, right) -> Double.compare(
                        ((Number) right.getOrDefault("confidence", 0.0)).doubleValue(),
                        ((Number) left.getOrDefault("confidence", 0.0)).doubleValue()
                ))
                .limit(5)
                .toList();
        if (!filtered.isEmpty()) {
            jdbcTemplate.update(
                    "update ai_learner_memory set last_recalled_at = now(), recall_count = recall_count + 1, confidence = least(1.0, confidence + 0.03) where user_id = ? and memory_key in (%s)"
                            .formatted(filtered.stream().map(row -> "?").collect(java.util.stream.Collectors.joining(","))),
                    buildRecallArgs(userId, filtered).toArray()
            );
        }
        return filtered;
    }

    public MemoryCandidate createCandidate(Long userId, Long problemId, String event, String summary, String memoryType, double confidence) {
        String memoryKey = "event:" + event + ":" + System.currentTimeMillis();
        MemoryScope scope = switch (memoryType) {
            case "error_pattern" -> MemoryScope.ERROR_PATTERN;
            case "learning_signal" -> MemoryScope.LEARNING_SIGNAL;
            case "reading_preference" -> MemoryScope.READING_PREFERENCE;
            case "debug_preference" -> MemoryScope.DEBUG_PREFERENCE;
            case "tutor_conclusion" -> MemoryScope.TUTOR_CONCLUSION;
            default -> MemoryScope.GENERIC;
        };
        return new MemoryCandidate(memoryKey, summary, memoryType, confidence, event, scope, problemId, java.time.Instant.now());
    }

    public MemorySaveDecision evaluateCandidate(MemoryCandidate candidate) {
        if (candidate.summary() == null || candidate.summary().isBlank()) {
            return MemorySaveDecision.DISCARD;
        }
        if (candidate.confidence() < 0.3) {
            return MemorySaveDecision.DISCARD;
        }
        if (candidate.confidence() < 0.6) {
            return MemorySaveDecision.DEFER;
        }
        return MemorySaveDecision.SAVE;
    }

    public void persistCandidate(Long userId, MemoryCandidate candidate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", candidate.summary());
        payload.put("source", candidate.source());
        payload.put("scope", candidate.scope() == null ? "generic" : candidate.scope().name().toLowerCase());

        // Phase 1 (V75 outbox): we no longer compute embeddings here. The 16-dim
        // pgvector dedup window (`findSemanticDuplicate`) is deleted with it; LightRAG
        // will dedup at the KG-entity level once Phase 3 cuts retrieval over. ON CONFLICT
        // on (user_id, memory_key) still coalesces re-emits of the same key.
        jdbcTemplate.update(
                """
                INSERT INTO ai_learner_memory(
                    user_id, memory_key, memory_value, confidence, source_problem_id,
                    expires_at, enabled, created_at, updated_at,
                    memory_type, memory_payload, source_type
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    now() + interval '60 day', true, now(), now(),
                    ?, cast(? as jsonb), 'event_driven'
                )
                ON CONFLICT (user_id, memory_key) DO UPDATE
                SET memory_value = excluded.memory_value,
                    confidence = excluded.confidence,
                    source_problem_id = excluded.source_problem_id,
                    expires_at = excluded.expires_at,
                    updated_at = now(),
                    memory_type = excluded.memory_type,
                    memory_payload = excluded.memory_payload
                """,
                userId, candidate.memoryKey(), candidate.summary(), candidate.confidence(),
                candidate.sourceProblemId(), candidate.memoryType(), toJson(payload)
        );

        Map<String, Object> indexMetadata = new LinkedHashMap<>();
        indexMetadata.put("user_id", userId);
        indexMetadata.put("memory_type", candidate.memoryType());
        indexMetadata.put("source_problem_id", candidate.sourceProblemId());
        indexMetadata.put("scope", candidate.scope() == null ? "generic" : candidate.scope().name().toLowerCase());
        indexMetadata.put("source", candidate.source());
        ragIndexQueue.enqueueIndex(
                RagEntityType.MEMORY,
                memoryEntityId(userId, candidate.memoryKey()),
                candidate.summary(),
                indexMetadata
        );

        learningEventPublisher.publishLearnerMemoryUpdated(
                userId,
                candidate.memoryKey(),
                candidate.memoryType(),
                candidate.sourceProblemId(),
                Map.of("action", "created", "confidence", candidate.confidence())
        );
    }

    private static String memoryEntityId(Long userId, String memoryKey) {
        return userId + ":" + memoryKey;
    }

    public void onEventCompleted(Long userId, Long problemId, String event, String summary) {
        if (userId == null || summary == null || summary.isBlank()) {
            return;
        }
        String memoryType = "AC_REVIEW".equals(event) ? "tutor_conclusion" : "learning_signal";
        double confidence = "AC_REVIEW".equals(event) ? 0.90 : 0.75;
        MemoryCandidate candidate = createCandidate(userId, problemId, event, summary, memoryType, confidence);
        MemorySaveDecision decision = evaluateCandidate(candidate);
        if (decision == MemorySaveDecision.SAVE) {
            persistCandidate(userId, candidate);
        }
    }

    /**
     * Saves a structured tutoring conclusion when a problem is solved (AC_REVIEW).
     * These conclusions persist across sessions and are loaded when the student
     * encounters a related problem, enabling cross-session feedback loops.
     */
    public void saveTutoringConclusion(Long userId, Long problemId,
                                       String strategyUsed, String keyInsight,
                                       List<String> weakKcsAddressed, String errorPattern) {
        if (userId == null || problemId == null) {
            return;
        }
        StringBuilder summary = new StringBuilder();
        summary.append("题目").append(problemId).append("教学结论：");
        if (strategyUsed != null && !strategyUsed.isBlank()) {
            summary.append("解题策略=").append(strategyUsed).append("；");
        }
        if (keyInsight != null && !keyInsight.isBlank()) {
            summary.append("关键洞察=").append(keyInsight).append("；");
        }
        if (weakKcsAddressed != null && !weakKcsAddressed.isEmpty()) {
            summary.append("涉及知识点=").append(String.join(",", weakKcsAddressed)).append("；");
        }
        if (errorPattern != null && !errorPattern.isBlank()) {
            summary.append("错误模式=").append(errorPattern);
        }
        MemoryCandidate candidate = createCandidate(userId, problemId,
                "TUTOR_CONCLUSION", summary.toString(), "tutor_conclusion", 0.92);
        persistCandidate(userId, candidate);
        log.debug("Saved tutoring conclusion for user={}, problem={}", userId, problemId);
    }

    /**
     * Loads previous session conclusions relevant to the current problem.
     * Used at session start to inject cross-session context into Agent prompts.
     */
    public List<Map<String, Object>> loadPreviousConclusions(Long userId, Long currentProblemId) {
        if (userId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                select memory_key, memory_value, memory_payload::text as memory_payload_json,
                       confidence, source_problem_id, updated_at
                from ai_learner_memory
                where user_id = ?
                  and memory_type = 'tutor_conclusion'
                  and enabled = true
                  and (expires_at is null or expires_at > now())
                order by confidence desc, updated_at desc
                limit 5
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("memory_key", rs.getString("memory_key"));
                    row.put("conclusion", rs.getString("memory_value"));
                    row.put("source_problem_id", rs.getObject("source_problem_id"));
                    row.put("confidence", rs.getDouble("confidence"));
                    row.put("updated_at", rs.getTimestamp("updated_at").toInstant().toString());
                    return row;
                },
                userId
        );
    }

    public void refreshFromSources(Long userId, Long problemId) {
        if (userId == null) {
            return;
        }
        syncNotebookMemories(userId, problemId);
        syncLearningEventMemories(userId, problemId);
    }

    private String deriveMemoryType(String memoryKey) {
        if (memoryKey == null || memoryKey.isBlank()) {
            return "generic";
        }
        if (memoryKey.contains("reading")) {
            return "reading_preference";
        }
        if (memoryKey.contains("debug")) {
            return "debug_preference";
        }
        return "generic";
    }

    private void syncNotebookMemories(Long userId, Long problemId) {
        List<Map<String, Object>> notebooks = jdbcTemplate.query(
                """
                select id, problem_id, error_taxonomy, root_cause, fix_outcome, student_reflection, tags::text as tags_json, update_time
                from ai_learner_notebook
                where user_id = ?
                  and is_deleted = false
                  and (? is null or problem_id = ?)
                order by update_time desc
                limit 20
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("problem_id", rs.getObject("problem_id"));
                    row.put("error_taxonomy", trimToDefault(rs.getString("error_taxonomy"), "unknown"));
                    row.put("root_cause", trimToDefault(rs.getString("root_cause"), ""));
                    row.put("fix_outcome", trimToDefault(rs.getString("fix_outcome"), ""));
                    row.put("student_reflection", trimToDefault(rs.getString("student_reflection"), ""));
                    row.put("tags_json", trimToDefault(rs.getString("tags_json"), "[]"));
                    row.put("update_time", rs.getTimestamp("update_time"));
                    return row;
                },
                userId,
                problemId,
                problemId
        );
        for (Map<String, Object> notebook : notebooks) {
            String summary = buildNotebookSummary(notebook);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("error_taxonomy", notebook.get("error_taxonomy"));
            payload.put("root_cause", notebook.get("root_cause"));
            payload.put("student_reflection", notebook.get("student_reflection"));
            payload.put("fix_outcome", notebook.get("fix_outcome"));
            payload.put("tags", parseJson(notebook.get("tags_json")));
            payload.put("source_notebook_id", notebook.get("id"));
            jdbcTemplate.update(
                    """
                    update ai_learner_notebook
                    set notebook_summary = ?, update_time = update_time
                    where id = ?
                    """,
                    summary,
                    notebook.get("id")
            );
            String memoryKey = "notebook:" + notebook.get("id");
            jdbcTemplate.update(
                    """
                    insert into ai_learner_memory(
                        user_id, memory_key, memory_value, confidence, source_problem_id, expires_at, enabled, created_at, updated_at,
                        memory_type, memory_payload, source_type
                    ) values (
                        ?, ?, ?, ?, ?, now() + interval '90 day', true, now(), now(),
                        'error_pattern', cast(? as jsonb), 'notebook'
                    )
                    on conflict (user_id, memory_key) do update
                    set memory_value = excluded.memory_value,
                        confidence = excluded.confidence,
                        source_problem_id = excluded.source_problem_id,
                        expires_at = excluded.expires_at,
                        enabled = excluded.enabled,
                        updated_at = now(),
                        memory_type = excluded.memory_type,
                        memory_payload = excluded.memory_payload,
                        source_type = excluded.source_type
                    """,
                    userId,
                    memoryKey,
                    summary,
                    0.85,
                    notebook.get("problem_id"),
                    toJson(payload)
            );

            // Two outbox rows here: one for the notebook entry itself (so SimilarErrorRetrieval
            // can find the rich error context once Phase 3 cuts over), and one for the
            // ai_learner_memory mirror (so LearnerMemorySemanticRetrievalService still has
            // a hit). They live in different namespaces (notebook / memory) so they don't
            // dedup against each other in LightRAG's KG.
            Map<String, Object> notebookMetadata = new LinkedHashMap<>();
            notebookMetadata.put("user_id", userId);
            notebookMetadata.put("problem_id", notebook.get("problem_id"));
            notebookMetadata.put("error_taxonomy", notebook.get("error_taxonomy"));
            notebookMetadata.put("root_cause", notebook.get("root_cause"));
            notebookMetadata.put("notebook_id", notebook.get("id"));
            ragIndexQueue.enqueueIndex(
                    RagEntityType.NOTEBOOK,
                    String.valueOf(notebook.get("id")),
                    summary,
                    notebookMetadata
            );

            Map<String, Object> memoryMetadata = new LinkedHashMap<>();
            memoryMetadata.put("user_id", userId);
            memoryMetadata.put("memory_type", "error_pattern");
            memoryMetadata.put("source_type", "notebook");
            memoryMetadata.put("source_notebook_id", notebook.get("id"));
            memoryMetadata.put("source_problem_id", notebook.get("problem_id"));
            ragIndexQueue.enqueueIndex(
                    RagEntityType.MEMORY,
                    memoryEntityId(userId, memoryKey),
                    summary,
                    memoryMetadata
            );
        }
    }

    private void syncLearningEventMemories(Long userId, Long problemId) {
        List<Map<String, Object>> events = jdbcTemplate.query(
                """
                select id, problem_id, event_type, extra_data::text as extra_data_json, created_at
                from ai_learning_event
                where user_id = ?
                  and (? is null or problem_id = ?)
                  and event_type in ('misconception_detected_ast', 'preflight_go_edit', 'preflight_force_submit', 'frustration_detected')
                order by created_at desc
                limit 20
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("problem_id", rs.getObject("problem_id"));
                    row.put("event_type", trimToDefault(rs.getString("event_type"), ""));
                    row.put("extra_data_json", trimToDefault(rs.getString("extra_data_json"), "{}"));
                    row.put("created_at", rs.getTimestamp("created_at"));
                    return row;
                },
                userId,
                problemId,
                problemId
        );
        for (Map<String, Object> event : events) {
            Map<String, Object> extraData = parseJson(event.get("extra_data_json"));
            String summary = buildLearningEventSummary(event, extraData);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("detector_name", extraData.getOrDefault("detector_name", ""));
            payload.put("hint", extraData.getOrDefault("hint", ""));
            payload.put("question", extraData.getOrDefault("question", ""));
            payload.put("source_event_id", event.get("id"));
            payload.put("event_type", event.get("event_type"));
            String memoryKey = "event:" + event.get("id");
            jdbcTemplate.update(
                    """
                    insert into ai_learner_memory(
                        user_id, memory_key, memory_value, confidence, source_problem_id, expires_at, enabled, created_at, updated_at,
                        memory_type, memory_payload, source_type
                    ) values (
                        ?, ?, ?, ?, ?, now() + interval '45 day', true, now(), now(),
                        'learning_signal', cast(? as jsonb), 'learning_event'
                    )
                    on conflict (user_id, memory_key) do update
                    set memory_value = excluded.memory_value,
                        confidence = excluded.confidence,
                        source_problem_id = excluded.source_problem_id,
                        expires_at = excluded.expires_at,
                        enabled = excluded.enabled,
                        updated_at = now(),
                        memory_type = excluded.memory_type,
                        memory_payload = excluded.memory_payload,
                        source_type = excluded.source_type
                    """,
                    userId,
                    memoryKey,
                    summary,
                    0.7,
                    event.get("problem_id"),
                    toJson(payload)
            );

            Map<String, Object> indexMetadata = new LinkedHashMap<>();
            indexMetadata.put("user_id", userId);
            indexMetadata.put("memory_type", "learning_signal");
            indexMetadata.put("source_type", "learning_event");
            indexMetadata.put("event_type", event.get("event_type"));
            indexMetadata.put("source_event_id", event.get("id"));
            indexMetadata.put("source_problem_id", event.get("problem_id"));
            ragIndexQueue.enqueueIndex(
                    RagEntityType.MEMORY,
                    memoryEntityId(userId, memoryKey),
                    summary,
                    indexMetadata
            );
        }
    }

    /**
     * 根据 {@code ai_learner_memory} 中累计的 {@code teaching_strategy_preference} 反馈推断学生偏好的教学风格。
     *
     * <p>策略：<br>
     *   - 至少累计 {@value #LEARNING_STYLE_MIN_FEEDBACK} 条反馈，否则返回 {@link LearningStyle#STEP_BY_STEP}（默认）；<br>
     *   - 根据 {@code memory_key=strategy_pref_{type}} 和 {@code memory_payload.rating} 投票到 4 种风格；<br>
     *   - {@code positive} +1 票，{@code negative} -0.5 票，最终取票数最高的风格；并列时回默认值。
     *
     * <p>映射规则（基于 {@link StrategyFeedbackService} 的 ALLOWED_STRATEGIES）：
     * <ul>
     *   <li>{@code worked_example} / {@code faded_example} → {@link LearningStyle#VISUAL}</li>
     *   <li>{@code minimal_hint} → {@link LearningStyle#EXPLORATORY}</li>
     *   <li>{@code error_diagnosis} / {@code problem_guide} → {@link LearningStyle#ANALYTICAL}</li>
     *   <li>{@code ideate_analysis} → {@link LearningStyle#STEP_BY_STEP}</li>
     *   <li>{@code post_ac} / {@code transfer_problem} 是通用反馈，不参与风格投票。</li>
     * </ul>
     */
    public LearningStyle inferLearningStyle(Long userId) {
        if (userId == null) {
            return LearningStyle.STEP_BY_STEP;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT memory_key,
                       memory_payload::text AS payload_text,
                       confidence
                FROM ai_learner_memory
                WHERE user_id = ?
                  AND memory_type = 'teaching_strategy_preference'
                  AND enabled = true
                """,
                userId
        );
        if (rows.size() < LEARNING_STYLE_MIN_FEEDBACK) {
            return LearningStyle.STEP_BY_STEP;
        }
        Map<LearningStyle, Double> votes = new java.util.EnumMap<>(LearningStyle.class);
        for (LearningStyle style : LearningStyle.values()) {
            votes.put(style, 0.0);
        }
        for (Map<String, Object> row : rows) {
            String memoryKey = String.valueOf(row.getOrDefault("memory_key", ""));
            String strategyType = memoryKey.startsWith("strategy_pref_")
                    ? memoryKey.substring("strategy_pref_".length())
                    : "";
            Map<String, Object> payload = parseJson(row.get("payload_text"));
            String rating = String.valueOf(payload.getOrDefault("rating", ""));
            double weight;
            if ("positive".equals(rating)) {
                weight = 1.0;
            } else if ("negative".equals(rating)) {
                weight = -0.5;
            } else {
                continue;
            }
            LearningStyle target = mapStrategyToStyle(strategyType);
            if (target == null) {
                continue;
            }
            votes.merge(target, weight, Double::sum);
        }
        LearningStyle winner = LearningStyle.STEP_BY_STEP;
        double best = Double.NEGATIVE_INFINITY;
        for (Map.Entry<LearningStyle, Double> entry : votes.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                winner = entry.getKey();
            } else if (entry.getValue() == best && entry.getKey() == LearningStyle.STEP_BY_STEP) {
                // 并列时保留默认
                winner = LearningStyle.STEP_BY_STEP;
            }
        }
        return best > 0.0 ? winner : LearningStyle.STEP_BY_STEP;
    }

    private static final int LEARNING_STYLE_MIN_FEEDBACK = 20;

    private static LearningStyle mapStrategyToStyle(String strategyType) {
        return switch (strategyType == null ? "" : strategyType.trim().toLowerCase()) {
            case "worked_example", "faded_example" -> LearningStyle.VISUAL;
            case "minimal_hint" -> LearningStyle.EXPLORATORY;
            case "error_diagnosis", "problem_guide" -> LearningStyle.ANALYTICAL;
            case "ideate_analysis" -> LearningStyle.STEP_BY_STEP;
            default -> null;
        };
    }

    private String buildNotebookSummary(Map<String, Object> notebook) {
        return "错误类型：" + notebook.get("error_taxonomy")
                + "；根因：" + notebook.get("root_cause")
                + "；反思：" + notebook.get("student_reflection")
                + "；修复结果：" + notebook.get("fix_outcome");
    }

    private String buildLearningEventSummary(Map<String, Object> event, Map<String, Object> extraData) {
        return "学习事件：" + event.get("event_type")
                + "；检测器：" + extraData.getOrDefault("detector_name", "")
                + "；问题：" + extraData.getOrDefault("question", "")
                + "；提示：" + extraData.getOrDefault("hint", "");
    }

    private static final Map<String, Double> DECAY_RATES = Map.of(
            "tutor_conclusion", 0.08,
            "error_pattern", 0.25,
            "learning_signal", 0.15,
            "reading_preference", 0.10,
            "debug_preference", 0.10,
            "generic", 0.15
    );

    private static final double RECALL_BOOST_FACTOR = 0.2;
    private static final double PRUNE_THRESHOLD = 0.05;

    /**
     * Ebbinghaus-inspired exponential decay with importance modulation.
     * strength = confidence × e^(-λ_eff × days) × (1 + recall_count × 0.2)
     * λ_eff = base_λ × (1 - confidence × 0.8)
     */
    private double decayConfidence(double confidence, String memoryType, Timestamp referenceTime, int recallCount) {
        Instant refInstant = referenceTime == null ? Instant.now() : referenceTime.toInstant();
        double days = Math.max(0.0, Duration.between(refInstant, Instant.now()).toHours() / 24.0);
        double baseLambda = DECAY_RATES.getOrDefault(
                memoryType == null ? "generic" : memoryType.toLowerCase(Locale.ROOT), 0.15);
        double lambdaEff = baseLambda * (1.0 - confidence * 0.8);
        double decayFactor = Math.exp(-lambdaEff * days);
        double recallBoost = 1.0 + recallCount * RECALL_BOOST_FACTOR;
        double strength = confidence * decayFactor * recallBoost;
        return Math.round(Math.max(0.0, Math.min(1.0, strength)) * 1000.0) / 1000.0;
    }

    private String deriveMemorySummary(String memoryValue, String memoryPayloadJson) {
        Map<String, Object> payload = parseJson(memoryPayloadJson);
        String summary = trimToDefault(String.valueOf(payload.getOrDefault("summary", payload.getOrDefault("memory_summary", ""))), "");
        if (!summary.isBlank()) {
            return summary;
        }
        return trimToDefault(memoryValue, "");
    }

    private String trimToDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private List<Object> buildRecallArgs(Long userId, List<Map<String, Object>> filtered) {
        List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        for (Map<String, Object> row : filtered) {
            args.add(row.get("memory_key"));
        }
        return args;
    }

    private Map<String, Object> parseJson(Object raw) {
        try {
            return objectMapper.readValue(String.valueOf(raw), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.debug("parseJson: failed, rawLength={}", String.valueOf(raw).length(), e);
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }
}
