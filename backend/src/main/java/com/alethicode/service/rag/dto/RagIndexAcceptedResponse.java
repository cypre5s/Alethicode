package com.alethicode.service.rag.dto;

/**
 * Mirrors {@code IndexAccepted} from the Python service. Returned with HTTP
 * 202 to acknowledge the indexing task was queued.
 */
public record RagIndexAcceptedResponse(
        String indexingTaskId,
        String entityType,
        String entityId
) {
}
