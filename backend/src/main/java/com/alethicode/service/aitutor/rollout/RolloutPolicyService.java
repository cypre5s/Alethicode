package com.alethicode.service.aitutor.rollout;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RolloutPolicyService {

    private final RolloutFlagService rolloutFlagService;
    private volatile boolean enabled = true;

    public RolloutPolicyService() {
        this(new NoopRolloutFlagService());
    }

    @Autowired
    public RolloutPolicyService(RolloutFlagService rolloutFlagService) {
        this.rolloutFlagService = rolloutFlagService;
    }

    public boolean isEnabled() {
        return enabled && rolloutFlagService.isEnabled("ai.rollout.enabled", true, "system", "global", Map.of());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RolloutDecision evaluate(String scopeType, String scopeKey, Map<String, Object> metrics) {
        String forcedMode = normalizeMode(rolloutFlagService.getVariant(
                "ai.rollout.force-mode", "", scopeType, scopeKey, metrics));
        if (!forcedMode.isBlank()) {
            return new RolloutDecision(forcedMode,
                    scopeType + ":" + scopeKey + " forced by rollout flag",
                    new LinkedHashMap<>(metrics));
        }
        double answerLeak = toDouble(metrics.get("answer_leak"));
        double schemaPass = toDouble(metrics.get("schema_pass"));
        double pedagogyPass = toDouble(metrics.get("pedagogy_pass"));
        double helpfulness = toDouble(metrics.get("helpfulness"));
        boolean banditEnabled = rolloutFlagService.isEnabled(
                "ai.tutor.bandit-enabled",
                toBoolean(metrics.get("bandit_enabled")),
                scopeType,
                scopeKey,
                metrics
        );
        boolean opeEligible = toBoolean(metrics.get("ope_eligible"));
        double opeScore = toDouble(metrics.get("ope_score"));
        int opeSampleSize = (int) Math.round(toDouble(metrics.get("ope_sample_size")));

        if (answerLeak > 0.1) {
            return new RolloutDecision("rollback", scopeType + ":" + scopeKey + " answer_leak threshold exceeded", new LinkedHashMap<>(metrics));
        }
        if (schemaPass > 0.0 && schemaPass < 0.95) {
            return new RolloutDecision("rollback", scopeType + ":" + scopeKey + " schema_pass threshold exceeded", new LinkedHashMap<>(metrics));
        }
        if (pedagogyPass > 0.0 && pedagogyPass < 0.95) {
            return new RolloutDecision("rollback", scopeType + ":" + scopeKey + " pedagogy_pass threshold exceeded", new LinkedHashMap<>(metrics));
        }
        if (helpfulness > 0.0 && helpfulness < 0.5) {
            return new RolloutDecision("dark_launch", scopeType + ":" + scopeKey + " helpfulness below threshold", new LinkedHashMap<>(metrics));
        }
        if (!banditEnabled) {
            return new RolloutDecision("baseline", scopeType + ":" + scopeKey + " bandit disabled", new LinkedHashMap<>(metrics));
        }
        if (!opeEligible || opeSampleSize < 5 || opeScore < 0.75) {
            return new RolloutDecision("dark_launch", scopeType + ":" + scopeKey + " ope not ready", new LinkedHashMap<>(metrics));
        }
        return new RolloutDecision("gray", scopeType + ":" + scopeKey + " ope cleared for gray rollout", new LinkedHashMap<>(metrics));
    }

    /**
     * A/B test allocation: assigns a user to treatment or control group.
     *
     * @param experimentId  unique experiment identifier
     * @param userId        user to assign
     * @param treatmentRate fraction of users in treatment group (0.0-1.0)
     * @return assignment decision with group label
     */
    public RolloutDecision evaluateHarnessGate(String scopeType, String scopeKey, Map<String, Object> harnessReport) {
        String forcedMode = normalizeMode(rolloutFlagService.getVariant(
                "ai.rollout.force-mode", "", scopeType, scopeKey, harnessReport));
        if (!forcedMode.isBlank()) {
            return new RolloutDecision(forcedMode,
                    scopeType + ":" + scopeKey + " forced by rollout flag",
                    new LinkedHashMap<>(harnessReport));
        }
        int sampleCount = (int) Math.round(toDouble(harnessReport.get("sample_count")));
        if (sampleCount < 5) {
            return new RolloutDecision("dark_launch", scopeType + ":" + scopeKey + " insufficient harness samples", new LinkedHashMap<>(harnessReport));
        }
        double groundingAccuracy = toDouble(harnessReport.get("grounding_accuracy"));
        double refusalAccuracy = toDouble(harnessReport.get("refusal_accuracy"));
        double avgOverallScore = toDouble(harnessReport.get("avg_overall_score"));
        if (groundingAccuracy > 0.0 && groundingAccuracy < 0.8) {
            return new RolloutDecision("rollback", scopeType + ":" + scopeKey + " grounding_accuracy below threshold", new LinkedHashMap<>(harnessReport));
        }
        if (refusalAccuracy > 0.0 && refusalAccuracy < 0.8) {
            return new RolloutDecision("rollback", scopeType + ":" + scopeKey + " refusal_accuracy below threshold", new LinkedHashMap<>(harnessReport));
        }
        if (avgOverallScore > 0.0 && avgOverallScore < 0.6) {
            return new RolloutDecision("dark_launch", scopeType + ":" + scopeKey + " avg_overall_score below threshold", new LinkedHashMap<>(harnessReport));
        }
        return new RolloutDecision("gray", scopeType + ":" + scopeKey + " harness gate passed", new LinkedHashMap<>(harnessReport));
    }

    public AbTestAssignment assignAbTest(String experimentId, Long userId, double treatmentRate) {
        double hash = stableHash(experimentId, userId);
        String group = hash < treatmentRate ? "treatment" : "control";
        return new AbTestAssignment(experimentId, userId, group, hash);
    }

    /**
     * Records a reward signal for the bandit from user actions (thumbs up/down, AC result).
     *
     * @param experimentId experiment this reward applies to
     * @param userId       user who triggered the reward
     * @param rewardType   type of reward signal
     * @param rewardValue  numeric reward (e.g. 1.0 for thumbs-up, 0.0 for thumbs-down)
     * @return collected reward entry
     */
    public BanditReward recordReward(String experimentId, Long userId,
                                     String rewardType, double rewardValue) {
        return new BanditReward(experimentId, userId, rewardType,
                Math.max(-1.0, Math.min(1.0, rewardValue)));
    }

    private double stableHash(String experimentId, Long userId) {
        long combined = (experimentId + ":" + userId).hashCode();
        return Math.abs(combined % 10000) / 10000.0;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizeMode(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.isBlank()) {
            return "";
        }
        return switch (normalized) {
            case "baseline", "dark_launch", "gray", "rollback" -> normalized;
            default -> "";
        };
    }

    public record AbTestAssignment(String experimentId, Long userId, String group, double hashValue) {}

    public record BanditReward(String experimentId, Long userId, String rewardType, double rewardValue) {}
}
