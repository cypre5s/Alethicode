package com.alethicode.service.aitutor.rollout;

import java.util.LinkedHashMap;
import java.util.Map;

public record RolloutDecision(String rolloutMode, String reason, Map<String, Object> metrics) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rollout_mode", rolloutMode);
        payload.put("reason", reason);
        payload.put("metrics", metrics);
        return payload;
    }
}
