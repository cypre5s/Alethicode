package com.alethicode.service.rag.dto;

/**
 * 对齐 Python 服务的 {@code IndexAccepted} 响应。
 */
public record RagIndexAcceptedResponse(
        String indexingTaskId,
        String entityType,
        String entityId
) {
}
