package com.alethicode.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    ERROR("error", HttpStatus.BAD_REQUEST, "Request failed"),
    BAD_REQUEST("bad-request", HttpStatus.BAD_REQUEST, "Bad request"),
    UNAUTHORIZED("permission-denied", HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN("permission-denied", HttpStatus.FORBIDDEN, "Permission denied"),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    CONFLICT("conflict", HttpStatus.CONFLICT, "Conflict"),
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    SERVICE_UNAVAILABLE("service-unavailable", HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
