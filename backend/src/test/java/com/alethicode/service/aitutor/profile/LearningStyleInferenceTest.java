package com.alethicode.service.aitutor.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningStyleInferenceTest {

    private JdbcTemplate jdbcTemplate;
    private LearnerMemoryService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new LearnerMemoryService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void returnsDefaultWhenUserIsNull() {
        assertThat(service.inferLearningStyle(null)).isEqualTo(LearningStyle.STEP_BY_STEP);
    }

    @Test
    void returnsDefaultWhenFeedbackBelowThreshold() {
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(buildFeedback(
                List.of("worked_example"), "positive", 5));
        assertThat(service.inferLearningStyle(99L)).isEqualTo(LearningStyle.STEP_BY_STEP);
    }

    @Test
    void returnsVisualWhenWorkedExamplePositivesDominate() {
        List<Map<String, Object>> feedback = new ArrayList<>();
        feedback.addAll(buildFeedback(List.of("worked_example"), "positive", 15));
        feedback.addAll(buildFeedback(List.of("faded_example"), "positive", 5));
        feedback.addAll(buildFeedback(List.of("minimal_hint"), "positive", 3));
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(feedback);

        assertThat(service.inferLearningStyle(99L)).isEqualTo(LearningStyle.VISUAL);
    }

    @Test
    void returnsExploratoryWhenMinimalHintPositivesDominate() {
        List<Map<String, Object>> feedback = new ArrayList<>();
        feedback.addAll(buildFeedback(List.of("minimal_hint"), "positive", 18));
        feedback.addAll(buildFeedback(List.of("worked_example"), "negative", 3));
        feedback.addAll(buildFeedback(List.of("problem_guide"), "positive", 1));
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(feedback);

        assertThat(service.inferLearningStyle(99L)).isEqualTo(LearningStyle.EXPLORATORY);
    }

    @Test
    void returnsAnalyticalWhenErrorDiagnosisPositivesDominate() {
        List<Map<String, Object>> feedback = new ArrayList<>();
        feedback.addAll(buildFeedback(List.of("error_diagnosis"), "positive", 12));
        feedback.addAll(buildFeedback(List.of("problem_guide"), "positive", 10));
        feedback.addAll(buildFeedback(List.of("worked_example"), "negative", 3));
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(feedback);

        assertThat(service.inferLearningStyle(99L)).isEqualTo(LearningStyle.ANALYTICAL);
    }

    @Test
    void returnsDefaultWhenAllFeedbackIsNegative() {
        List<Map<String, Object>> feedback = new ArrayList<>();
        feedback.addAll(buildFeedback(List.of("worked_example"), "negative", 11));
        feedback.addAll(buildFeedback(List.of("minimal_hint"), "negative", 10));
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(feedback);

        assertThat(service.inferLearningStyle(99L)).isEqualTo(LearningStyle.STEP_BY_STEP);
    }

    @Test
    void postAcAndTransferProblemFeedbackDoNotDiluteSpecificStyleVote() {
        List<Map<String, Object>> feedback = new ArrayList<>();
        feedback.addAll(buildFeedback(List.of("minimal_hint"), "positive", 8));
        feedback.addAll(buildFeedback(List.of("post_ac"), "positive", 6));
        feedback.addAll(buildFeedback(List.of("transfer_problem"), "positive", 6));
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(feedback);

        assertThat(service.inferLearningStyle(99L)).isEqualTo(LearningStyle.EXPLORATORY);
    }

    private static List<Map<String, Object>> buildFeedback(List<String> strategyTypes, String rating, int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (String type : strategyTypes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("memory_key", "strategy_pref_" + type);
                row.put("payload_text",
                        "{\"strategy_type\":\"" + type + "\",\"rating\":\"" + rating + "\"}");
                row.put("confidence", 0.8);
                rows.add(row);
            }
        }
        return rows;
    }
}
