package com.alethicode.dto.request;

public record AiProviderConfigRequest(
        String apiKey,
        String baseUrl,
        String model,
        String embeddingApiKey,
        String embeddingBaseUrl,
        String embeddingModel,
        Integer timeoutSeconds,
        Integer maxRetries
) {
}
