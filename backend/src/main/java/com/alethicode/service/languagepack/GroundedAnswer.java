package com.alethicode.service.languagepack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GroundedAnswer(
        String answerMarkdown,
        List<Map<String, Object>> citations,
        boolean grounded,
        boolean insufficientEvidence,
        String refusalReason
) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answer_markdown", answerMarkdown);
        payload.put("citations", citations);
        payload.put("grounded", grounded);
        payload.put("insufficient_evidence", insufficientEvidence);
        if (refusalReason != null && !refusalReason.isBlank()) {
            payload.put("refusal_reason", refusalReason);
        }
        return payload;
    }
}
