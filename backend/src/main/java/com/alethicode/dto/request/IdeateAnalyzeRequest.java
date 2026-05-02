package com.alethicode.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IdeateAnalyzeRequest(
        @NotNull Long problemId,
        String sessionId,
        @NotBlank String thoughtText
) {
}
