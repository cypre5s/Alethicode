package com.alethicode.service.aitutor.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TutorWorkflowProjectionService {

    private static final Logger log = LoggerFactory.getLogger(TutorWorkflowProjectionService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TutorWorkflowProjectionService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public Map<String, Object> createSessionWithId(String sessionId, long userId, long problemId,
                                                    String threadId, String language) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language is required to create a tutor workflow session");
        }
        jdbc.update(
                "INSERT INTO ai_tutor_workflow_session " +
                        "(session_id, thread_id, user_id, problem_id, language, phase, runtime_state, " +
                        " node_outputs, available_actions, plan, recommendation_reason) " +
                        "VALUES (:sid, :tid, :uid, :pid, :lang, 'READING', 'COMPLETED', '{}'::jsonb, '[]'::jsonb, '{}'::jsonb, '')",
                new MapSqlParameterSource()
                        .addValue("sid", sessionId)
                        .addValue("tid", threadId)
                        .addValue("uid", userId)
                        .addValue("pid", problemId)
                        .addValue("lang", language)
        );

        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("session_id", sessionId);
        snapshot.put("thread_id", threadId);
        snapshot.put("problem_id", problemId);
        snapshot.put("language", language);
        snapshot.put("phase", "READING");
        snapshot.put("runtime_state", "COMPLETED");
        snapshot.put("node_outputs", Map.of());
        snapshot.put("available_actions", List.of());
        snapshot.put("plan", Map.of());
        snapshot.put("recommendation_reason", "");
        snapshot.put("created", true);
        return snapshot;
    }

    public Optional<Map<String, Object>> getSession(String sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT session_id, thread_id, user_id, problem_id, language, phase, runtime_state, " +
                        "pending_human_action, node_outputs::text, behavior_metrics::text, " +
                        "available_actions::text, plan::text, recommendation_reason, failure_bucket, last_error, last_checkpoint_id, last_run_id, is_active, " +
                        "created_at, updated_at " +
                        "FROM ai_tutor_workflow_session WHERE session_id = :sid AND is_active = TRUE",
                new MapSqlParameterSource("sid", sessionId)
        );
        if (rows.isEmpty()) return Optional.empty();

        Map<String, Object> row = new java.util.HashMap<>(rows.get(0));
        row.put("node_outputs", parseJsonField(row.get("node_outputs")));
        row.put("behavior_metrics", parseJsonField(row.get("behavior_metrics")));
        row.put("available_actions", parseJsonField(row.get("available_actions")));
        row.put("plan", parseJsonField(row.get("plan")));
        return Optional.of(row);
    }

    public Optional<String> getSessionLanguage(String sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT language FROM ai_tutor_workflow_session " +
                        "WHERE session_id = :sid AND is_active = TRUE",
                new MapSqlParameterSource("sid", sessionId)
        );
        if (rows.isEmpty()) return Optional.empty();
        Object lang = rows.get(0).get("language");
        if (lang == null) return Optional.empty();
        String text = lang.toString();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    /**
     * Parse a projection JSON column. Returns {@link Map#of()} on any failure so the
     * caller (controller) never receives a raw String under a key that downstream
     * code treats as {@code Map<String, Object>} — that would surface as an
     * unrelated {@link ClassCastException} on {@code GET /api/ai/tutor-workflow-sessions/{id}}.
     */
    private Object parseJsonField(Object value) {
        if (value == null) return Map.of();
        String text = value.toString();
        if (text.isEmpty()) return Map.of();
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("Corrupt JSON projection field (len={}) — returning empty map: {}",
                    text.length(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * 默认"活跃" = is_active=TRUE 且 updated_at 在 1 小时内。超过窗口则视为
     * 隐式过期；新一次 createSession 会复用而不是新建。这条与 CRIT-3 的 active
     * session 配额配合：学生做完一道题切去做别的，旧 session 1h 后自然不再
     * 占用配额，无需后台 cron。
     */
    private static final String ACTIVE_TTL_HOURS = "1";

    public Optional<Map<String, Object>> findActiveSession(long userId, long problemId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ai_tutor_workflow_session " +
                        "WHERE user_id = :uid AND problem_id = :pid AND is_active = TRUE " +
                        "  AND updated_at > NOW() - (:ttl || ' hours')::interval " +
                        "ORDER BY updated_at DESC LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("uid", userId)
                        .addValue("pid", problemId)
                        .addValue("ttl", ACTIVE_TTL_HOURS)
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Transactional
    public void deactivateSession(String sessionId) {
        jdbc.update(
                "UPDATE ai_tutor_workflow_session SET is_active = FALSE, updated_at = NOW() WHERE session_id = :sid",
                new MapSqlParameterSource("sid", sessionId)
        );
    }

    public List<Map<String, Object>> getEvents(String sessionId) {
        return jdbc.queryForList(
                "SELECT * FROM ai_tutor_workflow_event WHERE session_id = :sid ORDER BY created_at DESC LIMIT 50",
                new MapSqlParameterSource("sid", sessionId)
        );
    }

    public boolean isSessionOwnedByUser(String sessionId, long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_tutor_workflow_session WHERE session_id = :sid AND user_id = :uid AND is_active = TRUE",
                new MapSqlParameterSource().addValue("sid", sessionId).addValue("uid", userId),
                Long.class
        );
        return count != null && count > 0;
    }

    /**
     * Count currently active tutor workflow sessions owned by a specific user,
     * limited to those updated within {@link #ACTIVE_TTL_HOURS}. Sessions that
     * are still {@code is_active=TRUE} in DB but stale beyond the window are
     * treated as implicitly expired and don't count toward the quota.
     *
     * <p>Used by {@link com.alethicode.service.aitutor.quota.AiTutorQuotaService}
     * to enforce the per-user concurrent-session cap (CRIT-3, 2026-05-02 渗透报告)
     * without punishing students who forget to close old conversations.
     */
    public long countActiveSessionsForUser(long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_tutor_workflow_session " +
                        "WHERE user_id = :uid AND is_active = TRUE " +
                        "  AND updated_at > NOW() - (:ttl || ' hours')::interval",
                new MapSqlParameterSource()
                        .addValue("uid", userId)
                        .addValue("ttl", ACTIVE_TTL_HOURS),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Transactional
    public void markRunQueued(String sessionId, String runId) {
        jdbc.update(
                "UPDATE ai_tutor_workflow_session SET runtime_state = 'QUEUED', failure_bucket = NULL, last_error = '', last_run_id = :rid, updated_at = NOW() " +
                        "WHERE session_id = :sid AND is_active = TRUE",
                new MapSqlParameterSource()
                        .addValue("sid", sessionId)
                        .addValue("rid", runId)
        );
    }
}
