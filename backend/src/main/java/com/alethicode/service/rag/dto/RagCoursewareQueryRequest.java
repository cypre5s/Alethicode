package com.alethicode.service.rag.dto;

import java.util.List;

public record RagCoursewareQueryRequest(
        Long languagePackId,
        String query,
        List<String> kcIds,
        Integer topK
) {
    public RagCoursewareQueryRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (kcIds == null) {
            kcIds = List.of();
        }
        if (topK == null || topK <= 0) {
            topK = 8;
        }
        if (topK > 50) {
            throw new IllegalArgumentException("top_k must be <= 50, got " + topK);
        }
    }
}
