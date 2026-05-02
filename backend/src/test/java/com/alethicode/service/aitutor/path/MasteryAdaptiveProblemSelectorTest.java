package com.alethicode.service.aitutor.path;

import com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasteryAdaptiveProblemSelectorTest {

    private static final Long USER_ID = 9L;
    private static final Long LP_ID = 11L;

    @Test
    void shouldReturnNoWeakKcWhenPlanHasNoCard() {
        BeginnerSupplementPlannerService planner = mock(BeginnerSupplementPlannerService.class);
        when(planner.buildPlan(eq(USER_ID), eq("warmup"), eq(LP_ID), any(), any(), any(), any()))
                .thenReturn(Map.of("cards", List.of(), "target_kcs", List.of()));

        MasteryAdaptiveProblemSelector selector = new MasteryAdaptiveProblemSelector(planner);
        Map<String, Object> result = selector.selectNextProblem(USER_ID, LP_ID);

        assertThat(result.get("status")).isEqualTo("no_weak_kc");
        assertThat(result.get("recommended")).isNull();
        assertThat(result.get("selection_strategy")).isEqualTo("adaptive_boundary");
    }

    @Test
    void shouldExtractCodingProblemCardWhenPresent() {
        BeginnerSupplementPlannerService planner = mock(BeginnerSupplementPlannerService.class);
        Map<String, Object> codingPayload = new LinkedHashMap<>();
        codingPayload.put("problem_id", 379L);
        codingPayload.put("problem_key", "AI-DEMO-1");
        codingPayload.put("title", "练习题");
        codingPayload.put("difficulty", "Mid");
        codingPayload.put("question_type", "coding");
        Map<String, Object> codingCard = new LinkedHashMap<>();
        codingCard.put("card_type", "coding_problem");
        codingCard.put("why_this_now", "正向练习");
        codingCard.put("payload", codingPayload);
        when(planner.buildPlan(eq(USER_ID), eq("warmup"), eq(LP_ID), any(), any(), any(), any()))
                .thenReturn(Map.of(
                        "cards", List.of(codingCard),
                        "target_kcs", List.of("循环")
                ));

        MasteryAdaptiveProblemSelector selector = new MasteryAdaptiveProblemSelector(planner);
        Map<String, Object> result = selector.selectNextProblem(USER_ID, LP_ID);

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("weakest_kc")).isEqualTo("循环");
        assertThat(result.get("selection_strategy")).isEqualTo("adaptive_boundary");
        Map<?, ?> recommended = (Map<?, ?>) result.get("recommended");
        assertThat(recommended).isNotNull();
        assertThat(recommended.get("problem_id")).isEqualTo(379L);
        assertThat(recommended.get("title")).isEqualTo("练习题");
        assertThat(recommended.get("question_type")).isEqualTo("coding");
        assertThat(result.get("target_difficulty")).isEqualTo("Mid");
        List<?> candidates = (List<?>) result.get("candidates");
        assertThat(candidates).hasSize(1);
    }

    @Test
    void shouldFallbackToObjectiveProblemCardWhenNoCoding() {
        BeginnerSupplementPlannerService planner = mock(BeginnerSupplementPlannerService.class);
        Map<String, Object> objectivePayload = new LinkedHashMap<>();
        objectivePayload.put("problem_id", 88L);
        objectivePayload.put("problem_key", "AI-OBJ-1");
        objectivePayload.put("title", "判断题");
        objectivePayload.put("difficulty", "Low");
        objectivePayload.put("question_type", "choice");
        Map<String, Object> objectiveCard = new LinkedHashMap<>();
        objectiveCard.put("card_type", "objective_problem");
        objectiveCard.put("why_this_now", "回忆性练习");
        objectiveCard.put("payload", objectivePayload);
        when(planner.buildPlan(eq(USER_ID), eq("warmup"), eq(LP_ID), any(), any(), any(), any()))
                .thenReturn(Map.of(
                        "cards", List.of(objectiveCard),
                        "target_kcs", List.of("条件分支")
                ));

        MasteryAdaptiveProblemSelector selector = new MasteryAdaptiveProblemSelector(planner);
        Map<String, Object> result = selector.selectNextProblem(USER_ID, LP_ID);

        assertThat(result.get("status")).isEqualTo("ok");
        Map<?, ?> recommended = (Map<?, ?>) result.get("recommended");
        assertThat(recommended.get("question_type")).isEqualTo("choice");
        assertThat(recommended.get("card_type")).isEqualTo("objective_problem");
    }

    @Test
    void shouldRejectBlankUserOrLanguagePackId() {
        BeginnerSupplementPlannerService planner = mock(BeginnerSupplementPlannerService.class);
        MasteryAdaptiveProblemSelector selector = new MasteryAdaptiveProblemSelector(planner);

        assertThatThrownBy(() -> selector.selectNextProblem(null, LP_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user_id");
        assertThatThrownBy(() -> selector.selectNextProblem(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language_pack_id");
    }
}
