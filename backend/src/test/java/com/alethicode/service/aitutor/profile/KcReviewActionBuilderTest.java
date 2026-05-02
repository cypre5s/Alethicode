package com.alethicode.service.aitutor.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KcReviewActionBuilderTest {

    @Test
    void returnsEmptyListForMasteryAtOrAboveWeakThreshold() {
        List<Map<String, Object>> actions = KcReviewActionBuilder.buildForWeakKc(
                "循环结构", 0.4, 5L, 3L, 10L);
        assertThat(actions).isEmpty();

        List<Map<String, Object>> stronger = KcReviewActionBuilder.buildForWeakKc(
                "循环结构", 0.75, 5L, 3L, 10L);
        assertThat(stronger).isEmpty();
    }

    @Test
    void alwaysIncludesAiExplainForWeakKc() {
        List<Map<String, Object>> actions = KcReviewActionBuilder.buildForWeakKc(
                "循环结构", 0.2, null, null, null);
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0))
                .containsEntry("key", "ai_explain")
                .containsEntry("label", "请 AI 讲解「循环结构」");
    }

    @Test
    void addsReviewNotebookWhenHasFailedSubmissions() {
        List<Map<String, Object>> actions = KcReviewActionBuilder.buildForWeakKc(
                "循环结构", 0.35, 10L, 3L, 5L);
        assertThat(actions)
                .extracting(a -> a.get("key"))
                .containsExactly("ai_explain", "review_notebook");
    }

    @Test
    void addsBeginnerProblemsWhenMasteryVeryLowAndHasProblems() {
        List<Map<String, Object>> actions = KcReviewActionBuilder.buildForWeakKc(
                "循环结构", 0.1, 0L, 0L, 8L);
        assertThat(actions)
                .extracting(a -> a.get("key"))
                .containsExactly("ai_explain", "beginner_problems");
    }

    @Test
    void addsAllThreeWhenAllConditionsMet() {
        List<Map<String, Object>> actions = KcReviewActionBuilder.buildForWeakKc(
                "循环结构", 0.15, 20L, 5L, 12L);
        assertThat(actions)
                .extracting(a -> a.get("key"))
                .containsExactly("ai_explain", "review_notebook", "beginner_problems");
    }

    @Test
    void rejectsBlankKcName() {
        assertThatThrownBy(() -> KcReviewActionBuilder.buildForWeakKc(
                "  ", 0.2, 1L, 0L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachActionHasKeyLabelAndHint() {
        List<Map<String, Object>> actions = KcReviewActionBuilder.buildForWeakKc(
                "循环结构", 0.15, 20L, 5L, 12L);
        for (Map<String, Object> action : actions) {
            assertThat(action).containsKeys("key", "label", "hint");
            assertThat(action.get("label")).isNotNull();
            assertThat(String.valueOf(action.get("label"))).isNotBlank();
        }
    }
}
