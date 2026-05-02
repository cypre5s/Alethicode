package com.alethicode.service.aitutor.eval;

import java.util.LinkedHashMap;
import java.util.Map;

public record OffPolicyEvalResult(
        boolean eligible,
        int sampleSize,
        double effectiveSampleSize,
        double estimatedReward,
        String reason
) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eligible", eligible);
        payload.put("sample_size", sampleSize);
        payload.put("effective_sample_size", effectiveSampleSize);
        payload.put("estimated_reward", estimatedReward);
        payload.put("reason", reason);
        return payload;
    }
}
