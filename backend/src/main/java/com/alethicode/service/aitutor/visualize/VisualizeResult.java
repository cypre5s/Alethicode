package com.alethicode.service.aitutor.visualize;

import java.util.LinkedHashMap;
import java.util.Map;

public record VisualizeResult(
        VisualizeIntent intent,
        String format,
        String payload,
        String altText,
        String sourceRole,
        Map<String, Object> debug
) {
    public Map<String, Object> toCardPayload() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("intent", intent.key());
        map.put("format", format);
        map.put("payload", payload);
        map.put("alt_text", altText == null ? "" : altText);
        map.put("source_role", sourceRole == null ? "AI" : sourceRole);
        return map;
    }
}
