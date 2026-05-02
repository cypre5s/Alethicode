package com.alethicode.service.betafeedback.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.BetaFeedbackCreateRequest;
import com.alethicode.dto.request.BetaTelemetryBatchRequest;
import com.alethicode.exception.BadRequestException;
import com.alethicode.service.betafeedback.BetaFeedbackService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 公测反馈与遥测服务实现。
 *
 * <p>设计点：
 * <ul>
 *   <li>截图存放在 {@code <uploadDir>/../beta-screenshots/yyyy-MM/<random10>.<ext>}，
 *       与 {@code /public/upload} 物理隔离，不暴露给学生端的静态资源映射。</li>
 *   <li>邮件发送委派给 {@link BetaFeedbackMailNotifier}，{@code @Async} 走 Spring 代理；
 *       SMTP 失败只把 mail_status 写成 failed，不阻塞学生提交（fail-soft）。</li>
 *   <li>所有事件类型 / 严重度 / 截图 MIME 都走显式白名单，未列入的直接 422。</li>
 * </ul>
 */
@Service
public class BetaFeedbackServiceImpl implements BetaFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(BetaFeedbackServiceImpl.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "cant_open", "button_dead", "page_confusing",
            "wrong_problem_or_answer", "ai_unclear", "submit_wrong", "other"
    );
    private static final Set<String> ALLOWED_SEVERITIES = Set.of(
            "blocker", "high", "medium", "low"
    );
    private static final Set<String> ALLOWED_SCREENSHOT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );
    private static final Set<String> ALLOWED_TELEMETRY_TYPES = Set.of(
            "page_view", "feature_click", "frontend_error",
            "api_error", "web_vital", "feedback_opened", "feedback_submitted"
    );
    private static final int MAX_SCREENSHOTS = 3;
    private static final int MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_RECENT_ACTIONS = 50;
    private static final int MAX_TELEMETRY_BATCH = 100;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] RANDOM_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final DateTimeFormatter MONTH_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final BetaFeedbackMailNotifier mailNotifier;
    private final String configuredScreenshotRoot;

    public BetaFeedbackServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AlethicodeProperties properties,
            BetaFeedbackMailNotifier mailNotifier,
            @Value("${alethicode.system.beta-screenshot-dir:}") String betaScreenshotDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.mailNotifier = mailNotifier;
        this.configuredScreenshotRoot = betaScreenshotDir == null ? "" : betaScreenshotDir.trim();
    }

    @Override
    public long createReport(BetaFeedbackCreateRequest request, MultipartFile[] screenshots, Long userId) {
        if (userId == null) {
            throw new BadRequestException("user not authenticated");
        }
        if (request == null) {
            throw new BadRequestException("missing request body");
        }

        String type = nullSafeTrim(request.type());
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BadRequestException("invalid feedback type");
        }
        String severity = nullSafeTrim(request.severity());
        if (!ALLOWED_SEVERITIES.contains(severity)) {
            throw new BadRequestException("invalid severity");
        }
        String description = request.description() == null ? "" : request.description();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException("description too long");
        }

        Map<String, Object> config = readBetaFeedbackConfig();
        validatePrivacyNoticeVersion(request.privacyNoticeVersion(), config);

        validateScreenshots(screenshots);

        String route = request.route() == null ? "" : request.route();
        String workflowSessionId = nullableTrim(request.workflowSessionId());
        String privacyNoticeVersion = nullSafeTrim(request.privacyNoticeVersion());

        String browserMetaJson = writeJsonOrEmpty(request.browserMeta(), "{}");
        String recentActionsJson = writeJsonOrEmpty(truncateActions(request.recentActions()), "[]");

        boolean enabled = asBoolean(config.get("enabled"), true);
        String initialMailStatus = enabled ? "pending" : "disabled";

        long reportId = insertReport(
                userId, type, severity, description, route,
                request.problemId(), request.submissionId(), workflowSessionId,
                Boolean.TRUE.equals(request.wjxFollowupOpened()),
                browserMetaJson, recentActionsJson,
                initialMailStatus, privacyNoticeVersion
        );

        int attachmentCount = persistScreenshots(reportId, screenshots);

        if (enabled) {
            mailNotifier.notifyAsync(reportId, userId, request, browserMetaJson, recentActionsJson, attachmentCount, config);
        }

        return reportId;
    }

    @Override
    public void recordTelemetryEvents(List<BetaTelemetryBatchRequest.TelemetryEvent> events, Long userId) {
        if (events == null || events.isEmpty()) {
            return;
        }
        if (events.size() > MAX_TELEMETRY_BATCH) {
            throw new BadRequestException("telemetry batch too large");
        }
        for (BetaTelemetryBatchRequest.TelemetryEvent event : events) {
            String eventType = nullSafeTrim(event.eventType());
            if (!ALLOWED_TELEMETRY_TYPES.contains(eventType)) {
                continue;
            }
            String route = event.route() == null ? "" : event.route();
            String payloadJson = writeJsonOrEmpty(event.payload(), "{}");
            jdbcTemplate.update(
                    "INSERT INTO beta_telemetry_event " +
                            "(user_id, event_type, route, problem_id, session_id, payload) " +
                            "VALUES (?, ?, ?, ?, ?, cast(? as jsonb))",
                    userId,
                    eventType,
                    route,
                    event.problemId(),
                    nullableTrim(event.sessionId()),
                    payloadJson
            );
        }
    }

    @Override
    public void recordWebVital(
            String metric,
            double value,
            String rating,
            String navigationType,
            String route,
            Long userId
    ) {
        if (metric == null || metric.isBlank()) {
            throw new BadRequestException("missing metric");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metric", metric);
        payload.put("value", value);
        payload.put("rating", rating);
        payload.put("navigation_type", navigationType);
        String payloadJson = writeJsonOrEmpty(payload, "{}");
        jdbcTemplate.update(
                "INSERT INTO beta_telemetry_event " +
                        "(user_id, event_type, route, payload) " +
                        "VALUES (?, ?, ?, cast(? as jsonb))",
                userId,
                "web_vital",
                route == null ? "" : route,
                payloadJson
        );
    }

    private long insertReport(
            long userId,
            String type,
            String severity,
            String description,
            String route,
            @Nullable Long problemId,
            @Nullable Long submissionId,
            @Nullable String workflowSessionId,
            boolean wjxFollowupOpened,
            String browserMetaJson,
            String recentActionsJson,
            String mailStatus,
            String privacyNoticeVersion
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "INSERT INTO beta_feedback_report " +
                "(reporter_user_id, type, severity, description, route, problem_id, submission_id, " +
                "workflow_session_id, wjx_followup_opened, browser_meta, recent_actions, mail_status, privacy_notice_version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setString(3, severity);
            ps.setString(4, description);
            ps.setString(5, route);
            if (problemId == null) {
                ps.setNull(6, java.sql.Types.BIGINT);
            } else {
                ps.setLong(6, problemId);
            }
            if (submissionId == null) {
                ps.setNull(7, java.sql.Types.BIGINT);
            } else {
                ps.setLong(7, submissionId);
            }
            if (workflowSessionId == null) {
                ps.setNull(8, java.sql.Types.VARCHAR);
            } else {
                ps.setString(8, workflowSessionId);
            }
            ps.setBoolean(9, wjxFollowupOpened);
            ps.setString(10, browserMetaJson);
            ps.setString(11, recentActionsJson);
            ps.setString(12, mailStatus);
            ps.setString(13, privacyNoticeVersion);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null && keys.get("id") instanceof Number n) {
                return n.longValue();
            }
            throw new IllegalStateException("failed to retrieve generated id");
        }
        return key.longValue();
    }

    private int persistScreenshots(long reportId, MultipartFile[] screenshots) {
        if (screenshots == null || screenshots.length == 0) {
            return 0;
        }
        Path screenshotRoot = resolveScreenshotRoot();
        String monthDir = LocalDate.now().format(MONTH_DIR_FORMAT);
        Path monthRoot = screenshotRoot.resolve(monthDir);
        try {
            Files.createDirectories(monthRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create screenshot dir", ex);
        }

        int saved = 0;
        for (MultipartFile file : screenshots) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = file.getContentType();
            String suffix = inferSuffix(contentType);
            String filename = randomString(10) + suffix;
            Path target = monthRoot.resolve(filename).normalize();
            if (!target.startsWith(screenshotRoot)) {
                throw new IllegalStateException("screenshot path escapes root");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to write screenshot", ex);
            }
            String storagePath = target.toAbsolutePath().toString();
            String originalName = file.getOriginalFilename() == null ? filename : file.getOriginalFilename();
            jdbcTemplate.update(
                    "INSERT INTO beta_feedback_attachment " +
                            "(report_id, file_name, content_type, size_bytes, storage_path) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    reportId,
                    originalName.length() > 256 ? originalName.substring(0, 256) : originalName,
                    contentType,
                    (int) file.getSize(),
                    storagePath
            );
            saved++;
        }
        return saved;
    }

    private void validateScreenshots(MultipartFile[] screenshots) {
        if (screenshots == null) {
            return;
        }
        if (screenshots.length > MAX_SCREENSHOTS) {
            throw new BadRequestException("too many screenshots");
        }
        for (MultipartFile file : screenshots) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_SCREENSHOT_BYTES) {
                throw new BadRequestException("screenshot too large");
            }
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_SCREENSHOT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("screenshot type not allowed");
            }
        }
    }

    private void validatePrivacyNoticeVersion(String submitted, Map<String, Object> config) {
        String required = nullSafeTrim(asString(config.get("privacy_notice_version")));
        if (required.isEmpty()) {
            return;
        }
        String provided = nullSafeTrim(submitted);
        if (provided.isEmpty()) {
            log.warn("Privacy notice version not provided by client; required={}. Allowing submission.", required);
            return;
        }
        if (!required.equals(provided)) {
            throw new BadRequestException("privacy notice version mismatch");
        }
    }

    private Path resolveScreenshotRoot() {
        Path root;
        if (!configuredScreenshotRoot.isEmpty()) {
            root = Path.of(configuredScreenshotRoot).toAbsolutePath().normalize();
        } else {
            String uploadDir = properties.getSystem().getUploadDir();
            if (uploadDir == null || uploadDir.isBlank()) {
                throw new IllegalStateException("alethicode.system.upload-dir not configured");
            }
            Path parent = Path.of(uploadDir).toAbsolutePath().normalize().getParent();
            if (parent == null) {
                throw new IllegalStateException("upload-dir has no parent for beta-screenshots");
            }
            root = parent.resolve("beta-screenshots");
        }
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create beta-screenshots root: " + root, ex);
        }
        return root;
    }

    private String inferSuffix(String contentType) {
        if (contentType == null) {
            return ".bin";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }

    private List<Map<String, Object>> truncateActions(List<Map<String, Object>> actions) {
        if (actions == null) {
            return List.of();
        }
        if (actions.size() <= MAX_RECENT_ACTIONS) {
            return actions;
        }
        return actions.subList(actions.size() - MAX_RECENT_ACTIONS, actions.size());
    }

    private String writeJsonOrEmpty(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private Map<String, Object> readBetaFeedbackConfig() {
        Map<String, Object> stored = readMapOption("beta_feedback_config");
        return stored == null ? Map.of() : stored;
    }

    private Map<String, Object> readMapOption(String key) {
        String rawJson;
        try {
            rawJson = jdbcTemplate.queryForObject(
                    "SELECT value::text FROM sys_options WHERE key = ?",
                    String.class,
                    key
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
            log.warn("Failed to parse sys option {} as map", key, ex);
            return null;
        }
    }

    private static String nullSafeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private static String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private static String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM_CHARS[RANDOM.nextInt(RANDOM_CHARS.length)]);
        }
        return builder.toString();
    }
}
