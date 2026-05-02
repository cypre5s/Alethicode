package com.alethicode.dto.response;

public record AiProviderConfigResponse(
        String apiKeyMasked,
        boolean apiKeySet,
        String baseUrl,
        String model,
        String embeddingApiKeyMasked,
        boolean embeddingApiKeySet,
        String embeddingBaseUrl,
        String embeddingModel,
        int timeoutSeconds,
        int maxRetries,
        String source
) {
}
