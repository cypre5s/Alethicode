package com.alethicode.service.aitutor.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TutorActionDecision(
        String recommendedAction,
        String confidence,
        String reason,
        List<Map<String, Object>> availableActions
) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recommended_action", recommendedAction);
        payload.put("confidence", confidence);
        payload.put("reason", reason);
        payload.put("available_actions", availableActions);
        return payload;
    }
}
