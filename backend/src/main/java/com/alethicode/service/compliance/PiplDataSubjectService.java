package com.alethicode.service.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the data-subject rights mandated by 《个人信息保护法》(PIPL) articles 44-47:
 *
 * <ul>
 *   <li>知情权 / 查阅权 — {@link #exportPersonalData}: return every data category
 *       we hold on the data subject in a structured payload.</li>
 *   <li>更正权 — covered by existing admin/user profile APIs; not duplicated here.</li>
 *   <li>删除权 — {@link #requestDeletion}: create a pending request; admin workflow
 *       actually executes and records the result.</li>
 *   <li>可携权 — the export payload is plain JSON suitable for reuse.</li>
 * </ul>
 *
 * <p>Every access, export, or deletion is recorded to {@code pii_access_log} for
 * the 5-year auditability window required by PIPL article 55 and DSL article 27.
 */
@Service
public class PiplDataSubjectService {

    private static final Logger log = LoggerFactory.getLogger(PiplDataSubjectService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final Counter auditWriteFailures;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PiplDataSubjectService(NamedParameterJdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        // Metric name aligned with the Prometheus alert `PiiAccessLogWriteFailing`.
        this.auditWriteFailures = Counter.builder("pii_access_log_write_failed_total")
                .description("PII access-log inserts that failed (PIPL audit trail at risk)")
                .register(meterRegistry);
    }

    /**
     * Aggregate a copy of the data subject's personal information across all
     * domains (profile, submissions, AI tutor sessions, learner notebook, etc).
     * Designed to be served as JSON; callers are responsible for auth / rate
     * limiting the HTTP entry point.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportPersonalData(long subjectId, Long accessorId,
                                                   String accessorRole, String clientIp,
                                                   String userAgent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject_id", subjectId);
        payload.put("generated_at", java.time.Instant.now().toString());
        payload.put("profile", loadProfile(subjectId));
        payload.put("submissions", loadSubmissionSummaries(subjectId));
        payload.put("tutor_sessions", loadTutorSessionSummaries(subjectId));
        payload.put("learner_notebook", loadNotebookSummaries(subjectId));
        recordAccess(subjectId, accessorId, accessorRole, "export",
                Map.of("categories", payload.keySet()), clientIp, userAgent);
        return payload;
    }

    /**
     * Register a deletion request. The actual purge happens asynchronously via
     * admin review so accidental or coerced requests can still be audited and
     * rolled back inside the regulatory response window (15 business days).
     */
    @Transactional
    public long requestDeletion(long subjectId, Long requestedById, String reason,
                                 String clientIp, String userAgent) {
        Number id = jdbc.getJdbcTemplate().queryForObject(
                "INSERT INTO pii_deletion_request (data_subject_id, requested_by_id, reason) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class,
                subjectId, requestedById, reason == null ? "" : reason
        );
        long requestId = id == null ? -1 : id.longValue();
        recordAccess(subjectId, requestedById,
                requestedById != null && requestedById == subjectId ? "self" : "admin",
                "delete",
                Map.of("deletion_request_id", requestId, "reason", reason == null ? "" : reason),
                clientIp, userAgent);
        return requestId;
    }

    /**
     * Append-only audit record. Use this everywhere PII is accessed / mutated so
     * we can produce a full access timeline during a regulator inquiry.
     */
    public void recordAccess(long subjectId, Long accessorId, String accessorRole,
                              String action, Map<String, Object> summary,
                              String clientIp, String userAgent) {
        try {
            jdbc.update(
                    "INSERT INTO pii_access_log " +
                            "(data_subject_id, accessor_id, accessor_role, action, " +
                            " payload_summary, client_ip, user_agent) " +
                            "VALUES (:sid, :aid, :role, :act, :pl::jsonb, :ip, :ua)",
                    new MapSqlParameterSource()
                            .addValue("sid", subjectId)
                            .addValue("aid", accessorId)
                            .addValue("role", accessorRole == null ? "system" : accessorRole)
                            .addValue("act", action)
                            .addValue("pl", objectMapper.writeValueAsString(summary == null ? Map.of() : summary))
                            .addValue("ip", clientIp)
                            .addValue("ua", userAgent));
        } catch (Exception e) {
            auditWriteFailures.increment();
            // Never block the business path on audit failure, but make the miss very loud.
            log.error("pii_access_log_write_failed subject={} action={} err={}",
                    subjectId, action, e.getMessage());
        }
    }

    private Map<String, Object> loadProfile(long subjectId) {
        // Only select columns that are guaranteed to exist on the `user` table across
        // migrations (V2 / V50). Additional PII columns (phone / real_name / school)
        // should be added here as they are introduced to the schema. Use try/catch
        // so a missing column downgrades to "partial export" instead of 500.
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, username, email " +
                            "FROM \"user\" WHERE id = :id",
                    new MapSqlParameterSource("id", subjectId));
            return rows.isEmpty() ? Map.of() : rows.get(0);
        } catch (Exception e) {
            log.warn("loadProfile failed for subject {}: {}", subjectId, e.getMessage());
            return Map.of("id", subjectId, "export_error", "profile_unavailable");
        }
    }

    private List<Map<String, Object>> loadSubmissionSummaries(long subjectId) {
        return safeQueryForList(
                "SELECT id, problem_id, language, result, create_time " +
                        "FROM submission WHERE user_id = :uid ORDER BY create_time DESC LIMIT 1000",
                new MapSqlParameterSource("uid", subjectId),
                "submissions");
    }

    private List<Map<String, Object>> loadTutorSessionSummaries(long subjectId) {
        return safeQueryForList(
                "SELECT session_id, problem_id, phase, language, created_at " +
                        "FROM ai_tutor_workflow_session WHERE user_id = :uid " +
                        "ORDER BY created_at DESC LIMIT 500",
                new MapSqlParameterSource("uid", subjectId),
                "tutor_sessions");
    }

    private List<Map<String, Object>> loadNotebookSummaries(long subjectId) {
        // Learner notebook holds student reflections & diagnoses; we export
        // structured fields and omit free-form embeddings.
        return safeQueryForList(
                "SELECT id, problem_id, language, error_taxonomy, root_cause, fix_outcome, update_time " +
                        "FROM ai_learner_notebook WHERE user_id = :uid AND is_deleted = false " +
                        "ORDER BY update_time DESC LIMIT 1000",
                new MapSqlParameterSource("uid", subjectId),
                "learner_notebook");
    }

    /**
     * Schema-tolerant query helper: any SQL-level failure (missing column, missing
     * table in an older deployment) is logged and downgraded to an empty list so
     * the PIPL export endpoint returns a "partial but honest" payload instead of a
     * hard 500. The caller is responsible for surfacing the partial status to the
     * user when relevant.
     */
    private List<Map<String, Object>> safeQueryForList(String sql, MapSqlParameterSource params,
                                                       String category) {
        try {
            return jdbc.queryForList(sql, params);
        } catch (Exception e) {
            log.warn("PIPL export partial: {} query failed — {}", category, e.getMessage());
            return List.of();
        }
    }
}
