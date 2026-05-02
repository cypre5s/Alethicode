package com.alethicode.dto.request;

import jakarta.validation.constraints.NotNull;

public record IdeateInsertedRequest(
        @NotNull Long problemId,
        String sessionId
) {
}
