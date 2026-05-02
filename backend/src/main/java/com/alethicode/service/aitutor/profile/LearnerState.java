package com.alethicode.service.aitutor.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LearnerState(
        boolean calibrated,
        Map<String, Double> masteryByKc,
        List<String> weakKcs,
        Map<String, Double> misconceptionDistribution,
        Map<String, Object> recentBehavior,
        String frustrationLevel,
        String confidenceProxy,
        Map<String, Object> recommendedActionBias,
        List<Map<String, Object>> memoryRefs,
        String narrativeSummary,
        boolean personalizationEnabled
) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("calibrated", calibrated);
        payload.put("mastery_by_kc", masteryByKc);
        payload.put("weak_kcs", weakKcs);
        payload.put("misconception_distribution", misconceptionDistribution);
        payload.put("recent_behavior", recentBehavior);
        payload.put("frustration_level", frustrationLevel);
        payload.put("confidence_proxy", confidenceProxy);
        payload.put("recommended_action_bias", recommendedActionBias);
        payload.put("memory_refs", memoryRefs);
        payload.put("narrative_summary", narrativeSummary == null ? "" : narrativeSummary);
        payload.put("personalization_enabled", personalizationEnabled);
        return payload;
    }
}
