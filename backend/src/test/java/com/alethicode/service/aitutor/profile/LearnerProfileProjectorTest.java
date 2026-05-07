package com.alethicode.service.aitutor.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearnerProfileProjectorTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MasteryService masteryService = mock(MasteryService.class);
    private final LearnerMemoryService learnerMemoryService = mock(LearnerMemoryService.class);
    private final LearnerMemorySemanticRetrievalService memorySemanticRetrievalService =
            mock(LearnerMemorySemanticRetrievalService.class);
    private final LearnerNarrativeSummaryService narrativeSummaryService =
            mock(LearnerNarrativeSummaryService.class);
    private final CrossCourseProfileService crossCourseProfileService = mock(CrossCourseProfileService.class);
    private final LearnerProfileProjector projector = new LearnerProfileProjector(
            jdbcTemplate,
            new ObjectMapper(),
            masteryService,
            learnerMemoryService,
            memorySemanticRetrievalService,
            narrativeSummaryService,
            crossCourseProfileService
    );

    {
        when(narrativeSummaryService.loadOrGenerate(anyLong()))
                .thenReturn(LearnerNarrativeSummaryService.NarrativeSummary.empty(0L));
    }

    @Test
    void projectShouldBlendCalibrationPriorIntoMastery() throws Exception {
        when(masteryService.projectMastery(42L, 1001L)).thenReturn(Map.of(
                "for_loop", 0.2,
                "array_index", 0.7,
                "string_processing", 0.9
        ));
        mockCalibrationState(42L, true, "{\"loop\":0.8,\"array\":0.3,\"recursion\":0.2}");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong())).thenReturn(List.of());
        when(learnerMemoryService.listActiveMemoryRefs(42L)).thenReturn(List.of());
        when(crossCourseProfileService.loadActionBias(42L)).thenReturn(Map.of());

        LearnerState learnerState = projector.project(
                42L,
                1001L,
                Map.of("submissionCount", 0, "consecutiveErrors", 0),
                "READING"
        );

        assertThat(learnerState.calibrated()).isTrue();
        assertThat(learnerState.masteryByKc()).containsEntry("for_loop", 0.41);
        assertThat(learnerState.masteryByKc()).containsEntry("array_index", 0.56);
        assertThat(learnerState.masteryByKc()).containsEntry("string_processing", 0.737);
        assertThat(learnerState.weakKcs()).contains("for_loop", "array_index");
        assertThat(learnerState.recommendedActionBias()).containsEntry("calibration_applied", true);
    }

    @Test
    void projectShouldFallbackToCalibrationPriorWhenProblemHasNoKcMastery() throws Exception {
        when(masteryService.projectMastery(7L, 2002L)).thenReturn(Map.of());
        mockCalibrationState(7L, false, "{\"loop\":0.25,\"array\":0.65}");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong())).thenReturn(List.of());
        when(learnerMemoryService.listActiveMemoryRefs(7L)).thenReturn(List.of());
        when(crossCourseProfileService.loadActionBias(7L)).thenReturn(Map.of());

        LearnerState learnerState = projector.project(
                7L,
                2002L,
                Map.of("submissionCount", 1, "consecutiveErrors", 2),
                "CODING"
        );

        assertThat(learnerState.calibrated()).isFalse();
        assertThat(learnerState.masteryByKc()).containsEntry("loop", 0.25);
        assertThat(learnerState.masteryByKc()).containsEntry("array", 0.65);
        assertThat(learnerState.recommendedActionBias()).containsEntry("calibration_applied", true);
    }

    @Test
    void projectShouldKeepBaseMasteryWhenCalibrationStateMissing() {
        when(masteryService.projectMastery(9L, 3003L)).thenReturn(Map.of("for_loop", 0.48));
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyLong()))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong())).thenReturn(List.of());
        when(learnerMemoryService.listActiveMemoryRefs(9L)).thenReturn(List.of());
        when(crossCourseProfileService.loadActionBias(9L)).thenReturn(Map.of());

        LearnerState learnerState = projector.project(
                9L,
                3003L,
                Map.of("submissionCount", 0, "consecutiveErrors", 1),
                "IDEATING"
        );

        assertThat(learnerState.calibrated()).isFalse();
        assertThat(learnerState.masteryByKc()).containsEntry("for_loop", 0.48);
        assertThat(learnerState.recommendedActionBias()).containsEntry("calibration_applied", false);
    }

    @SuppressWarnings("unchecked")
    private void mockCalibrationState(Long userId, boolean calibrated, String accumulatedJson) throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), org.mockito.ArgumentMatchers.eq(userId)))
                .thenAnswer(invocation -> {
                    RowMapper<Object> rowMapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getBoolean("calibrated")).thenReturn(calibrated);
                    when(resultSet.getString("accumulated_json")).thenReturn(accumulatedJson);
                    return rowMapper.mapRow(resultSet, 0);
                });
    }
}
