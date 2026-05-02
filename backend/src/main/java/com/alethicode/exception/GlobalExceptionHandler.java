package com.alethicode.exception;

import com.alethicode.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LegacyBusinessException.class)
    public ResponseEntity<ApiResponse<String>> handleLegacyBusinessException(LegacyBusinessException exception) {
        return ResponseEntity.ok(ApiResponse.error(exception.legacyCode(), exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<String>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        if (errorCode == ErrorCode.INTERNAL_ERROR) {
            log.error("BusinessException [INTERNAL_ERROR]: {}", exception.getMessage(), exception);
            return build(errorCode, errorCode.defaultMessage());
        }
        log.warn("BusinessException [{}]: {}", errorCode, exception.getMessage());
        return build(errorCode, sanitizeMessage(exception.getMessage()));
    }

    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<String>> handleBadRequest(Exception exception) {
        return build(ErrorCode.BAD_REQUEST, resolveBadRequestMessage(exception));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<String>> handleDataAccessException(DataAccessException exception) {
        log.error("Database access failed", exception);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<String>> handleAuthenticationException(AuthenticationException exception) {
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<String>> handleAccessDeniedException(AccessDeniedException exception) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNoResourceFoundException(NoResourceFoundException exception) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleUnhandledException(Exception exception) {
        log.error("Unhandled exception", exception);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<ApiResponse<String>> build(ErrorCode errorCode, String message) {
        String responseMessage = message == null || message.isBlank() ? errorCode.defaultMessage() : message;
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.error(errorCode.code(), responseMessage));
    }

    /**
     * 业务异常消息清洗：仅当消息明确包含可能泄露内部基础设施信息的关键字（路径/SQL/堆栈等）时才屏蔽，
     * 其他情况原样返回，避免把"第 3 题不存在"这种正常错误中的数字也一并吞掉。
     */
    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return ErrorCode.NOT_FOUND.defaultMessage();
        }
        String lower = message.toLowerCase();
        boolean sensitive = lower.contains("exception:")
                || lower.contains("caused by")
                || lower.contains("stacktrace")
                || lower.contains("at ")
                || lower.contains("org.springframework")
                || lower.contains("java.")
                || lower.contains(".jar")
                || lower.contains(".class")
                || lower.contains("postgres")
                || lower.contains("redis")
                || lower.contains("sql");
        if (!sensitive) {
            return message.trim();
        }
        return ErrorCode.INTERNAL_ERROR.defaultMessage();
    }

    private String resolveBadRequestMessage(Exception exception) {
        // MethodArgumentNotValidException extends BindException，所以必须先判子类。
        // 合并到统一的 BindException 分支后语义等价，但避免"第二个 if 永远触发不到"的死代码。
        if (exception instanceof BindException bindException) {
            FieldError fieldError = bindException.getBindingResult().getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
                return fieldError.getDefaultMessage();
            }
            return ErrorCode.BAD_REQUEST.defaultMessage();
        }
        if (exception instanceof MissingServletRequestParameterException missingServletRequestParameterException) {
            return missingServletRequestParameterException.getParameterName() + " is required";
        }
        if (exception instanceof MethodArgumentTypeMismatchException methodArgumentTypeMismatchException) {
            return methodArgumentTypeMismatchException.getName() + " has invalid value";
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? ErrorCode.BAD_REQUEST.defaultMessage() : message;
    }
}
