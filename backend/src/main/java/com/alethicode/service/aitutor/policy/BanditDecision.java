package com.alethicode.service.aitutor.policy;

import java.util.LinkedHashMap;
import java.util.Map;

public record BanditDecision(
        String chosenAction,
        double chosenPropensity,
        Map<String, Double> probabilities,
        String reason
) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chosen_action", chosenAction);
        payload.put("chosen_propensity", chosenPropensity);
        payload.put("probabilities", probabilities);
        payload.put("reason", reason);
        return payload;
    }
}
