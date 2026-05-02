package com.alethicode.service.ai;

/**
 * Chat-model provider profile. Phase 3 切流后 Java 端不再持有 embedding 凭据，
 * 嵌入与向量检索由 alethicode-rag 微服务托管（services/alethicode-rag）。
 */
public record AiModelProfile(
        String profilePrefix,
        String apiKey,
        String baseUrl,
        String chatModel,
        int timeoutSeconds,
        int maxRetries
) {
}
