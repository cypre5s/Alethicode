package com.alethicode.service.aitutor.supplement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeginnerSupplementPlannerServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long LANGUAGE_PACK_ID = 11L;
    private static final Long CURRENT_PROBLEM_ID = 379L;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BeginnerSupplementPlannerService service;

    @BeforeEach
    void setUp() {
        service = new BeginnerSupplementPlannerService(jdbcTemplate);
        stubLanguagePackLookup();
        stubTargetKcLookup();
        stubCourseExampleLookup();
        stubFadedExampleLookup();
        stubObjectiveProblemLookup();
        stubCodingProblemLookup();
    }

    @SuppressWarnings("unchecked")
    @Test
    void stuckTriggerShouldNotRecommendAnotherCodingProblem() throws Exception {
        Map<String, Object> plan = service.buildPlan(
                USER_ID, "stuck", LANGUAGE_PACK_ID, CURRENT_PROBLEM_ID, null, null, 3
        );

        List<Map<String, Object>> cards = (List<Map<String, Object>>) plan.get("cards");
        assertThat(cards)
                .as("stuck trigger should keep learner inside the current problem context, not push another coding problem")
                .noneMatch(card -> "coding_problem".equals(card.get("card_type")));
        verify(jdbcTemplate, never()).query(
                argThat((String sql) -> sql != null
                        && sql.contains("from ai_problem_kc_mapping m")
                        && sql.contains("not in ('choice', 'fill_blank')")),
                any(RowMapper.class),
                any(Object[].class)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void wrongAnswerTriggerShouldNotRecommendAnotherCodingProblem() throws Exception {
        Map<String, Object> plan = service.buildPlan(
                USER_ID, "wrong_answer", LANGUAGE_PACK_ID, CURRENT_PROBLEM_ID, null, null, 3
        );

        List<Map<String, Object>> cards = (List<Map<String, Object>>) plan.get("cards");
        assertThat(cards)
                .as("wrong_answer trigger should focus on remediation, not push a parallel coding problem")
                .noneMatch(card -> "coding_problem".equals(card.get("card_type")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void warmupTriggerShouldStillRecommendCodingProblem() throws Exception {
        Map<String, Object> plan = service.buildPlan(
                USER_ID, "warmup", LANGUAGE_PACK_ID, null, null, null, 3
        );

        List<Map<String, Object>> cards = (List<Map<String, Object>>) plan.get("cards");
        assertThat(cards)
                .as("warmup trigger should still surface a coding practice")
                .anyMatch(card -> "coding_problem".equals(card.get("card_type")));
    }

    @Test
    void codingProblemSelectionShouldExcludeCurrentProblemId() throws Exception {
        service.buildPlan(
                USER_ID, "warmup", LANGUAGE_PACK_ID, CURRENT_PROBLEM_ID, null, null, 3
        );

        verify(jdbcTemplate).query(
                argThat((String sql) -> sql != null
                        && sql.contains("from ai_problem_kc_mapping m")
                        && sql.contains("not in ('choice', 'fill_blank')")
                        && sql.contains("p.id <> ?")),
                any(RowMapper.class),
                any(Object[].class)
        );
    }

    private void stubLanguagePackLookup() {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(LANGUAGE_PACK_ID);
            when(rs.getString("name")).thenReturn("Python 基础");
            when(rs.getString("primary_language")).thenReturn("Python3");
            when(jdbcTemplate.query(
                    argThat((String sql) -> sql != null && sql.contains("from language_pack")
                            && !sql.contains("language_pack_kc")
                            && !sql.contains("language_pack_example")
                            && !sql.contains("language_pack_problem_mapping")),
                    any(RowMapper.class),
                    eq(LANGUAGE_PACK_ID)
            )).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubTargetKcLookup() {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(101L);
            when(rs.getString("name")).thenReturn("for循环与遍历");
            when(rs.getObject("mastery")).thenReturn(0.4d);
            lenient().when(jdbcTemplate.query(
                    argThat((String sql) -> sql != null
                            && sql.contains("from ai_problem_kc_mapping m")
                            && sql.contains("language_pack_kc kc")
                            && sql.contains("learner_kc_mastery km")),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });
            lenient().when(jdbcTemplate.query(
                    argThat((String sql) -> sql != null
                            && sql.contains("from language_pack_kc kc")
                            && sql.contains("learner_kc_mastery km")),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubCourseExampleLookup() {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(9001L);
            when(rs.getString("source_title")).thenReturn("圆面积计算");
            when(rs.getString("normalized_body")).thenReturn("radius = float(input())\narea = 3.1415 * radius * radius");
            when(rs.getString("kc_name")).thenReturn("for循环与遍历");
            when(rs.getString("chapter_title")).thenReturn("第二章 顺序结构");
            when(rs.getObject("document_id")).thenReturn(5001L);
            when(rs.getObject("page_range_start")).thenReturn(12);
            lenient().when(jdbcTemplate.query(
                    argThat((String sql) -> sql != null
                            && sql.contains("from language_pack_example_kc_mapping ekm")
                            && sql.contains("language_pack_chapter")),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubFadedExampleLookup() {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(9001L);
            when(rs.getString("source_title")).thenReturn("圆面积计算 渐退示例");
            when(rs.getString("normalized_body")).thenReturn(
                    "radius = float(input())\narea = 3.1415 * radius * radius\nprint(f\"{area:.4f}\")"
            );
            when(rs.getObject("document_id")).thenReturn(5001L);
            when(rs.getObject("page_range_start")).thenReturn(12);
            lenient().when(jdbcTemplate.query(
                    argThat((String sql) -> sql != null
                            && sql.contains("from language_pack_example_kc_mapping ekm")
                            && !sql.contains("language_pack_chapter")
                            && sql.contains("normalized_body")),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubObjectiveProblemLookup() {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(4001L);
            when(rs.getString("_id")).thenReturn("PPT2-MICRO");
            when(rs.getString("title")).thenReturn("圆面积计算 微练习");
            when(rs.getString("difficulty")).thenReturn("Low");
            when(rs.getString("question_type")).thenReturn("fill_blank");
            lenient().when(jdbcTemplate.query(
                    argThat((String sql) -> sql != null
                            && sql.contains("from ai_problem_kc_mapping m")
                            && sql.contains("in ('choice', 'fill_blank')")),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubCodingProblemLookup() {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(4002L);
            when(rs.getString("_id")).thenReturn("PPT3-CAESAR");
            when(rs.getString("title")).thenReturn("凯撒密码加密");
            when(rs.getString("difficulty")).thenReturn("Mid");
            when(rs.getString("question_type")).thenReturn("coding");
            lenient().when(jdbcTemplate.query(
                    argThat((String sql) -> sql != null
                            && sql.contains("from ai_problem_kc_mapping m")
                            && sql.contains("not in ('choice', 'fill_blank')")),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
