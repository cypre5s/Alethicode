package com.alethicode.service.aitutor.rollout;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RolloutPolicyServiceTest {

    private final RolloutPolicyService service = new RolloutPolicyService();

    @Test
    void shouldTriggerRollbackWhenSafetyMetricsCrossThreshold() {
        RolloutDecision decision = service.evaluate(
                "workflow",
                "session-1:READING",
                Map.of(
                        "answer_leak", 0.25,
                        "schema_pass", 0.70,
                        "helpfulness", 0.40
                )
        );

        assertThat(decision.rolloutMode()).isEqualTo("rollback");
        assertThat(decision.reason()).contains("answer_leak");
    }

    @Test
    void shouldStayDarkLaunchUntilOpeThresholdPasses() {
        RolloutDecision decision = service.evaluate(
                "workflow",
                "session-2:READING",
                Map.of(
                        "answer_leak", 0.0,
                        "schema_pass", 1.0,
                        "pedagogy_pass", 1.0,
                        "helpfulness", 0.82,
                        "bandit_enabled", true,
                        "ope_eligible", false,
                        "ope_score", 0.0,
                        "ope_sample_size", 2
                )
        );

        assertThat(decision.rolloutMode()).isEqualTo("dark_launch");
        assertThat(decision.reason()).contains("ope");
    }

    @Test
    void shouldEnterGrayWhenBanditOpeClearsThreshold() {
        RolloutDecision decision = service.evaluate(
                "workflow",
                "session-3:READING",
                Map.of(
                        "answer_leak", 0.0,
                        "schema_pass", 1.0,
                        "pedagogy_pass", 1.0,
                        "helpfulness", 0.91,
                        "bandit_enabled", true,
                        "ope_eligible", true,
                        "ope_score", 0.86,
                        "ope_sample_size", 8
                )
        );

        assertThat(decision.rolloutMode()).isEqualTo("gray");
        assertThat(decision.reason()).contains("ope");
    }

    @Test
    void shouldRespectForcedModeFromFlagProvider() {
        RolloutPolicyService flaggedService = new RolloutPolicyService(new RolloutFlagService() {
            @Override
            public boolean isEnabled(String flagName, boolean defaultValue, String scopeType, String scopeKey, Map<String, Object> context) {
                return defaultValue;
            }

            @Override
            public String getVariant(String flagName, String defaultValue, String scopeType, String scopeKey, Map<String, Object> context) {
                if ("ai.rollout.force-mode".equals(flagName)) {
                    return "baseline";
                }
                return defaultValue;
            }
        });

        RolloutDecision decision = flaggedService.evaluate(
                "workflow",
                "session-4:READING",
                Map.of(
                        "answer_leak", 0.0,
                        "schema_pass", 1.0,
                        "pedagogy_pass", 1.0,
                        "helpfulness", 0.91,
                        "bandit_enabled", true,
                        "ope_eligible", true,
                        "ope_score", 0.86,
                        "ope_sample_size", 8
                )
        );

        assertThat(decision.rolloutMode()).isEqualTo("baseline");
        assertThat(decision.reason()).contains("forced");
    }
}
