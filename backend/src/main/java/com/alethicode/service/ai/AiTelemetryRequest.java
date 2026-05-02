package com.alethicode.service.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public record AiTelemetryRequest(Map<String, Object> attributes) {

    public AiTelemetryRequest {
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    public static AiTelemetryRequest generation(
            String operation,
            String scene,
            String profilePrefix,
            String modelName,
            String promptHash,
            int promptLength,
            String genAiOperation,
            AiTelemetryContext context
    ) {
        AiTelemetryCaller caller = AiTelemetrySupport.inferCaller(operation);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("langfuse.trace.name", "ai." + operation);
        attributes.put("langfuse.observation.type", "generation");
        attributes.put("langfuse.observation.model.name", normalize(modelName, "unknown"));
        attributes.put("langfuse.observation.metadata.service", caller.service());
        attributes.put("langfuse.observation.metadata.scene", caller.known() ? caller.scene() : normalize(scene, operation));
        attributes.put("langfuse.observation.metadata.domain", caller.domain());
        attributes.put("langfuse.observation.metadata.caller_class", caller.callerClass());
        attributes.put("langfuse.observation.metadata.caller_method", caller.callerMethod());
        attributes.put("langfuse.trace.metadata.service", caller.service());
        attributes.put("langfuse.trace.metadata.domain", caller.domain());
        attributes.put("langfuse.observation.metadata.profile_prefix", normalize(profilePrefix, "default"));
        attributes.put("langfuse.observation.metadata.prompt_hash", promptHash);
        attributes.put("langfuse.observation.metadata.prompt_length", Math.max(promptLength, 0));
        attributes.put("gen_ai.system", "spring_ai");
        attributes.put("gen_ai.operation.name", normalize(genAiOperation, "chat"));
        applyContext(attributes, context);
        return new AiTelemetryRequest(attributes);
    }

    public static AiTelemetryRequest cacheObservation(
            String operation,
            String profilePrefix,
            String promptHash,
            int promptLength,
            String cacheKeyHash,
            boolean cacheHit,
            AiTelemetryContext context
    ) {
        AiTelemetryCaller caller = AiTelemetrySupport.inferCaller(operation);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("langfuse.trace.name", "ai.cache." + operation);
        attributes.put("langfuse.observation.type", "event");
        attributes.put("langfuse.observation.model.name", "cache");
        attributes.put("langfuse.observation.metadata.service", caller.service());
        attributes.put("langfuse.observation.metadata.scene", caller.known() ? caller.scene() : operation);
        attributes.put("langfuse.observation.metadata.domain", caller.domain());
        attributes.put("langfuse.observation.metadata.caller_class", caller.callerClass());
        attributes.put("langfuse.observation.metadata.caller_method", caller.callerMethod());
        attributes.put("langfuse.trace.metadata.service", caller.service());
        attributes.put("langfuse.trace.metadata.domain", caller.domain());
        attributes.put("langfuse.observation.metadata.profile_prefix", normalize(profilePrefix, "default"));
        attributes.put("langfuse.observation.metadata.prompt_hash", promptHash);
        attributes.put("langfuse.observation.metadata.prompt_length", Math.max(promptLength, 0));
        attributes.put("langfuse.observation.metadata.cache_key_hash", cacheKeyHash);
        attributes.put("langfuse.observation.metadata.cache_hit", cacheHit);
        attributes.put("gen_ai.system", "spring_ai");
        attributes.put("gen_ai.operation.name", "cache");
        applyContext(attributes, context);
        return new AiTelemetryRequest(attributes);
    }

    private static void applyContext(Map<String, Object> attributes, AiTelemetryContext context) {
        if (context == null) {
            return;
        }
        if (context.scene() != null && !context.scene().isBlank()) {
            attributes.put("langfuse.observation.metadata.scene", context.scene().strip());
        }
        if (context.userId() != null) {
            attributes.put("langfuse.user.id", String.valueOf(context.userId()));
        }
        if (context.sessionId() != null && !context.sessionId().isBlank()) {
            attributes.put("langfuse.session.id", context.sessionId().strip());
        }
        if (context.problemId() != null) {
            attributes.put("langfuse.observation.metadata.problem_id", context.problemId());
            attributes.put("langfuse.trace.metadata.problem_id", context.problemId());
        }
        if (context.promptVersion() != null && !context.promptVersion().isBlank()) {
            attributes.put("langfuse.version", context.promptVersion().strip());
            attributes.put("langfuse.observation.metadata.prompt_version", context.promptVersion().strip());
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
