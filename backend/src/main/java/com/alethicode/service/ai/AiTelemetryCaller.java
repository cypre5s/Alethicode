package com.alethicode.service.ai;

public record AiTelemetryCaller(
        String service,
        String scene,
        String callerClass,
        String callerMethod,
        String domain
) {

    public static AiTelemetryCaller unknown(String operation) {
        String normalizedOperation = operation == null || operation.isBlank() ? "ai.call" : operation.strip();
        return new AiTelemetryCaller(
                "java-ai",
                normalizedOperation,
                "unknown",
                "unknown",
                "java-ai"
        );
    }

    public boolean known() {
        return !"unknown".equals(callerClass);
    }
}
