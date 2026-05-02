package com.alethicode.dto.request;

public record DebugSubmissionRequest(
        Long problemId,
        String language,
        String code,
        String input
) {
}
