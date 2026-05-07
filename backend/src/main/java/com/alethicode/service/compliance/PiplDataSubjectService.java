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
 * 实现《个人信息保护法》中数据主体权利相关能力。
 *
 * <ul>
 *   <li>知情权 / 查阅权：{@link #exportPersonalData} 返回当前主体的结构化数据副本。</li>
 *   <li>更正权：由已有管理端和用户画像接口覆盖，此处不重复实现。</li>
 *   <li>删除权：{@link #requestDeletion} 创建待处理请求，由管理员流程执行并记录结果。</li>
 *   <li>可携权：导出载荷使用可复用的普通 JSON。</li>
 * </ul>
 *
 * <p>所有访问、导出和删除动作都会写入 {@code pii_access_log}，保留五年审计窗口。</p>
 */
@Service
public class PiplDataSubjectService {

    private static final Logger log = LoggerFactory.getLogger(PiplDataSubjectService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final Counter auditWriteFailures;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PiplDataSubjectService(NamedParameterJdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        // 指标名必须与 Prometheus 告警 PiiAccessLogWriteFailing 保持一致。
        this.auditWriteFailures = Counter.builder("pii_access_log_write_failed_total")
                .description("PII access-log inserts that failed (PIPL audit trail at risk)")
                .register(meterRegistry);
    }

    /**
     * 聚合数据主体在各业务域中的个人信息副本。
     *
     * 调用方负责 HTTP 入口鉴权与限流，本方法只组装 JSON 载荷。
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
     * 登记删除请求，实际清理由管理员复核流程异步完成。
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
     * 写入追加式 PII 审计记录。
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
            // 审计失败不阻断业务路径，但必须显式告警。
            log.error("pii_access_log_write_failed subject={} action={} err={}",
                    subjectId, action, e.getMessage());
        }
    }

    private Map<String, Object> loadProfile(long subjectId) {
        // 只读取跨迁移稳定存在的 user 列，避免旧部署因缺列导致导出失败。
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
        // 学习笔记只导出结构化诊断字段，不导出自由文本 embedding。
        return safeQueryForList(
                "SELECT id, problem_id, language, error_taxonomy, root_cause, fix_outcome, update_time " +
                        "FROM ai_learner_notebook WHERE user_id = :uid AND is_deleted = false " +
                        "ORDER BY update_time DESC LIMIT 1000",
                new MapSqlParameterSource("uid", subjectId),
                "learner_notebook");
    }

    /**
     * 容忍旧 schema 的查询辅助方法，SQL 失败时降级为空列表。
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
