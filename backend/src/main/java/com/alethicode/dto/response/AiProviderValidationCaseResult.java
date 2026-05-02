package com.alethicode.dto.response;

import java.util.Map;

/**
 * Single validation case outcome. {@code shapeMatched} is the authoritative pass/fail
 * signal; {@code summary} is a redacted summary (never includes prompts, completions,
 * or API keys).
 */
public record AiProviderValidationCaseResult(
        String caseName,
        boolean passed,
        boolean shapeMatched,
        String failureMessage,
        Map<String, Object> summary
) {
}
