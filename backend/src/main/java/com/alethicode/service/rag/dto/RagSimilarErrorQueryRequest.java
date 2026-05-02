package com.alethicode.service.rag.dto;

public record RagSimilarErrorQueryRequest(
        Long userId,
        Long currentProblemId,
        String errorTaxonomy,
        String query,
        Integer topK
) {
    public RagSimilarErrorQueryRequest {
        if (userId == null) {
            throw new IllegalArgumentException("user_id is required");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK == null || topK <= 0) {
            topK = 5;
        }
        if (topK > 50) {
            throw new IllegalArgumentException("top_k must be <= 50, got " + topK);
        }
    }
}
