package com.alethicode.dto.request;

public record AiProviderValidationRunRequest(
        String profilePrefix,
        boolean includeJson,
        boolean includeContent,
        boolean includeEmbedding,
        boolean includeToolLoop
) {
}
