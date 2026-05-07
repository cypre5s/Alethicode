package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.compliance.PiplDataSubjectService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人信息保护自助接口。
 *
 * <p>导出和删除请求都使用最严格的 {@code adminWrite} 限流桶，公开入口必须优先防滥用。</p>
 */
@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {

    private static final Logger log = LoggerFactory.getLogger(PrivacyController.class);

    private final PiplDataSubjectService piplService;

    public PrivacyController(PiplDataSubjectService piplService) {
        this.piplService = piplService;
    }

    /**
     * 导出当前用户关联的个人数据快照。
     */
    @PostMapping("/data-exports")
    @RateLimiter(name = "adminWrite")
    public ResponseEntity<ApiResponse<Object>> exportPersonalData(
            HttpServletRequest servletRequest,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        Map<String, Object> payload = piplService.exportPersonalData(
                userId, userId, "self",
                servletRequest == null ? null : servletRequest.getRemoteAddr(),
                servletRequest == null ? null : servletRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    /**
     * 登记个人数据删除工单。
     *
     * 实际清理由管理员复核，避免胁迫或钓鱼场景下立即执行不可逆删除。
     */
    @DeleteMapping("/personal-data")
    @RateLimiter(name = "adminWrite")
    public ResponseEntity<ApiResponse<Object>> requestDeletion(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest servletRequest,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        long requestId = piplService.requestDeletion(
                userId, userId, reason,
                servletRequest == null ? null : servletRequest.getRemoteAddr(),
                servletRequest == null ? null : servletRequest.getHeader("User-Agent"));
        Map<String, Object> result = Map.of(
                "deletion_request_id", requestId,
                "status", "PENDING",
                "resolution_window_days", 15
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurity(SecurityException e) {
        log.warn("privacy request unauthenticated: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(io.github.resilience4j.ratelimiter.RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimit(
            io.github.resilience4j.ratelimiter.RequestNotPermitted e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(ApiResponse.error("Rate limit exceeded — please slow down"));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Not authenticated");
        }
        Object details = authentication.getDetails();
        if (details instanceof Number n) return n.longValue();
        Object principal = authentication.getPrincipal();
        if (principal instanceof Map<?, ?> p) {
            Object id = p.get("id");
            if (id instanceof Number n) return n.longValue();
            if (id != null) {
                try { return Long.parseLong(String.valueOf(id)); } catch (NumberFormatException ignored) { }
            }
        }
        throw new SecurityException("User id missing from authentication");
    }
}
