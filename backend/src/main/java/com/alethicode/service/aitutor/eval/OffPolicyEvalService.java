package com.alethicode.service.aitutor.eval;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OffPolicyEvalService {

    private static final int MIN_SAMPLE_SIZE = 5;
    private static final double MIN_EFFECTIVE_SAMPLE_SIZE = 3.0;

    public OffPolicyEvalResult evaluate(String phase, String candidateAction, List<Map<String, Object>> loggedSamples) {
        String normalizedPhase = phase == null ? "" : phase.toUpperCase(Locale.ROOT);
        String normalizedAction = candidateAction == null ? "" : candidateAction;
        double weightedReward = 0.0;
        double totalWeight = 0.0;
        double squaredWeight = 0.0;
        int matchedSamples = 0;

        for (Map<String, Object> loggedSample : loggedSamples) {
            String samplePhase = String.valueOf(loggedSample.getOrDefault("phase", "")).toUpperCase(Locale.ROOT);
            String loggedAction = String.valueOf(loggedSample.getOrDefault("logged_action", ""));
            if (!normalizedPhase.equals(samplePhase) || !normalizedAction.equals(loggedAction)) {
                continue;
            }
            double propensity = clamp(parseDouble(loggedSample.get("propensity")), 0.05, 1.0);
            double reward = clamp(parseDouble(loggedSample.get("reward")), 0.0, 1.0);
            double weight = 1.0 / propensity;
            weightedReward += weight * reward;
            totalWeight += weight;
            squaredWeight += weight * weight;
            matchedSamples++;
        }

        if (matchedSamples < MIN_SAMPLE_SIZE || totalWeight <= 0.0) {
            return new OffPolicyEvalResult(false, matchedSamples, 0.0, 0.0, "insufficient samples for ope");
        }

        double estimatedReward = weightedReward / totalWeight;
        double effectiveSampleSize = totalWeight * totalWeight / squaredWeight;
        boolean eligible = effectiveSampleSize >= MIN_EFFECTIVE_SAMPLE_SIZE;
        String reason = eligible ? "ope eligible" : "insufficient effective sample size";
        return new OffPolicyEvalResult(eligible, matchedSamples, effectiveSampleSize, estimatedReward, reason);
    }

    private double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
