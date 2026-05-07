package com.alethicode.service.classroom.ai;

import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassroomAssignmentSmartComposerTest {

    private static final String CLASSROOM_ID = "classroom-1";
    private static final Long LP_ID = 11L;

    @Test
    @SuppressWarnings({"unchecked"})
    void shouldComposeFromExplicitKcAndDedupeProblems() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomKcResolver resolver = mock(ClassroomKcResolver.class);
        MasteryService mastery = mock(MasteryService.class);
        BeginnerSupplementPlannerService planner = mock(BeginnerSupplementPlannerService.class);

        when(resolver.resolveLanguagePackId(CLASSROOM_ID)).thenReturn(LP_ID);
        when(resolver.expandKcIds(eq(CLASSROOM_ID), any())).thenReturn(List.of(1L, 2L));
        when(resolver.loadKcNameMap(eq(LP_ID), eq(List.of(1L, 2L)))).thenReturn(Map.of(1L, "循环", 2L, "条件"));

        when(jdbc.queryForList(anyString(), eq(Long.class), eq(CLASSROOM_ID))).thenReturn(List.of(101L, 102L));

        when(mastery.projectMasteryByLanguagePack(101L, LP_ID)).thenReturn(Map.of("循环", 0.3, "条件", 0.6));
        when(mastery.projectMasteryByLanguagePack(102L, LP_ID)).thenReturn(Map.of("循环", 0.4, "条件", 0.5));

        Map<String, Object> codingPayload1 = Map.of(
                "problem_id", 200L, "problem_key", "P-1", "title", "题1", "difficulty", "Mid", "question_type", "coding"
        );
        Map<String, Object> objectivePayload1 = Map.of(
                "problem_id", 201L, "problem_key", "P-2", "title", "题2", "difficulty", "Low", "question_type", "choice"
        );
        Map<String, Object> codingPayload2 = Map.of(
                "problem_id", 200L, "problem_key", "P-1", "title", "题1", "difficulty", "Mid", "question_type", "coding"
        );
        Map<String, Object> codingPayload3 = Map.of(
                "problem_id", 202L, "problem_key", "P-3", "title", "题3", "difficulty", "Mid", "question_type", "coding"
        );
        when(planner.buildPlan(anyLong(), anyString(), anyLong(), any(), any(), any(), anyInt()))
                .thenReturn(Map.of(
                        "cards", List.of(
                                Map.of("card_type", "coding_problem", "payload", codingPayload1),
                                Map.of("card_type", "objective_problem", "payload", objectivePayload1)
                        )))
                .thenReturn(Map.of(
                        "cards", List.of(
                                Map.of("card_type", "coding_problem", "payload", codingPayload2),
                                Map.of("card_type", "coding_problem", "payload", codingPayload3)
                        )));

        ClassroomAssignmentSmartComposer composer = new ClassroomAssignmentSmartComposer(jdbc, resolver, mastery, planner);
        Map<String, Object> result = composer.composeForClassroom(CLASSROOM_ID, List.<Long>of(1L, 2L), 3, 5);

        assertThat(result.get("compose_strategy")).isEqualTo("smart_kc");
        assertThat(result.get("language_pack_id")).isEqualTo(LP_ID);
        assertThat((List<Long>) result.get("kc_ids")).containsExactly(1L, 2L);
        List<Map<String, Object>> sections = (List<Map<String, Object>>) result.get("sections");
        assertThat(sections).hasSize(2);
        assertThat(((List<?>) sections.get(0).get("problems"))).hasSize(2);
        assertThat(((List<?>) sections.get(1).get("problems"))).hasSize(1);
        assertThat(result.get("total_picked")).isEqualTo(3);
    }

    @Test
    void shouldFailfastWhenNoWeakKcAndNoExplicitKc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomKcResolver resolver = mock(ClassroomKcResolver.class);
        MasteryService mastery = mock(MasteryService.class);
        BeginnerSupplementPlannerService planner = mock(BeginnerSupplementPlannerService.class);

        when(resolver.resolveLanguagePackId(CLASSROOM_ID)).thenReturn(LP_ID);
        when(resolver.expandKcIds(eq(CLASSROOM_ID), any())).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(CLASSROOM_ID))).thenReturn(List.of(101L));
        when(mastery.projectMasteryByLanguagePack(eq(101L), eq(LP_ID)))
                .thenReturn(Map.of("循环", 0.9, "条件", 0.95));

        ClassroomAssignmentSmartComposer composer = new ClassroomAssignmentSmartComposer(jdbc, resolver, mastery, planner);
        assertThatThrownBy(() -> composer.composeForClassroom(CLASSROOM_ID, List.<Long>of(), null, null))
                .hasMessageContaining("没有可识别的薄弱 KC");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void resolveClassroomProblemIdsByProblemIdShouldQueryAndReturnIds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomKcResolver resolver = mock(ClassroomKcResolver.class);
        MasteryService mastery = mock(MasteryService.class);
        BeginnerSupplementPlannerService planner = mock(BeginnerSupplementPlannerService.class);
        ClassroomAssignmentSmartComposer composer = new ClassroomAssignmentSmartComposer(jdbc, resolver, mastery, planner);

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of("cp-1", "cp-2"));

        List<String> ids = composer.resolveClassroomProblemIdsByProblemId(CLASSROOM_ID, List.of(1L, 2L));
        assertThat(ids).containsExactly("cp-1", "cp-2");
    }
}
