package com.alethicode.service.rag.dto;

import java.util.List;

public record RagMemoryQueryRequest(
        Long userId,
        List<String> currentKcs,
        String errorContext,
        String query,
        Integer topK
) {
    public RagMemoryQueryRequest {
        if (userId == null) {
            throw new IllegalArgumentException("user_id is required");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (currentKcs == null) {
            currentKcs = List.of();
        }
        if (topK == null || topK <= 0) {
            topK = 5;
        }
        if (topK > 50) {
            throw new IllegalArgumentException("top_k must be <= 50, got " + topK);
        }
    }
}
