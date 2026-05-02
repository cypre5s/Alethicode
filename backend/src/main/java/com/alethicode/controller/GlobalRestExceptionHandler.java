package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cross-cutting REST exception mapping. Individual {@code @RestController}s can
 * still declare their own {@code @ExceptionHandler}s for business-specific
 * mapping; this advice handles framework-level validation / parsing / upload
 * errors uniformly.
 *
 * <p>Spring's default dispatcher already prefers a controller-local handler to a
 * {@code @ControllerAdvice}, so we do not need a custom order. Keep this advice
 * free of business-specific mappings to avoid accidentally overriding a
 * controller's handler.
 */
@ControllerAdvice
public class GlobalRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalRestExceptionHandler.class);

    /** {@code @Valid} on a {@code @RequestBody} DTO failed. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        List<Map<String, String>> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("field", fe.getField());
                    // defaultMessage is controlled by our validation annotations, not user input
                    m.put("message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
                    return m;
                })
                .collect(Collectors.toList());
        log.debug("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error("validation failed: " + fieldErrors));
    }

    /** {@code @Validated} on method params (query/path) failed. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException e) {
        log.debug("Constraint violation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error("validation failed: " + e.getMessage()));
    }

    /** Malformed / missing JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleBodyNotReadable(
            HttpMessageNotReadableException e) {
        log.debug("Body not readable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("request body is missing or malformed"));
    }

    /** Upload size cap. Keep the generic message so we don't leak container limits. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("upload exceeds configured limit"));
    }

    /**
     * Last-resort {@link RequestNotPermitted} handler so controllers that forgot
     * to define their own rate-limit exception handler still return a well-formed
     * 429 instead of a generic 500. Per-controller handlers still take precedence.
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimitExceeded(RequestNotPermitted e) {
        log.debug("Rate limit exceeded: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(ApiResponse.error("Rate limit exceeded — please slow down"));
    }
}
