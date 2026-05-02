package com.alethicode.service.aitutor.react;

import java.util.Set;

public record ToolContext(
        Long userId,
        String sessionId,
        Long problemId,
        Long languagePackId,
        String phase,
        String event,
        String locale,
        Set<String> permissions
) {

    public void requireUserId() {
        if (userId == null) {
            throw new IllegalStateException("ToolContext: userId is required");
        }
    }

    public void requireProblemId() {
        if (problemId == null) {
            throw new IllegalStateException("ToolContext: problemId is required");
        }
    }

    public void requireLanguagePackId() {
        if (languagePackId == null) {
            throw new IllegalStateException("ToolContext: languagePackId is required");
        }
    }

    public void requireSessionId() {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("ToolContext: sessionId is required");
        }
    }
}
