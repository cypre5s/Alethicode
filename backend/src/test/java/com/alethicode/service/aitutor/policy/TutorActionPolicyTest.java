package com.alethicode.service.aitutor.policy;

import com.alethicode.service.aitutor.profile.LearnerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TutorActionPolicyTest {

    private final TutorActionPolicy policy = new TutorActionPolicy();

    @Test
    void shouldRecommendReIdeateWhenStudentIsHighlyFrustrated() {
        LearnerState learnerState = new LearnerState(
                false,
                Map.of("循环", 0.2),
                List.of("循环"),
                Map.of(),
                Map.of("consecutiveErrors", 4),
                "high",
                "low",
                Map.of(),
                List.of(),
                "",
                true
        );

        TutorActionDecision decision = policy.decide("ERROR_FEEDBACK", "", learnerState);

        assertThat(decision.recommendedAction()).isEqualTo("re_ideate");
        assertThat(decision.confidence()).isEqualTo("high");
        assertThat(decision.availableActions().getFirst().get("key")).isEqualTo("re_ideate");
    }

    @Test
    void shouldKeepAcReviewWhenCrossCourseWeakKcsRemain() {
        LearnerState learnerState = new LearnerState(
                true,
                Map.of("循环", 0.45),
                List.of("循环"),
                Map.of(),
                Map.of("consecutiveErrors", 0, "submissionCount", 1),
                "low",
                "medium",
                Map.of("cross_course_weak_kcs", List.of("循环"), "source", "profile_snapshot"),
                List.of(),
                "",
                true
        );

        TutorActionDecision decision = policy.decide("AC_REVIEW", "", learnerState);

        assertThat(decision.recommendedAction()).isEqualTo("ac_review");
        assertThat(decision.reason()).contains("薄弱");
        assertThat(decision.availableActions().getFirst().get("key")).isEqualTo("ac_review");
    }
}
