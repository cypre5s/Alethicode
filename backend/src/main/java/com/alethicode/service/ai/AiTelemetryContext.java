package com.alethicode.service.ai;

public record AiTelemetryContext(
        String scene,
        Long userId,
        String sessionId,
        Long problemId,
        String promptVersion
) {
}
