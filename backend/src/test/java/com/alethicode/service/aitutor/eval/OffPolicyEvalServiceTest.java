package com.alethicode.service.aitutor.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OffPolicyEvalServiceTest {

    private final OffPolicyEvalService service = new OffPolicyEvalService();

    @Test
    void shouldStayIneligibleWhenSamplesAreInsufficient() {
        OffPolicyEvalResult result = service.evaluate(
                "READING",
                "problem_guide",
                List.of(
                        Map.of("phase", "READING", "logged_action", "problem_guide", "propensity", 0.6, "reward", 0.8)
                )
        );

        assertThat(result.eligible()).isFalse();
        assertThat(result.sampleSize()).isEqualTo(1);
        assertThat(result.estimatedReward()).isEqualTo(0.0);
        assertThat(result.reason()).contains("insufficient");
    }

    @Test
    void shouldEstimateIpsRewardFromLoggedBanditSamples() {
        OffPolicyEvalResult result = service.evaluate(
                "READING",
                "problem_guide",
                List.of(
                        Map.of("phase", "READING", "logged_action", "problem_guide", "propensity", 0.5, "reward", 0.80),
                        Map.of("phase", "READING", "logged_action", "problem_guide", "propensity", 0.4, "reward", 0.85),
                        Map.of("phase", "READING", "logged_action", "problem_guide", "propensity", 0.6, "reward", 0.90),
                        Map.of("phase", "READING", "logged_action", "problem_guide", "propensity", 0.5, "reward", 0.88),
                        Map.of("phase", "READING", "logged_action", "problem_guide", "propensity", 0.55, "reward", 0.86)
                )
        );

        assertThat(result.eligible()).isTrue();
        assertThat(result.sampleSize()).isEqualTo(5);
        assertThat(result.estimatedReward()).isGreaterThan(0.8);
        assertThat(result.effectiveSampleSize()).isGreaterThan(3.0);
    }
}
