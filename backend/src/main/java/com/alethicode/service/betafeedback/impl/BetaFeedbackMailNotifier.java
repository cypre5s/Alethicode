package com.alethicode.service.betafeedback.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.BetaFeedbackCreateRequest;
import com.alethicode.service.system.SmtpMailService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 公测反馈邮件异步通知。独立成组件，避免 {@code @Async} 自调用走不到代理。
 *
 * <p>失败原则：异步邮件失败 fail-soft，把 mail_status='failed' / mail_error 写回库即可，
 * 绝不抛异常拖累主流程。
 */
@Component
public class BetaFeedbackMailNotifier {

    private static final Logger log = LoggerFactory.getLogger(BetaFeedbackMailNotifier.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final SmtpMailService smtpMailService;

    public BetaFeedbackMailNotifier(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AlethicodeProperties properties,
            SmtpMailService smtpMailService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.smtpMailService = smtpMailService;
    }

    @Async
    public void notifyAsync(
            long reportId,
            long userId,
            BetaFeedbackCreateRequest request,
            String browserMetaJson,
            String recentActionsJson,
            int attachmentCount,
            Map<String, Object> betaFeedbackConfig
    ) {
        String notifyEmail = trimOrEmpty(asString(betaFeedbackConfig.get("notify_email")));
        if (notifyEmail.isEmpty()) {
            updateMailStatus(reportId, "failed", "notify_email not configured");
            return;
        }
        Map<String, Object> smtpConfig = readSmtpConfig();
        if (smtpConfig == null) {
            updateMailStatus(reportId, "failed", "smtp_config missing");
            return;
        }
        String server = asString(smtpConfig.get("server")).trim();
        Integer port = asNullableInteger(smtpConfig.get("port"));
        String email = asString(smtpConfig.get("email")).trim();
        String password = asString(smtpConfig.get("password")).trim();
        boolean tls = asBoolean(smtpConfig.get("tls"), true);
        if (server.isEmpty() || port == null || email.isEmpty() || password.isEmpty()) {
            updateMailStatus(reportId, "failed", "smtp_config incomplete");
            return;
        }

        String username = lookupUsername(userId);
        String websiteName = properties.getWebsite().getNameShortcut();
        String baseUrl = properties.getWebsite().getBaseUrl();
        String adminLink = (baseUrl == null ? "" : baseUrl) + "/admin/beta-feedback?id=" + reportId;
        String subject = String.format(
                "[%s 公测] %s/%s #%d - %s",
                websiteName, request.type(), request.severity(), reportId, username
        );
        StringBuilder body = new StringBuilder();
        body.append("用户=").append(username).append('(').append(userId).append(')').append('\n');
        body.append("路径=").append(request.route() == null ? "" : request.route()).append('\n');
        body.append("题号=").append(request.problemId()).append('\n');
        body.append("提交号=").append(request.submissionId()).append('\n');
        body.append("会话=").append(request.workflowSessionId() == null ? "" : request.workflowSessionId()).append('\n');
        body.append("\n描述:\n").append(request.description() == null ? "" : request.description()).append('\n');
        body.append("\n浏览器: ").append(browserMetaJson).append('\n');
        body.append("最近操作: ").append(recentActionsJson).append('\n');
        body.append("附件数: ").append(attachmentCount).append('\n');
        body.append("管理员入口: ").append(adminLink).append('\n');

        try {
            smtpMailService.send(
                    server, port, email, password, tls,
                    websiteName, notifyEmail, "", subject, body.toString()
            );
            updateMailStatus(reportId, "sent", "");
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("Beta feedback mail notify failed reportId={}", reportId, ex);
            updateMailStatus(reportId, "failed", message.length() > 500 ? message.substring(0, 500) : message);
        }
    }

    private void updateMailStatus(long reportId, String status, String errorMessage) {
        try {
            jdbcTemplate.update(
                    "UPDATE beta_feedback_report SET mail_status = ?, mail_error = ?, updated_at = NOW() WHERE id = ?",
                    status,
                    errorMessage == null ? "" : errorMessage,
                    reportId
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to update mail status for report {}", reportId, ex);
        }
    }

    private String lookupUsername(long userId) {
        try {
            String username = jdbcTemplate.queryForObject(
                    "SELECT username FROM \"user\" WHERE id = ?",
                    String.class,
                    userId
            );
            return username == null ? String.valueOf(userId) : username;
        } catch (EmptyResultDataAccessException ex) {
            return String.valueOf(userId);
        } catch (RuntimeException ex) {
            return String.valueOf(userId);
        }
    }

    @Nullable
    private Map<String, Object> readSmtpConfig() {
        String rawJson;
        try {
            rawJson = jdbcTemplate.queryForObject(
                    "SELECT value::text FROM sys_options WHERE key = 'smtp_config'",
                    String.class
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
        if (rawJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @Nullable
    private static Integer asNullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
