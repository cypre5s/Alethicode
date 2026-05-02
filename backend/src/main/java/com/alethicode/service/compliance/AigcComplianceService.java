package com.alethicode.service.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * AIGC compliance service that satisfies the "generative AI provider" obligations
 * in 《生成式人工智能服务管理暂行办法》:
 *
 * <ul>
 *   <li>Article 12: AI-generated content must be explicitly labelled to end users.
 *       Call {@link #labelAiGeneratedContent(String)} before returning any LLM
 *       output from a student-facing surface.</li>
 *   <li>Article 10/11: provider logs inputs and outputs for at least 6 months.
 *       Call {@link #auditGeneration(AuditEntry)} for every generation event;
 *       retention is DB-enforced via {@code retention_expires_at}.</li>
 *   <li>Sensitive content scan: providers must have a pre-release scan pipeline.
 *       The default implementation is a pluggable fail-open no-op that logs the
 *       decision; swap for an enterprise provider (阿里云内容安全 / 腾讯云 T-Sec)
 *       in production.</li>
 * </ul>
 */
@Service
public class AigcComplianceService {

    private static final Logger log = LoggerFactory.getLogger(AigcComplianceService.class);

    /** Chinese-first disclaimer; keep short so it never breaks card layouts. */
    private static final String AI_GENERATED_TAG = "（以下内容由 AI 生成，仅供参考）";

    private final NamedParameterJdbcTemplate jdbc;
    private final Counter auditWriteFailures;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AigcComplianceService(NamedParameterJdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        // Name matches the Prometheus alert rule `AigcAuditWriteFailing` in
        // deploy/observability/prometheus/alerts.yml. Changing the name here
        // silently breaks the alert.
        this.auditWriteFailures = Counter.builder("aigc_audit_write_failed_total")
                .description("AIGC audit-log inserts that failed (regulatory retention at risk)")
                .register(meterRegistry);
    }

    /**
     * Prepend the AI-generated disclaimer if the content doesn't already carry one.
     * Re-tagging the same content is safe and idempotent.
     */
    public String labelAiGeneratedContent(String content) {
        if (content == null || content.isEmpty()) return content;
        if (content.startsWith(AI_GENERATED_TAG)) return content;
        return AI_GENERATED_TAG + "\n" + content;
    }

    /**
     * Scan an outbound AI response for sensitive categories before it reaches an
     * end user. The reference implementation is permissive by design — production
     * deployments must wire this to 阿里云内容安全 / 网易易盾 / 腾讯云天御.
     *
     * @return list of triggered category labels (empty = safe). The caller decides
     *         whether to redact, refuse, or continue based on business policy.
     */
    public List<String> scanForSensitiveContent(String content) {
        // TODO(compliance): integrate 阿里云内容安全 API. Keep this method a single
        // plug-in point so the rest of the codebase needn't change when swapping
        // vendors.
        if (content == null || content.isEmpty()) return List.of();
        return List.of();
    }

    /**
     * Persist the regulatory audit record for a single generation turn.
     * Failures are logged but do not propagate so a transient DB outage never
     * blocks the student-facing response; operators must alert on
     * {@code aigc_audit_write_failed} counters.
     */
    public void auditGeneration(AuditEntry entry) {
        try {
            String sensitiveJson = objectMapper.writeValueAsString(entry.sensitiveFlags());
            jdbc.update(
                    "INSERT INTO aigc_audit_log " +
                            "(user_id, session_id, run_id, surface, model_family, " +
                            " input_hash, output_hash, input_preview, output_preview, " +
                            " content_tagged, sensitive_flags) " +
                            "VALUES (:uid, :sid, :rid, :sfc, :mdl, " +
                            "        :ih, :oh, :ip, :op, :ct, :sf::jsonb)",
                    new MapSqlParameterSource()
                            .addValue("uid", entry.userId())
                            .addValue("sid", entry.sessionId())
                            .addValue("rid", entry.runId())
                            .addValue("sfc", entry.surface())
                            .addValue("mdl", entry.modelFamily())
                            .addValue("ih", sha256(entry.rawInput()))
                            .addValue("oh", sha256(entry.rawOutput()))
                            .addValue("ip", abbreviate(entry.rawInput(), 500))
                            .addValue("op", abbreviate(entry.rawOutput(), 500))
                            .addValue("ct", entry.contentTagged())
                            .addValue("sf", sensitiveJson));
        } catch (Exception e) {
            auditWriteFailures.increment();
            log.warn("aigc_audit_write_failed: {}", e.getMessage());
        }
    }

    /**
     * Retention sweep: delete rows whose retention window has passed. Intended to
     * run as a scheduled job (Spring {@code @Scheduled} outside this class so the
     * frequency is configurable).
     *
     * @return number of rows purged
     */
    public int purgeExpiredAuditLogs() {
        return jdbc.update(
                "DELETE FROM aigc_audit_log WHERE retention_expires_at < NOW()",
                new MapSqlParameterSource());
    }

    private static String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String abbreviate(String input, int maxLen) {
        if (input == null) return "";
        return input.length() <= maxLen ? input : input.substring(0, maxLen);
    }

    public record AuditEntry(
            Long userId,
            String sessionId,
            String runId,
            String surface,
            String modelFamily,
            String rawInput,
            String rawOutput,
            boolean contentTagged,
            List<String> sensitiveFlags
    ) {}
}
