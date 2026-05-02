package com.alethicode.controller;

import com.alethicode.dto.request.BetaFeedbackCreateRequest;
import com.alethicode.dto.request.BetaTelemetryBatchRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.betafeedback.BetaFeedbackService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 公测反馈与遥测的学生侧入口。
 *
 * <p>路径鉴权策略（与 SecurityConfig 协作）：
 * <ul>
 *   <li>{@code POST /api/beta/feedback-reports} ：必须登录（authenticated）</li>
 *   <li>{@code POST /api/beta/telemetry/events} ：必须登录</li>
 *   <li>{@code POST /api/beta/telemetry/web-vitals} ：必须登录</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/beta")
public class BetaFeedbackController {

    private static final Logger log = LoggerFactory.getLogger(BetaFeedbackController.class);

    private final BetaFeedbackService service;
    private final ObjectMapper objectMapper;

    public BetaFeedbackController(BetaFeedbackService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            value = {"/feedback-reports", "/feedback-reports/"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<Map<String, Object>> createReport(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "screenshots", required = false) MultipartFile[] screenshots,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        BetaFeedbackCreateRequest request = parseRequest(dataJson);
        long id = service.createReport(request, screenshots, userId);
        return ApiResponse.success(Map.of("id", id));
    }

    @PostMapping({"/telemetry/events", "/telemetry/events/"})
    public ApiResponse<Map<String, Object>> telemetryBatch(
            @RequestBody BetaTelemetryBatchRequest body,
            Authentication authentication
    ) {
        Long userId = extractUserIdNullable(authentication);
        List<BetaTelemetryBatchRequest.TelemetryEvent> events = body == null ? List.of() : body.events();
        if (events == null) {
            events = List.of();
        }
        service.recordTelemetryEvents(events, userId);
        return ApiResponse.success(Map.of("created", events.size()));
    }

    @PostMapping({"/telemetry/web-vitals", "/telemetry/web-vitals/"})
    public ApiResponse<Void> webVitals(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        Long userId = extractUserIdNullable(authentication);
        if (body == null) {
            body = Map.of();
        }
        Object valueObj = body.get("value");
        double value = valueObj instanceof Number n ? n.doubleValue() : 0d;
        service.recordWebVital(
                asString(body.get("metric")),
                value,
                asString(body.get("rating")),
                asString(body.get("navigationType")),
                asString(body.get("route")),
                userId
        );
        return ApiResponse.success(null);
    }

    private BetaFeedbackCreateRequest parseRequest(String dataJson) {
        if (dataJson == null || dataJson.isBlank()) {
            throw new IllegalArgumentException("missing data part");
        }
        try {
            return objectMapper.readValue(dataJson, BetaFeedbackCreateRequest.class);
        } catch (JsonProcessingException ex) {
            log.debug("Failed to parse beta feedback data part", ex);
            throw new IllegalArgumentException("malformed data part");
        }
    }

    private Long extractUserId(Authentication authentication) {
        Long id = extractUserIdNullable(authentication);
        if (id == null) {
            throw new SecurityException("Not authenticated");
        }
        return id;
    }

    private Long extractUserIdNullable(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof Number n) {
            return n.longValue();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Map<?, ?> p) {
            Object id = p.get("id");
            if (id instanceof Number n) {
                return n.longValue();
            }
            if (id != null) {
                try {
                    return Long.parseLong(String.valueOf(id));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 公测反馈的业务校验失败（截图过大/类型不允许/隐私版本不匹配等）映射为 422 Unprocessable Entity，
     * 与全局 {@link com.alethicode.exception.BadRequestException} 的默认 400 不同——这里语义更精确：
     * 请求格式合法但语义违规。
     */
    @ExceptionHandler(com.alethicode.exception.BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(com.alethicode.exception.BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
