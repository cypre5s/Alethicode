package com.alethicode.dto.request;

import java.util.List;
import java.util.Map;

public record PreflightCheckRequest(
        Long problemId,
        String detectorName,
        Integer lineNumber,
        String codeSnippet,
        String studentCode,
        List<Map<String, Object>> otherHits
) {
}
