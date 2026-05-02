package com.alethicode.service.aitutor.policy;

import com.alethicode.service.aitutor.profile.LearnerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ContextualBanditRerankerTest {

    private final ContextualBanditReranker reranker = new ContextualBanditReranker();

    @Test
    void shouldFavorReadingGuideWhenMemoryAndLowConfidencePreferReadingSupport() {
        TutorActionDecision ruleDecision = new TutorActionDecision(
                "ideate",
                "medium",
                "rule",
                List.of(
                        Map.of("key", "ideate", "label", "继续思路分析", "event", "IDEATING"),
                        Map.of("key", "problem_guide", "label", "获取题目导读", "event", "READING")
                )
        );
        LearnerState learnerState = new LearnerState(
                false,
                Map.of("循环", 0.3),
                List.of("循环"),
                Map.of(),
                Map.of("consecutiveErrors", 0),
                "low",
                "low",
                Map.of("cross_course_weak_kcs", List.of("循环"), "source", "profile_snapshot"),
                List.of(Map.of("memory_key", "reading_pref", "confidence", 0.95, "source_problem_id", 1001L)),
                "",
                true
        );

        BanditDecision banditDecision = reranker.rerank("READING", ruleDecision, learnerState);

        assertThat(banditDecision.chosenAction()).isEqualTo("problem_guide");
        assertThat(banditDecision.probabilities()).containsKeys("ideate", "problem_guide");
        assertThat(banditDecision.probabilities().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, within(0.0001));
        assertThat(banditDecision.reason()).contains("reading_pref");
    }

    @Test
    void shouldFavorReIdeateForSeverelyFrustratedLearner() {
        TutorActionDecision ruleDecision = new TutorActionDecision(
                "coding",
                "high",
                "rule",
                List.of(
                        Map.of("key", "coding", "label", "继续编码", "event", "CODING"),
                        Map.of("key", "re_ideate", "label", "重新梳理思路", "event", "IDEATING")
                )
        );
        LearnerState learnerState = new LearnerState(
                false,
                Map.of("边界", 0.2),
                List.of("边界"),
                Map.of(),
                Map.of("consecutiveErrors", 5),
                "severe",
                "low",
                Map.of(),
                List.of(),
                "",
                true
        );

        BanditDecision banditDecision = reranker.rerank("ERROR_FEEDBACK", ruleDecision, learnerState);

        assertThat(banditDecision.chosenAction()).isEqualTo("re_ideate");
        assertThat(banditDecision.reason()).contains("frustration");
    }
}
