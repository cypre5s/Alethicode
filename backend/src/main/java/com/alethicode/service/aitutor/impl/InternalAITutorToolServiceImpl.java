package com.alethicode.service.aitutor.impl;

import com.alethicode.service.aitutor.InternalAITutorToolService;
import com.alethicode.service.aitutor.SessionUsage;
import com.alethicode.service.aitutor.context.CardSummary;
import com.alethicode.service.aitutor.context.ConversationContextService;
import com.alethicode.service.aitutor.context.ConversationMode;
import com.alethicode.service.aitutor.context.CoursewareContextProvider;
import com.alethicode.service.aitutor.context.CoursewareSummary;
import com.alethicode.service.aitutor.context.KcContextProvider;
import com.alethicode.service.aitutor.context.KcSummary;
import com.alethicode.service.aitutor.context.NotebookContextProvider;
import com.alethicode.service.aitutor.context.NotebookSummary;
import com.alethicode.service.aitutor.context.PageContextProvider;
import com.alethicode.service.aitutor.context.PageSummary;
import com.alethicode.service.aitutor.context.ReferenceResolver;
import com.alethicode.service.aitutor.profile.ContextSignals;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.profile.LearnerState;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import com.alethicode.service.aitutor.retrieval.SimilarErrorRetrievalService;
import com.alethicode.service.aitutor.parsons.ParsonsCapabilityService;
import com.alethicode.service.aitutor.visualize.VisualizeCapabilityService;
import com.alethicode.service.aitutor.visualize.VisualizeIntent;
import com.alethicode.service.aitutor.visualize.VisualizeRequest;
import com.alethicode.service.aitutor.visualize.VisualizeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InternalAITutorToolServiceImpl implements InternalAITutorToolService {

    private static final Logger log = LoggerFactory.getLogger(InternalAITutorToolServiceImpl.class);
    /** Match the evidence pack assembler used by the legacy Java tutor so card payloads stay consistent. */
    private static final int COURSEWARE_HIT_LIMIT = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final CoursewareRetrievalService coursewareRetrievalService;
    private final SimilarErrorRetrievalService similarErrorRetrievalService;
    private final LearnerProfileProjector learnerProfileProjector;
    private final VisualizeCapabilityService visualizeCapabilityService;
    private final ConversationContextService conversationContextService;
    private final CoursewareContextProvider coursewareContextProvider;
    private final PageContextProvider pageContextProvider;
    private final KcContextProvider kcContextProvider;
    private final NotebookContextProvider notebookContextProvider;
    private final ParsonsCapabilityService parsonsCapabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalAITutorToolServiceImpl(NamedParameterJdbcTemplate jdbc,
                                          CoursewareRetrievalService coursewareRetrievalService,
                                          SimilarErrorRetrievalService similarErrorRetrievalService,
                                          LearnerProfileProjector learnerProfileProjector,
                                          VisualizeCapabilityService visualizeCapabilityService,
                                          ConversationContextService conversationContextService,
                                          CoursewareContextProvider coursewareContextProvider,
                                          PageContextProvider pageContextProvider,
                                          KcContextProvider kcContextProvider,
                                          NotebookContextProvider notebookContextProvider,
                                          ParsonsCapabilityService parsonsCapabilityService) {
        this.jdbc = jdbc;
        this.coursewareRetrievalService = coursewareRetrievalService;
        this.similarErrorRetrievalService = similarErrorRetrievalService;
        this.learnerProfileProjector = learnerProfileProjector;
        this.visualizeCapabilityService = visualizeCapabilityService;
        this.conversationContextService = conversationContextService;
        this.coursewareContextProvider = coursewareContextProvider;
        this.pageContextProvider = pageContextProvider;
        this.kcContextProvider = kcContextProvider;
        this.notebookContextProvider = notebookContextProvider;
        this.parsonsCapabilityService = parsonsCapabilityService;
    }

    @Override
    public Map<String, Object> getWorkflowContext(Long problemId, Long userId, String sessionId, String language) {
        if (problemId == null || userId == null || sessionId == null || language == null) {
            throw new IllegalArgumentException("problemId, userId, sessionId, and language are required");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, _id, title, description, input_description, output_description, " +
                        "hint, languages::text AS languages_json, difficulty, visible, is_public, " +
                        "samples::text AS samples_json, created_by_id " +
                        "FROM problem WHERE id = :pid",
                new MapSqlParameterSource("pid", problemId)
        );

        if (rows.isEmpty()) {
            throw new ProblemNotFoundException("Problem not found: " + problemId);
        }

        Map<String, Object> problem = rows.get(0);
        boolean visible = Boolean.TRUE.equals(problem.get("visible"));
        Long ownerId = toLong(problem.get("created_by_id"));
        if (!visible && (ownerId == null || !ownerId.equals(userId))) {
            throw new SecurityException("Problem not accessible to user");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("problem_id", problemId);
        result.put("display_id", problem.get("_id"));
        result.put("statement", problem.get("description"));
        result.put("title", problem.get("title"));
        result.put("input_description", problem.get("input_description"));
        result.put("output_description", problem.get("output_description"));
        result.put("hint", problem.get("hint"));
        result.put("languages", parseJsonOrDefault(problem.get("languages_json"), List.of()));
        result.put("samples", parseJsonOrDefault(problem.get("samples_json"), List.of()));
        result.put("difficulty", problem.get("difficulty"));
        result.put("language", language);

        return result;
    }

    @Override
    public Map<String, Object> getDiagnosisEvidence(String submissionId, Long userId, Long problemId, String sessionId) {
        if (submissionId == null || userId == null || problemId == null) {
            throw new IllegalArgumentException("submissionId, userId, and problemId are required");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, problem_id, user_id, code, language, result, " +
                        "info::text AS info_json, statistic_info::text AS statistic_info_json, create_time " +
                        "FROM submission WHERE id = :sid",
                new MapSqlParameterSource("sid", submissionId)
        );

        if (rows.isEmpty()) {
            throw new ProblemNotFoundException("Submission not found: " + submissionId);
        }

        Map<String, Object> sub = rows.get(0);
        Long subUserId = toLong(sub.get("user_id"));
        Long subProblemId = toLong(sub.get("problem_id"));
        if (subUserId == null || !userId.equals(subUserId)) {
            throw new SecurityException("Submission does not belong to user");
        }
        if (subProblemId == null || !problemId.equals(subProblemId)) {
            throw new SecurityException("Submission does not belong to problem");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("submission_id", submissionId);
        result.put("problem_id", subProblemId);
        result.put("user_id", subUserId);
        result.put("code", sub.get("code"));
        result.put("language", sub.get("language"));
        result.put("result", sub.get("result"));
        result.put("info", parseJsonOrDefault(sub.get("info_json"), Map.of()));
        result.put("statistic_info", parseJsonOrDefault(sub.get("statistic_info_json"), Map.of()));
        Object createTime = sub.get("create_time");
        result.put("created_at", createTime != null ? createTime.toString() : null);
        return result;
    }

    @Override
    public Map<String, Object> getLearnerState(Long userId, Long problemId, String sessionId, String language,
                                               ContextSignals contextSignals) {
        if (userId == null || problemId == null) {
            throw new IllegalArgumentException("userId and problemId are required");
        }

        Map<String, Object> behaviorMetrics = loadLatestBehaviorMetrics(sessionId);
        String currentPhase = sessionId == null ? "" : loadSessionPhase(sessionId);
        LearnerState projected = learnerProfileProjector.project(
                userId, problemId, behaviorMetrics, currentPhase, contextSignals);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("user_id", userId);
        state.put("problem_id", problemId);
        state.put("language", language);
        state.putAll(projected.toMap());

        Long totalSubs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM submission WHERE user_id = :uid AND problem_id = :pid",
                new MapSqlParameterSource().addValue("uid", userId).addValue("pid", problemId),
                Long.class
        );
        state.put("submission_count", totalSubs != null ? totalSubs : 0L);

        Long acCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM submission WHERE user_id = :uid AND problem_id = :pid AND result = 0",
                new MapSqlParameterSource().addValue("uid", userId).addValue("pid", problemId),
                Long.class
        );
        state.put("ac_count", acCount != null ? acCount : 0L);

        return state;
    }

    @Override
    public Map<String, Object> getCoursewareHits(Long problemId, Long userId, String sessionId) {
        if (problemId == null) {
            throw new IllegalArgumentException("problemId is required");
        }
        // Language pack hits are the primary signal when the problem belongs to a pack; otherwise the
        // retrieval service falls back to KC and chapter indices on the legacy courseware chunk table.
        Long languagePackId = resolveLanguagePackId(problemId);
        List<Long> kcIds = coursewareRetrievalService.loadProblemKcIds(problemId, languagePackId);
        String chapter = coursewareRetrievalService.loadPrimaryChapter(problemId, languagePackId);
        List<Map<String, Object>> hits = coursewareRetrievalService.retrieve(
                problemId, kcIds, chapter, COURSEWARE_HIT_LIMIT, languagePackId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("problem_id", problemId);
        result.put("language_pack_id", languagePackId);
        result.put("kc_ids", kcIds);
        result.put("chapter", chapter == null ? "" : chapter);
        result.put("hits", hits == null ? List.of() : hits);
        return result;
    }

    @Override
    public Map<String, Object> getSimilarErrors(Long userId, Long problemId, String sessionId, String language) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        // Resolve taxonomy + query text from a single event lookup to avoid N+1 on hot paths
        // (ERROR_FEEDBACK fires on every WA submission).
        LatestErrorContext errorContext = sessionId == null
                ? LatestErrorContext.EMPTY
                : loadLatestErrorContext(sessionId);

        Map<String, List<Map<String, Object>>> retrieved = similarErrorRetrievalService.retrieve(
                userId, problemId, language, errorContext.taxonomy(), errorContext.queryText());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("problem_id", problemId);
        result.put("language", language);
        result.put("error_taxonomy", errorContext.taxonomy());
        // Keep the legacy `similar_errors` key so existing Python tutor_graph consumers
        // continue to work while exposing the full decomposition for callers that need it.
        List<Map<String, Object>> notebookHits = retrieved.getOrDefault("similar_notebook_hits", List.of());
        List<Map<String, Object>> memoryHits = retrieved.getOrDefault("similar_memory_hits", List.of());
        result.put("similar_notebook_hits", notebookHits);
        result.put("similar_memory_hits", memoryHits);
        result.put("similar_errors", notebookHits);
        return result;
    }

    private record LatestErrorContext(String taxonomy, String queryText) {
        private static final LatestErrorContext EMPTY = new LatestErrorContext("", "");
    }

    private Map<String, Object> loadLatestBehaviorMetrics(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT behavior_metrics::text AS bm FROM ai_tutor_workflow_session " +
                        "WHERE session_id = :sid",
                new MapSqlParameterSource("sid", sessionId)
        );
        if (rows.isEmpty()) return Map.of();
        Object raw = rows.get(0).get("bm");
        Object parsed = parseJsonOrDefault(raw, Map.of());
        return (parsed instanceof Map) ? (Map<String, Object>) parsed : Map.of();
    }

    private String loadSessionPhase(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT phase FROM ai_tutor_workflow_session WHERE session_id = :sid",
                new MapSqlParameterSource("sid", sessionId)
        );
        if (rows.isEmpty()) return "";
        Object phase = rows.get(0).get("phase");
        return phase == null ? "" : phase.toString();
    }

    /**
     * Read the latest ERROR_FEEDBACK event for the session and extract both the
     * taxonomy (from {@code node_outputs.error_diagnosis.error_pattern}) and the
     * query text (root cause summary, falling back to the student's raw err_info).
     *
     * <p>Single SQL round-trip: ERROR_FEEDBACK fires on every WA submission, so the
     * two earlier per-field methods doubled the query count on the hot path.
     */
    private LatestErrorContext loadLatestErrorContext(String sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_data::text AS ed FROM ai_tutor_workflow_event " +
                        "WHERE session_id = :sid AND client_event = 'ERROR_FEEDBACK' " +
                        "ORDER BY created_at DESC LIMIT 1",
                new MapSqlParameterSource("sid", sessionId)
        );
        if (rows.isEmpty()) return LatestErrorContext.EMPTY;
        Object parsed = parseJsonOrDefault(rows.get(0).get("ed"), Map.of());
        if (!(parsed instanceof Map<?, ?> rawMap)) return LatestErrorContext.EMPTY;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;

        String taxonomy = "";
        String queryText = "";
        Object nodeOutputs = map.get("node_outputs");
        if (nodeOutputs instanceof Map<?, ?> m) {
            Object diag = m.get("error_diagnosis");
            if (diag instanceof Map<?, ?> d) {
                Object pattern = d.get("error_pattern");
                if (pattern != null) taxonomy = String.valueOf(pattern);
                Object rootCause = d.get("root_cause");
                if (rootCause != null && !rootCause.toString().isBlank()) {
                    queryText = rootCause.toString();
                }
            }
        }
        if (queryText.isEmpty()) {
            Object eventData = map.get("event_data");
            if (eventData instanceof Map<?, ?> ed) {
                Object info = ed.get("err_info");
                if (info != null && !info.toString().isBlank()) {
                    queryText = info.toString();
                }
            }
        }
        return new LatestErrorContext(taxonomy, queryText);
    }

    private Long resolveLanguagePackId(Long problemId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT language_pack_id FROM ai_problem_kc_mapping " +
                        "WHERE problem_id = :pid AND language_pack_id IS NOT NULL LIMIT 1",
                new MapSqlParameterSource("pid", problemId)
        );
        if (rows.isEmpty()) return null;
        Object raw = rows.get(0).get("language_pack_id");
        if (raw instanceof Number n) return n.longValue();
        return null;
    }

    @Override
    @Transactional
    public Map<String, Object> createTransferProblem(Map<String, Object> request) {
        String idempotencyKey = (String) request.get("idempotency_key");
        String requestHash = (String) request.get("request_hash");
        String sessionId = (String) request.get("session_id");
        String runId = (String) request.get("run_id");
        Long userId = toLong(request.get("user_id"));
        Long sourceProblemId = toLong(request.get("source_problem_id"));

        if (idempotencyKey == null || requestHash == null || userId == null || sourceProblemId == null) {
            throw new IllegalArgumentException(
                    "idempotency_key, request_hash, user_id, source_problem_id are required");
        }

        Long accessible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM problem " +
                        "WHERE id = :pid AND (visible = true OR is_public = true OR created_by_id = :uid)",
                new MapSqlParameterSource().addValue("pid", sourceProblemId).addValue("uid", userId),
                Long.class);
        if (accessible == null || accessible == 0) {
            throw new SecurityException("Source problem not accessible to user");
        }

        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT request_hash, result_json::text AS result_json_text " +
                        "FROM ai_tutor_side_effect_log WHERE idempotency_key = :key",
                new MapSqlParameterSource("key", idempotencyKey)
        );

        if (!existing.isEmpty()) {
            String existingHash = (String) existing.get(0).get("request_hash");
            if (!requestHash.equals(existingHash)) {
                throw new IllegalStateException("idempotency_key conflict: same key but different request hash");
            }
            String cachedJson = (String) existing.get(0).get("result_json_text");
            if (cachedJson == null) {
                throw new RuntimeException("Corrupted cached result for key: " + idempotencyKey);
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cached = objectMapper.readValue(cachedJson, Map.class);
                return cached;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse cached result: " + e.getMessage(), e);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) request.get("draft");
        if (draft == null) {
            throw new IllegalArgumentException("draft is required");
        }

        String title = (String) draft.getOrDefault("title", "Transfer Problem");
        String description = (String) draft.getOrDefault("description", "");
        String inputDescription = (String) draft.getOrDefault("input_description", "");
        String outputDescription = (String) draft.getOrDefault("output_description", "");
        String hint = (String) draft.get("hint");
        String refLang = (String) draft.getOrDefault("reference_solution_language", "Python3");
        String refCode = (String) draft.get("reference_solution_code");

        Object samplesObj = draft.getOrDefault("samples", List.of());
        String samplesJson;
        String languagesJson;
        try {
            samplesJson = objectMapper.writeValueAsString(samplesObj);
            languagesJson = objectMapper.writeValueAsString(List.of(refLang));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize draft fields", e);
        }

        String displayId = "T" + sourceProblemId + "-" + System.currentTimeMillis();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
                "INSERT INTO problem (_id, title, description, input_description, output_description, " +
                        "hint, languages, samples, reference_solution_language, reference_solution_code, " +
                        "visible, is_public, created_by_id, create_time, ai_source_problem_id, " +
                        "is_ai_generated, visibility_status, difficulty) " +
                        "VALUES (:display, :title, :desc, :idesc, :odesc, :hint, :langs::jsonb, :samples::jsonb, " +
                        ":reflang, :refcode, false, false, :uid, NOW(), :src, true, 'class_private', 'Low')",
                new MapSqlParameterSource()
                        .addValue("display", displayId)
                        .addValue("title", title)
                        .addValue("desc", description)
                        .addValue("idesc", inputDescription)
                        .addValue("odesc", outputDescription)
                        .addValue("hint", hint)
                        .addValue("langs", languagesJson)
                        .addValue("samples", samplesJson)
                        .addValue("reflang", refLang)
                        .addValue("refcode", refCode)
                        .addValue("uid", userId)
                        .addValue("src", sourceProblemId),
                keyHolder,
                new String[]{"id"}
        );

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new RuntimeException("Failed to obtain generated problem id");
        }
        Long newProblemId = generatedKey.longValue();

        Map<String, Object> result = new HashMap<>();
        result.put("problem_id", newProblemId);
        result.put("problem_display_id", displayId);
        result.put("temporary_problem", true);
        result.put("ai_tutor_enabled", false);

        String resultJsonStr;
        try {
            resultJsonStr = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize result", e);
        }

        jdbc.update(
                "INSERT INTO ai_tutor_side_effect_log (idempotency_key, session_id, run_id, effect_type, " +
                        "request_hash, result_json) " +
                        "VALUES (:key, :sid, :rid, 'transfer_problem', :hash, :result::jsonb)",
                new MapSqlParameterSource()
                        .addValue("key", idempotencyKey)
                        .addValue("sid", sessionId)
                        .addValue("rid", runId)
                        .addValue("hash", requestHash)
                        .addValue("result", resultJsonStr)
        );

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> recordWorkflowEvent(Map<String, Object> request) {
        String sessionId = (String) request.get("session_id");
        String runId = (String) request.get("run_id");
        String threadId = (String) request.get("thread_id");

        if (sessionId == null || runId == null) {
            throw new IllegalArgumentException("session_id and run_id are required");
        }

        String eventDataJson;
        try {
            eventDataJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Failed to serialize event_data for session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to serialize workflow event payload", e);
        }

        jdbc.update(
                "INSERT INTO ai_tutor_workflow_event (session_id, run_id, thread_id, event_type, runtime_state, " +
                        "server_event, client_event, failure_bucket, trace_id, event_data) " +
                        "VALUES (:sid, :rid, :tid, :et, :rs, :se, :ce, :fb, :ti, :ed::jsonb)",
                new MapSqlParameterSource()
                        .addValue("sid", sessionId)
                        .addValue("rid", runId)
                        .addValue("tid", threadId != null ? threadId : "")
                        .addValue("et", request.getOrDefault("event_type", ""))
                        .addValue("rs", request.get("runtime_state"))
                        .addValue("se", request.get("server_event"))
                        .addValue("ce", request.get("client_event"))
                        .addValue("fb", request.get("failure_bucket"))
                        .addValue("ti", request.get("trace_id"))
                        .addValue("ed", eventDataJson)
        );

        String nodeOutputsJson;
        String availableActionsJson;
        String behaviorMetricsJson;
        String planJson;
        try {
            nodeOutputsJson = objectMapper.writeValueAsString(request.getOrDefault("node_outputs", Map.of()));
            availableActionsJson = objectMapper.writeValueAsString(request.getOrDefault("available_actions", List.of()));
            behaviorMetricsJson = objectMapper.writeValueAsString(request.getOrDefault("behavior_metrics", Map.of()));
            planJson = objectMapper.writeValueAsString(request.getOrDefault("plan", Map.of()));
        } catch (Exception e) {
            log.error("Failed to serialize node_outputs/available_actions for session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to serialize projection payload", e);
        }

        int rowsUpdated = jdbc.update(
                "UPDATE ai_tutor_workflow_session SET " +
                        "phase = :phase, runtime_state = :rs, pending_human_action = :pha, " +
                        "node_outputs = :no::jsonb, behavior_metrics = :bm::jsonb, available_actions = :aa::jsonb, " +
                        "plan = :plan::jsonb, recommendation_reason = :rr, " +
                        "last_run_id = :rid, updated_at = NOW() " +
                        "WHERE session_id = :sid",
                new MapSqlParameterSource()
                        .addValue("sid", sessionId)
                        .addValue("phase", request.getOrDefault("phase", "READING"))
                        .addValue("rs", request.getOrDefault("runtime_state", "COMPLETED"))
                        .addValue("pha", request.getOrDefault("pending_human_action", ""))
                        .addValue("no", nodeOutputsJson)
                        .addValue("bm", behaviorMetricsJson)
                        .addValue("aa", availableActionsJson)
                        .addValue("plan", planJson)
                        .addValue("rr", request.getOrDefault("recommendation_reason", ""))
                        .addValue("rid", runId)
        );

        if (rowsUpdated == 0) {
            log.warn("recordWorkflowEvent: session {} not found in projection (event skipped for session update)", sessionId);
        }

        if (rowsUpdated > 0) {
            stampCardForCompletedEvent(request, sessionId, runId);
        }

        return Map.of("status", "recorded", "session_updated", rowsUpdated > 0);
    }

    private void stampCardForCompletedEvent(Map<String, Object> request, String sessionId, String runId) {
        String runtimeState = asString(request.getOrDefault("runtime_state", "COMPLETED"));
        if (!"COMPLETED".equals(runtimeState)) {
            return;
        }
        Map<String, Object> nodeOutputs = castStringKeyMap(request.get("node_outputs"));
        String cardType = cardTypeForEvent(asString(request.get("client_event")), nodeOutputs);
        if (cardType.isBlank()) {
            return;
        }
        ConversationMode activeMode = conversationContextService.getActiveMode(sessionId);
        conversationContextService.stampCardForLatestEvent(
                sessionId,
                runId,
                cardType,
                activeMode,
                extractReferencedCardIds(nodeOutputs)
        );
    }

    private String cardTypeForEvent(String clientEvent, Map<String, Object> nodeOutputs) {
        return switch (clientEvent) {
            case "READING" -> "problem_guide";
            case "IDEATING" -> "ideate_analysis";
            case "SKELETON" -> "skeleton_code";
            case "ERROR_FEEDBACK" -> "error_diagnosis";
            case "AC_REVIEW" -> "post_ac";
            case "TRANSFER" -> "transfer_problem";
            case "CHAT" -> "ai_reply";
            case "KNOWLEDGE_REVIEW" -> "knowledge_review";
            case "VISUALIZE" -> "visualize";
            case "CODING" -> nodeOutputs.containsKey("execution_trace_explainer")
                    ? "execution_trace_explainer" : "";
            default -> "";
        };
    }

    private List<String> extractReferencedCardIds(Map<String, Object> nodeOutputs) {
        Object chatOutput = nodeOutputs.get("chat");
        if (!(chatOutput instanceof Map<?, ?> chatMap)) {
            return List.of();
        }
        Object rawIds = chatMap.get("referenced_card_ids");
        if (!(rawIds instanceof List<?> ids)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (Object rawId : ids) {
            String cardId = asString(rawId);
            if (!cardId.isBlank() && !normalized.contains(cardId)) {
                normalized.add(cardId);
            }
        }
        return normalized;
    }

    @Override
    public Map<String, Object> dispatchVisualize(Map<String, Object> request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String intentRaw = asString(request.get("intent"));
        VisualizeIntent intent = VisualizeIntent.fromKey(intentRaw)
                .orElseThrow(() -> new IllegalArgumentException("unsupported visualize intent: " + intentRaw));
        String prompt = asString(request.get("prompt"));
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        VisualizeRequest visualizeRequest = new VisualizeRequest(
                intent,
                prompt,
                castStringKeyMap(request.get("context_hints")),
                toLong(request.get("user_id")),
                toLong(request.get("problem_id")),
                asNullableString(request.get("session_id")),
                asNullableString(request.get("source_role"))
        );
        VisualizeResult result = visualizeCapabilityService.dispatch(visualizeRequest);
        Map<String, Object> cardPayload = result.toCardPayload();
        String cardId = "C-V-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("card_id", cardId);
        response.put("id", cardId);
        response.put("card_type", "visualize");
        response.put("card_payload", cardPayload);
        response.putAll(cardPayload);
        return response;
    }

    @Override
    public Map<String, Object> dispatchParsons(Map<String, Object> request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        ParsonsCapabilityService.DispatchRequest internalReq = new ParsonsCapabilityService.DispatchRequest(
                toLong(request.get("user_id")),
                toLong(request.get("problem_id")),
                asNullableString(request.get("session_id")),
                asNullableString(request.get("source_card_id")),
                asNullableString(request.get("previous_session_id")),
                asNullableString(request.get("fsrs_origin")),
                request.get("override_fading_level") instanceof Number n ? n.intValue() : null
        );
        ParsonsCapabilityService.DispatchResult result = parsonsCapabilityService.dispatch(internalReq);
        Map<String, Object> response = new LinkedHashMap<>();
        String cardId = "C-PSN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        response.put("card_id", cardId);
        response.put("id", cardId);
        response.put("card_type", "parsons_problem");
        response.put("card_payload", result.cardPayload());
        response.put("parsons_session_id", result.parsonsSessionId());
        response.putAll(result.cardPayload());
        return response;
    }

    @Override
    public Map<String, Object> gradeParsons(Map<String, Object> request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String sessionId = asString(request.get("parsons_session_id"));
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("parsons_session_id is required");
        }
        Object rawOrder = request.get("ordered_block_ids");
        List<String> ordered = rawOrder instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        ParsonsCapabilityService.GradeResult result = parsonsCapabilityService.grade(
                new ParsonsCapabilityService.GradeRequest(sessionId, ordered));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("passed", result.passed());
        body.put("judge_status", result.judgeStatus());
        body.put("attempts", result.attempts());
        body.put("walkthrough_required", result.walkthroughRequired());
        body.put("cascade_degrade", result.cascadeDegrade());
        body.put("cascade_failfast", result.cascadeFailfast());
        body.put("current_fading_level", result.currentFadingLevel());
        if (result.nextFadingLevel() != null) {
            body.put("next_fading_level", result.nextFadingLevel());
        }
        if (result.hint() != null) body.put("hint", result.hint());
        return body;
    }

    @Override
    public Map<String, Object> getLastCards(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("session_id is required");
        }
        return Map.of(
                "cards",
                conversationContextService.listLastCards(sessionId, limit).stream()
                        .map(CardSummary::toMap)
                        .toList()
        );
    }

    @Override
    public Map<String, Object> resolveReferences(String sessionId, List<String> references, String currentQuery) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("session_id is required");
        }
        List<String> safeRefs = references == null ? List.of() : references;
        // 现有 cards 解析维持不变（含 @card / @last_*）
        List<Map<String, Object>> cards = conversationContextService
                .resolveReferences(sessionId, safeRefs).stream()
                .map(CardSummary::toMap)
                .toList();

        // 新增：@courseware:<lpId> 解析。currentQuery 用作 RAG query；session 内反查 username 做鉴权。
        // currentQuery 缺失时不做 courseware 解析（保留 backwards compat：调用方不传 query 时只拿 cards）。
        List<Map<String, Object>> coursewares = List.of();
        if (currentQuery != null && !currentQuery.isBlank() && hasCoursewareToken(safeRefs)) {
            String username = lookupSessionUsername(sessionId);
            if (username != null) {
                coursewares = coursewareContextProvider.resolveCoursewareReferences(
                        username, safeRefs, currentQuery, null
                ).stream().map(CoursewareSummary::toMap).toList();
            }
        }

        String username = null;
        Long userId = null;
        if (hasPageToken(safeRefs) || hasKcToken(safeRefs) || hasNotebookToken(safeRefs)) {
            username = lookupSessionUsername(sessionId);
            userId = lookupSessionUserId(sessionId);
        }
        List<Map<String, Object>> pages = username == null ? List.of() : pageContextProvider
                .resolvePageReferences(username, null, safeRefs)
                .stream().map(PageSummary::toMap).toList();
        List<Map<String, Object>> kcs = userId == null ? List.of() : kcContextProvider
                .resolveKcReferences(userId, safeRefs)
                .stream().map(KcSummary::toMap).toList();
        List<Map<String, Object>> notebooks = userId == null ? List.of() : notebookContextProvider
                .resolveNotebookReferences(userId, safeRefs)
                .stream().map(NotebookSummary::toMap).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cards", cards);
        result.put("coursewares", coursewares);
        result.put("pages", pages);
        result.put("kcs", kcs);
        result.put("notebooks", notebooks);
        return result;
    }

    /** 任意一条 token 是 @courseware:<digits> → true，避免无意义跳到 username 查询。 */
    private static boolean hasCoursewareToken(List<String> references) {
        for (String raw : references) {
            if (ReferenceResolver.isCoursewareRef(raw)) return true;
        }
        return false;
    }

    private static boolean hasPageToken(List<String> references) {
        for (String raw : references) {
            if (ReferenceResolver.extractPageRef(raw) != null) return true;
        }
        return false;
    }

    private static boolean hasKcToken(List<String> references) {
        for (String raw : references) {
            if (ReferenceResolver.extractKcId(raw) != null) return true;
        }
        return false;
    }

    private static boolean hasNotebookToken(List<String> references) {
        for (String raw : references) {
            if (ReferenceResolver.extractNotebookEntryId(raw) != null) return true;
        }
        return false;
    }

    @Override
    public SessionUsage getSessionUsage(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("session_id is required");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tokens_used, tokens_limit, model_name, updated_at "
                        + "FROM ai_tutor_workflow_session WHERE session_id = :sid",
                new MapSqlParameterSource("sid", sessionId)
        );
        if (rows.isEmpty()) {
            throw new ProblemNotFoundException("Session not found: " + sessionId);
        }
        Map<String, Object> row = rows.get(0);
        Long used = toLong(row.get("tokens_used"));
        Long limit = toLong(row.get("tokens_limit"));
        String modelName = asString(row.get("model_name"));
        Instant updated = toInstant(row.get("updated_at"));
        return new SessionUsage(
                used == null ? 0L : used,
                limit == null ? 0L : limit,
                modelName == null ? "" : modelName,
                updated
        );
    }

    private static Instant toInstant(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Instant i) return i;
        if (raw instanceof Timestamp ts) return ts.toInstant();
        if (raw instanceof java.util.Date d) return d.toInstant();
        if (raw instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (raw instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
        }
        return null;
    }

    /**
     * 从 session_id 反查会话所属用户名。失败（session 不存在 / user 不存在）返回 null，
     * 调用方据此放弃 courseware 解析（不阻断主链路）。
     */
    private String lookupSessionUsername(String sessionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT u.username FROM ai_tutor_workflow_session s "
                            + "JOIN \"user\" u ON u.id = s.user_id "
                            + "WHERE s.session_id = :sid AND s.is_active = TRUE",
                    new MapSqlParameterSource("sid", sessionId),
                    String.class
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        } catch (Exception ex) {
            log.warn("lookupSessionUsername failed for session {}: {}", sessionId, ex.getMessage());
            return null;
        }
    }

    private Long lookupSessionUserId(String sessionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT user_id FROM ai_tutor_workflow_session "
                            + "WHERE session_id = :sid AND is_active = TRUE",
                    new MapSqlParameterSource("sid", sessionId),
                    Long.class
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        } catch (Exception ex) {
            log.warn("lookupSessionUserId failed for session {}: {}", sessionId, ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castStringKeyMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private Object parseJsonOrDefault(Object raw, Object defaultValue) {
        if (raw == null) return defaultValue;
        String text = raw.toString();
        if (text.isEmpty()) return defaultValue;
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON field, returning default: {}", e.getMessage());
            return defaultValue;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String asNullableString(Object value) {
        String text = asString(value);
        return text.isEmpty() ? null : text;
    }

    @Override
    @Transactional
    public Map<String, Object> forkSession(String sourceSessionId, Long fromMessageEventId) {
        if (sourceSessionId == null || sourceSessionId.isBlank()) {
            throw new IllegalArgumentException("source session_id is required");
        }

        List<Map<String, Object>> srcRows = jdbc.queryForList(
                "SELECT session_id, user_id, problem_id, thread_id, language, phase "
                        + "FROM ai_tutor_workflow_session WHERE session_id = :sid AND is_active = TRUE",
                new MapSqlParameterSource("sid", sourceSessionId)
        );
        if (srcRows.isEmpty()) {
            throw new ProblemNotFoundException("Source session not found: " + sourceSessionId);
        }
        Map<String, Object> src = srcRows.get(0);
        Long userId = toLong(src.get("user_id"));
        Long problemId = toLong(src.get("problem_id"));
        String language = asString(src.get("language"));
        String phase = asString(src.get("phase"));

        String newSessionId = "twf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String newThreadId = "thr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        jdbc.update(
                "INSERT INTO ai_tutor_workflow_session "
                        + "(session_id, user_id, problem_id, thread_id, language, phase, "
                        + " runtime_state, is_active, parent_session_id, fork_from_message_id, created_at, updated_at) "
                        + "VALUES (:sid, :uid, :pid, :tid, :lang, :pha, 'IDLE', TRUE, :psid, :fmid, now(), now())",
                new MapSqlParameterSource()
                        .addValue("sid", newSessionId)
                        .addValue("uid", userId)
                        .addValue("pid", problemId)
                        .addValue("tid", newThreadId)
                        .addValue("lang", language)
                        .addValue("pha", phase)
                        .addValue("psid", sourceSessionId)
                        .addValue("fmid", fromMessageEventId)
        );

        String eventCopyCondition = fromMessageEventId != null
                ? "AND e.id <= :fmid"
                : "";
        MapSqlParameterSource copyParams = new MapSqlParameterSource()
                .addValue("srcSid", sourceSessionId)
                .addValue("newSid", newSessionId);
        if (fromMessageEventId != null) {
            copyParams.addValue("fmid", fromMessageEventId);
        }

        jdbc.update(
                "INSERT INTO ai_tutor_workflow_event "
                        + "(session_id, run_id, thread_id, event_type, event_data, runtime_state, "
                        + " phase, node_outputs, behavior_metrics, available_actions, "
                        + " pending_human_action, created_at) "
                        + "SELECT :newSid, e.run_id, e.thread_id, e.event_type, e.event_data, e.runtime_state, "
                        + " e.phase, e.node_outputs, e.behavior_metrics, e.available_actions, "
                        + " e.pending_human_action, e.created_at "
                        + "FROM ai_tutor_workflow_event e "
                        + "WHERE e.session_id = :srcSid " + eventCopyCondition
                        + " ORDER BY e.id ASC",
                copyParams
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", newSessionId);
        result.put("thread_id", newThreadId);
        result.put("problem_id", problemId);
        result.put("language", language);
        result.put("phase", phase);
        result.put("forked_from", sourceSessionId);
        return result;
    }

    /**
     * Dedicated exception to map "resource not found" → HTTP 404 in controller.
     */
    public static class ProblemNotFoundException extends RuntimeException {
        public ProblemNotFoundException(String message) {
            super(message);
        }
    }
}
