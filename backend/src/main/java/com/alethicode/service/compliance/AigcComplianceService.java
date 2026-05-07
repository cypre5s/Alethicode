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
 * 满足《生成式人工智能服务管理暂行办法》要求的 AIGC 合规服务。
 *
 * <ul>
 *   <li>面向学生的 AI 生成内容必须显式标识。</li>
 *   <li>每次生成都必须记录输入输出审计，保留期由 {@code retention_expires_at} 约束。</li>
 *   <li>敏感内容扫描应接入企业级内容安全服务，默认实现仅保留插拔点。</li>
 * </ul>
 */
@Service
public class AigcComplianceService {

    private static final Logger log = LoggerFactory.getLogger(AigcComplianceService.class);

    /** 中文优先的 AI 生成内容标识，保持短文本以免破坏卡片布局。 */
    private static final String AI_GENERATED_TAG = "（以下内容由 AI 生成，仅供参考）";

    private final NamedParameterJdbcTemplate jdbc;
    private final Counter auditWriteFailures;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AigcComplianceService(NamedParameterJdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        // 指标名必须与 Prometheus 告警 AigcAuditWriteFailing 保持一致。
        this.auditWriteFailures = Counter.builder("aigc_audit_write_failed_total")
                .description("AIGC audit-log inserts that failed (regulatory retention at risk)")
                .register(meterRegistry);
    }

    /**
     * 为尚未标识的内容添加 AI 生成声明，重复调用保持幂等。
     */
    public String labelAiGeneratedContent(String content) {
        if (content == null || content.isEmpty()) return content;
        if (content.startsWith(AI_GENERATED_TAG)) return content;
        return AI_GENERATED_TAG + "\n" + content;
    }

    /**
     * 在 AI 响应到达最终用户前扫描敏感内容类别。
     *
     * @return 命中的类别标签列表，空列表表示未命中
     */
    public List<String> scanForSensitiveContent(String content) {
        // TODO(compliance): 接入内容安全 API；该方法保持为唯一供应商插拔点。
        if (content == null || content.isEmpty()) return List.of();
        return List.of();
    }

    /**
     * 持久化单次生成的监管审计记录。
     *
     * 审计写入失败只记录日志和指标，不阻断学生端响应。
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
     * 清理超过保留窗口的审计记录。
     *
     * @return 已清理行数
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
