package com.alethicode.service.rag;

/**
 * Thrown when a call to alethicode-rag fails (transport error, 4xx/5xx
 * response). Application code that talks to the RAG layer should treat
 * this as a fail-fast signal — per the calling contract there is no
 * silent degradation to old SQL retrieval.
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
