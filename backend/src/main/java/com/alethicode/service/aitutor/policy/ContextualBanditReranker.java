package com.alethicode.service.aitutor.policy;

import com.alethicode.service.aitutor.profile.LearnerState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ContextualBanditReranker {

    public BanditDecision rerank(String phase, TutorActionDecision ruleDecision, LearnerState learnerState) {
        List<Map<String, Object>> actions = ruleDecision == null || ruleDecision.availableActions() == null
                ? List.of()
                : ruleDecision.availableActions();
        if (actions.isEmpty()) {
            return new BanditDecision("", 0.0, Map.of(), "no_actions");
        }

        Map<String, Double> rawScores = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        String normalizedPhase = phase == null ? "" : phase;
        boolean lowConfidence = learnerState != null && "low".equals(learnerState.confidenceProxy());
        boolean strongReadingPreference = hasMemoryKey(learnerState, "reading_pref");
        boolean highFrustration = learnerState != null
                && ("high".equals(learnerState.frustrationLevel()) || "severe".equals(learnerState.frustrationLevel()));
        boolean hasWeakKcs = learnerState != null && !learnerState.weakKcs().isEmpty();
        boolean hasCrossCourseWeakKcs = learnerState != null
                && learnerState.recommendedActionBias().get("cross_course_weak_kcs") instanceof List<?> list
                && !list.isEmpty();

        for (int index = 0; index < actions.size(); index++) {
            Map<String, Object> action = actions.get(index);
            String key = String.valueOf(action.getOrDefault("key", ""));
            double score = 1.0 - (index * 0.12);
            if (key.equals(ruleDecision.recommendedAction())) {
                score += 0.20;
            }
            if ("READING".equals(normalizedPhase) && "problem_guide".equals(key) && lowConfidence) {
                score += 0.35;
                reasons.add("low_confidence");
            }
            if ("READING".equals(normalizedPhase) && "problem_guide".equals(key) && strongReadingPreference) {
                score += 0.55;
                reasons.add("reading_pref");
            }
            if ("ERROR_FEEDBACK".equals(normalizedPhase) && "re_ideate".equals(key) && highFrustration) {
                score += 0.65;
                reasons.add("frustration");
            }
            if ("AC_REVIEW".equals(normalizedPhase) && "ac_review".equals(key) && (hasWeakKcs || hasCrossCourseWeakKcs)) {
                score += 0.45;
                reasons.add("weak_kcs");
            }
            if ("AC_REVIEW".equals(normalizedPhase) && "metacognitive".equals(key) && (hasWeakKcs || hasCrossCourseWeakKcs)) {
                score += 0.40;
                reasons.add("metacognitive_weak_kcs");
            }
            if ("AC_REVIEW".equals(normalizedPhase) && "transfer".equals(key) && !hasWeakKcs && !hasCrossCourseWeakKcs) {
                score += 0.30;
                reasons.add("ready_transfer");
            }
            rawScores.put(key, Math.max(score, 0.05));
        }

        Map<String, Double> probabilities = softmax(rawScores);
        String chosenAction = probabilities.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(String.valueOf(actions.getFirst().getOrDefault("key", "")));
        return new BanditDecision(
                chosenAction,
                probabilities.getOrDefault(chosenAction, 0.0),
                probabilities,
                reasons.isEmpty() ? "rule_prior" : String.join("+", deduplicate(reasons))
        );
    }

    private boolean hasMemoryKey(LearnerState learnerState, String needle) {
        if (learnerState == null || learnerState.memoryRefs() == null) {
            return false;
        }
        String expected = needle.toLowerCase(Locale.ROOT);
        for (Map<String, Object> memoryRef : learnerState.memoryRefs()) {
            String memoryKey = String.valueOf(memoryRef.getOrDefault("memory_key", "")).toLowerCase(Locale.ROOT);
            if (memoryKey.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Double> softmax(Map<String, Double> rawScores) {
        double max = rawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double sum = 0.0;
        Map<String, Double> expScores = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : rawScores.entrySet()) {
            double exp = Math.exp(entry.getValue() - max);
            expScores.put(entry.getKey(), exp);
            sum += exp;
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        if (sum <= 0.0) {
            double uniform = rawScores.isEmpty() ? 0.0 : 1.0 / rawScores.size();
            rawScores.keySet().forEach(key -> probabilities.put(key, uniform));
            return probabilities;
        }
        for (Map.Entry<String, Double> entry : expScores.entrySet()) {
            probabilities.put(entry.getKey(), entry.getValue() / sum);
        }
        return probabilities;
    }

    private List<String> deduplicate(List<String> reasons) {
        List<String> result = new ArrayList<>();
        for (String reason : reasons) {
            if (!result.contains(reason)) {
                result.add(reason);
            }
        }
        return result;
    }
}
