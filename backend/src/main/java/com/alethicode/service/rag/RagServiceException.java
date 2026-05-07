package com.alethicode.service.rag;

/**
 * 表示 alethicode-rag 调用失败。
 *
 * 该异常是 fail fast 信号，调用方不得静默降级到旧 SQL 检索。
 */
public class RagServiceException extends RuntimeException {

    private final int statusCode;

    public RagServiceException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public RagServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public RagServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
