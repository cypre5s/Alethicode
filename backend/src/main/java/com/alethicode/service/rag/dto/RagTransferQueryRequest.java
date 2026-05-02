package com.alethicode.service.rag.dto;

import java.util.List;

public record RagTransferQueryRequest(
        Long currentProblemId,
        List<String> kcIds,
        String query,
        Integer topK
) {
    public RagTransferQueryRequest {
        if (currentProblemId == null) {
            throw new IllegalArgumentException("current_problem_id is required");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (kcIds == null) {
            kcIds = List.of();
        }
        if (topK == null || topK <= 0) {
            topK = 5;
        }
        if (topK > 50) {
            throw new IllegalArgumentException("top_k must be <= 50, got " + topK);
        }
    }
}
