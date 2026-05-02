package com.alethicode.service.aitutor.visualize;

import java.util.Map;

public record VisualizeRequest(
        VisualizeIntent intent,
        String prompt,
        Map<String, Object> contextHints,
        Long userId,
        Long problemId,
        String sessionId,
        String sourceRole
) {
}
