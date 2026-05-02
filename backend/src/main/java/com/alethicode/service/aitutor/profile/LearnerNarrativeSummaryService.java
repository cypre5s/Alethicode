package com.alethicode.service.aitutor.profile;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情画像层 P1：自然语言长期学习摘要服务。
 *
 * 负责为每名活跃学生维护一段 ≤ 500 字的中文学习摘要：
 * - {@link #loadOrGenerate(Long)} 首次访问时立刻生成
 * - {@link #refreshIfStale(Long)} 在 AC_REVIEW / 学生进入题目页时按"新事件 ≥ 3"或"12h 陈旧"触发增量更新
 * - {@link #disablePersonalization(Long)} / {@link #enablePersonalization(Long)} 学生主动关闭/恢复个性化
 * - {@link #overrideSummary(Long, String)} 学生手动改写后 AI 不再覆盖
 *
 * 设计：docs/plans/2026-04-25-persistent-memory-layer-design.md §6.2.1
 */
@Service
public class LearnerNarrativeSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LearnerNarrativeSummaryService.class);

    /** 摘要正文最大字数；超出截断尾部。 */
    private static final int MAX_SUMMARY_CHARS = 500;

    /** 自上次摘要起新增事件 < 此值时跳过 refresh，避免抖动调 LLM。 */
    private static final int MIN_NEW_EVENTS_TO_REFRESH = 3;

    /** 摘要陈旧阈值；超过即允许 refresh 重生。 */
    private static final Duration MAX_STALENESS = Duration.ofHours(12);

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是教学画像生成助手。请基于以下学生数据，生成一段中文学习摘要。

            要求：
            - 字数 <= 500
            - 用第三人称、平实陈述（"该学生近 30 天做了 X 题..."）
            - 仅陈述事实，不提任何鼓励 / 评价 / 励志语
            - 不暴露内部检测器名（如 misconception_detected_ast）
            - 必须包含：题量统计、AC 率、最常涉及的 KC、仍在反复的错误模式（<=2 个）、最近一次错误概览
            - 输出 JSON：{"summary_text": "...", "top_kcs": [...], "top_errors": [...]}
            - 任一字段无法生成则返回该字段为空数组 / 空字符串，不要编造
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;
    private final LearnerMemoryService learnerMemoryService;
    private final ObjectMapper objectMapper;

    public LearnerNarrativeSummaryService(JdbcTemplate jdbcTemplate,
                                          AiModelGateway aiModelGateway,
                                          LearnerMemoryService learnerMemoryService,
                                          ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
        this.learnerMemoryService = learnerMemoryService;
        this.objectMapper = objectMapper;
    }

    public NarrativeSummary loadOrGenerate(Long userId) {
        if (userId == null) {
            return NarrativeSummary.empty(0L);
        }
        NarrativeSummary existing = loadRow(userId);
        if (existing != null) {
            return existing;
        }
        return generateAndPersist(userId, /* incrementVersion */ false);
    }

    /** 学生进入题目页 / AC_REVIEW 完成时调；若摘要新鲜或新事件不足，直接复用。 */
    public NarrativeSummary refreshIfStale(Long userId) {
        if (userId == null) {
            return NarrativeSummary.empty(0L);
        }
        NarrativeSummary existing = loadRow(userId);
        if (existing == null) {
            return generateAndPersist(userId, false);
        }
        if (existing.userOverridden()) {
            // 学生改写过，AI 不再覆盖
            return existing;
        }
        if (Duration.between(existing.updatedAt(), Instant.now()).compareTo(MAX_STALENESS) < 0
                && countNewEvents(userId, existing.lastEventId()) < MIN_NEW_EVENTS_TO_REFRESH) {
            return existing;
        }
        return generateAndPersist(userId, true);
    }

    public void disablePersonalization(Long userId) {
        if (userId == null) {
            return;
        }
        upsertFlag(userId, /* userDisabled */ true);
    }

    public void enablePersonalization(Long userId) {
        if (userId == null) {
            return;
        }
        upsertFlag(userId, false);
    }

    public void overrideSummary(Long userId, String text) {
        if (userId == null || text == null) {
            return;
        }
        String safe = text.length() > MAX_SUMMARY_CHARS ? text.substring(0, MAX_SUMMARY_CHARS) : text;
        jdbcTemplate.update(
                """
                insert into ai_learner_narrative_summary
                    (user_id, summary_version, summary_text, summary_payload, learning_style_key,
                     is_user_overridden, user_disabled, created_at, updated_at)
                values (?, 1, ?, '{}'::jsonb, ?, true, false, now(), now())
                on conflict (user_id) do update
                set summary_version = ai_learner_narrative_summary.summary_version + 1,
                    summary_text = excluded.summary_text,
                    summary_payload = excluded.summary_payload,
                    is_user_overridden = true,
                    updated_at = now()
                """,
                userId, safe,
                learnerMemoryService.inferLearningStyle(userId).key()
        );
    }

    /** 学生主动 enabled/disabled 标记不动 summary_text 内容，仅切换开关。 */
    private void upsertFlag(Long userId, boolean userDisabled) {
        int updated = jdbcTemplate.update(
                """
                update ai_learner_narrative_summary
                set user_disabled = ?, updated_at = now()
                where user_id = ?
                """,
                userDisabled, userId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    insert into ai_learner_narrative_summary
                        (user_id, summary_version, summary_text, summary_payload, learning_style_key,
                         is_user_overridden, user_disabled, created_at, updated_at)
                    values (?, 0, '', '{}'::jsonb, ?, false, ?, now(), now())
                    """,
                    userId, learnerMemoryService.inferLearningStyle(userId).key(), userDisabled
            );
        }
    }

    private NarrativeSummary loadRow(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select user_id, summary_version, summary_text,
                           summary_payload::text as payload_json,
                           learning_style_key, last_event_id, last_session_id,
                           is_user_overridden, user_disabled,
                           created_at, updated_at
                    from ai_learner_narrative_summary
                    where user_id = ?
                    """,
                    (rs, rowNum) -> new NarrativeSummary(
                            rs.getLong("user_id"),
                            rs.getInt("summary_version"),
                            rs.getString("summary_text"),
                            parsePayload(rs.getString("payload_json")),
                            rs.getString("learning_style_key"),
                            (Long) rs.getObject("last_event_id"),
                            rs.getString("last_session_id"),
                            rs.getBoolean("is_user_overridden"),
                            rs.getBoolean("user_disabled"),
                            timestampToInstant(rs.getTimestamp("created_at")),
                            timestampToInstant(rs.getTimestamp("updated_at"))
                    ),
                    userId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private NarrativeSummary generateAndPersist(Long userId, boolean incrementVersion) {
        ProfileMaterials materials = collectMaterials(userId);
        Map<String, Object> raw;
        try {
            raw = aiModelGateway.callForJson(SUMMARY_SYSTEM_PROMPT, buildUserPrompt(materials));
        } catch (Exception ex) {
            log.warn("narrative summary LLM call failed for userId={}, will not overwrite existing row", userId, ex);
            // failfast：返回上一版本（如果有），首次失败返回空
            NarrativeSummary existing = loadRow(userId);
            return existing != null ? existing : NarrativeSummary.empty(userId);
        }
        String summaryText = stringOf(raw.get("summary_text"));
        if (summaryText.length() > MAX_SUMMARY_CHARS) {
            summaryText = summaryText.substring(0, MAX_SUMMARY_CHARS);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("top_kcs", raw.getOrDefault("top_kcs", List.of()));
        payload.put("top_errors", raw.getOrDefault("top_errors", List.of()));
        payload.put("stats_30d", materials.stats());
        String payloadJson = toJson(payload);
        String styleKey = learnerMemoryService.inferLearningStyle(userId).key();

        jdbcTemplate.update(
                """
                insert into ai_learner_narrative_summary
                    (user_id, summary_version, summary_text, summary_payload, learning_style_key,
                     last_event_id, is_user_overridden, user_disabled, created_at, updated_at)
                values (?, 1, ?, cast(? as jsonb), ?, ?, false, false, now(), now())
                on conflict (user_id) do update
                set summary_version = case when ? then ai_learner_narrative_summary.summary_version + 1
                                            else ai_learner_narrative_summary.summary_version end,
                    summary_text = excluded.summary_text,
                    summary_payload = excluded.summary_payload,
                    learning_style_key = excluded.learning_style_key,
                    last_event_id = excluded.last_event_id,
                    is_user_overridden = false,
                    updated_at = now()
                """,
                userId, summaryText, payloadJson, styleKey, materials.lastEventId(),
                incrementVersion
        );
        return loadRow(userId);
    }

    private ProfileMaterials collectMaterials(Long userId) {
        Long submissionsAttempted = jdbcTemplate.queryForObject(
                "select count(distinct problem_id) from submission where user_id = ? and create_time > now() - interval '30 day'",
                Long.class, userId
        );
        Long submissionsAc = jdbcTemplate.queryForObject(
                "select count(distinct problem_id) from submission where user_id = ? and result = 0 and create_time > now() - interval '30 day'",
                Long.class, userId
        );
        Long maxEventId = jdbcTemplate.queryForObject(
                "select coalesce(max(id), 0) from ai_learning_event where user_id = ?",
                Long.class, userId
        );
        List<Map<String, Object>> recentNotebooks = jdbcTemplate.queryForList(
                """
                select error_taxonomy, root_cause, update_time
                from ai_learner_notebook
                where user_id = ? and is_deleted = false
                order by update_time desc limit 5
                """,
                userId
        );
        List<Map<String, Object>> weakKcs = jdbcTemplate.queryForList(
                """
                select k.name as kc_name, km.mastery
                from learner_kc_mastery km
                join language_pack_kc k on k.id = km.kc_id
                where km.user_id = ? and km.mastery < 0.6
                order by km.mastery asc limit 5
                """,
                userId
        );
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("problems_attempted_30d", submissionsAttempted == null ? 0 : submissionsAttempted);
        stats.put("problems_ac_30d", submissionsAc == null ? 0 : submissionsAc);
        if (submissionsAttempted != null && submissionsAttempted > 0) {
            stats.put("ac_rate_30d", Math.round((double) submissionsAc / submissionsAttempted * 1000.0) / 1000.0);
        } else {
            stats.put("ac_rate_30d", 0.0);
        }
        return new ProfileMaterials(stats, recentNotebooks, weakKcs, maxEventId == null ? 0L : maxEventId);
    }

    private String buildUserPrompt(ProfileMaterials materials) {
        StringBuilder sb = new StringBuilder();
        sb.append("近 30 天做题统计：").append(toJson(materials.stats())).append('\n');
        sb.append("最近 5 条错题（含原因）：").append(toJson(materials.recentNotebooks())).append('\n');
        sb.append("当前掌握度低于 0.6 的知识点：").append(toJson(materials.weakKcs())).append('\n');
        return sb.toString();
    }

    private long countNewEvents(Long userId, Long sinceEventId) {
        Long n;
        if (sinceEventId == null || sinceEventId == 0L) {
            n = jdbcTemplate.queryForObject(
                    "select count(*) from ai_learning_event where user_id = ?",
                    Long.class, userId
            );
        } else {
            n = jdbcTemplate.queryForObject(
                    "select count(*) from ai_learning_event where user_id = ? and id > ?",
                    Long.class, userId, sinceEventId
            );
        }
        return n == null ? 0L : n;
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }

    private static String stringOf(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Instant timestampToInstant(Timestamp ts) {
        return ts == null ? Instant.now() : ts.toInstant();
    }

    public record NarrativeSummary(
            Long userId,
            int version,
            String summaryText,
            Map<String, Object> payload,
            String learningStyleKey,
            Long lastEventId,
            String lastSessionId,
            boolean userOverridden,
            boolean userDisabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static NarrativeSummary empty(Long userId) {
            return new NarrativeSummary(userId, 0, "", Map.of(),
                    "step_by_step", null, null, false, false, Instant.now(), Instant.now());
        }
    }

    private record ProfileMaterials(
            Map<String, Object> stats,
            List<Map<String, Object>> recentNotebooks,
            List<Map<String, Object>> weakKcs,
            Long lastEventId
    ) {}
}
