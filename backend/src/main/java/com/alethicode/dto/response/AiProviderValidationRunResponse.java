package com.alethicode.dto.response;

import java.util.List;
import java.util.Map;

public record AiProviderValidationRunResponse(
        String runId,
        String profilePrefix,
        boolean passed,
        List<AiProviderValidationCaseResult> cases,
        Map<String, Object> summary
) {
}
