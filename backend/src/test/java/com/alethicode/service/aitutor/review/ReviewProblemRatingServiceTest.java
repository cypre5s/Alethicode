package com.alethicode.service.aitutor.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.exception.LegacyBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewProblemRatingServiceTest {

    private static final Long USER_ID = 5L;
    private static final String PKG = "pkg-abc";
    private static final String PROBLEM_ROW = "row-1";

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SpecializedProblemGenerator specializedProblemGenerator;
    @Mock private ErrorReviewPackageService errorReviewPackageService;

    private ReviewProblemRatingService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ReviewProblemRatingService(jdbcTemplate, objectMapper, specializedProblemGenerator, errorReviewPackageService);
    }

    @SuppressWarnings("unchecked")
    @Test
    void goodRatingShouldWriteRatingAndAdvanceFsrsWhenAllProblemsGood() {
        stubOwnerAndProblem(true);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("count(*) from ai_error_review_problem")),
                eq(Integer.class),
                eq(PKG)
        )).thenReturn(0);
        when(errorReviewPackageService.getPackageDetail(USER_ID, PKG)).thenReturn(stubResponse());

        ReviewPackageResponse response = service.rateProblem(USER_ID, PKG, PROBLEM_ROW, "good");

        assertThat(response).isNotNull();
        verify(jdbcTemplate).update(
                eq("update ai_error_review_problem set user_rating = ?, rated_at = now() where id = ? and package_id = ?"),
                eq("good"), eq(PROBLEM_ROW), eq(PKG)
        );
        verify(errorReviewPackageService).advancePackageScheduleAfterMastery(eq(PKG), eq(USER_ID), any());
        verify(specializedProblemGenerator, never()).generateOne(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void goodRatingShouldNotAdvanceFsrsWhenOtherProblemsRemain() {
        stubOwnerAndProblem(true);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("count(*) from ai_error_review_problem")),
                eq(Integer.class),
                eq(PKG)
        )).thenReturn(2);
        when(errorReviewPackageService.getPackageDetail(USER_ID, PKG)).thenReturn(stubResponse());

        service.rateProblem(USER_ID, PKG, PROBLEM_ROW, "good");

        verify(errorReviewPackageService, never()).advancePackageScheduleAfterMastery(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void againRatingShouldGenerateSimilarProblemAndAppend() {
        stubOwnerAndProblem(true);
        Map<String, Object> evidence = Map.of(
                "error_taxonomy", "logic_error",
                "evidence_json", "{\"recent_root_causes\":[\"loop never updates i\"]}"
        );
        when(jdbcTemplate.queryForMap(
                argThat(sql -> sql != null && sql.contains("evidence_summary::text as evidence_json")),
                eq(PKG)
        )).thenReturn(evidence);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("max(sequence)")),
                eq(Integer.class),
                eq(PKG)
        )).thenReturn(3);
        when(specializedProblemGenerator.generateOne(eq(USER_ID), eq("logic_error"), eq(List.of("loop never updates i")), eq(List.of(42L))))
                .thenReturn(999L);
        when(errorReviewPackageService.getPackageDetail(USER_ID, PKG)).thenReturn(stubResponse());

        service.rateProblem(USER_ID, PKG, PROBLEM_ROW, "again");

        verify(jdbcTemplate).update(
                eq("update ai_error_review_problem set user_rating = ?, rated_at = now() where id = ? and package_id = ?"),
                eq("again"), eq(PROBLEM_ROW), eq(PKG)
        );
        verify(specializedProblemGenerator).appendOneToPackage(PKG, 999L, 4);
        verify(jdbcTemplate).update(
                eq("update ai_error_review_package set problem_count = problem_count + 1, updated_at = now() where id = ?"),
                eq(PKG)
        );
        verify(errorReviewPackageService, never()).advancePackageScheduleAfterMastery(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRejectRatingWhenProblemNotSubmitted() {
        stubOwnerAndProblem(false);

        assertThatThrownBy(() -> service.rateProblem(USER_ID, PKG, PROBLEM_ROW, "good"))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("请先完成本题再评分");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRejectRatingWhenPackageOwnedByAnotherUser() {
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("select user_id from ai_error_review_package")),
                any(RowMapper.class),
                eq(PKG)
        )).thenReturn(List.of(99L));

        assertThatThrownBy(() -> service.rateProblem(USER_ID, PKG, PROBLEM_ROW, "good"))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("复习包不属于当前用户");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRejectInvalidRatingValue() {
        assertThatThrownBy(() -> service.rateProblem(USER_ID, PKG, PROBLEM_ROW, "lazy"))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("rating 必须是 again 或 good");
    }

    @SuppressWarnings("unchecked")
    private void stubOwnerAndProblem(boolean submitted) {
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("select user_id from ai_error_review_package")),
                any(RowMapper.class),
                eq(PKG)
        )).thenReturn(List.of(USER_ID));
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from ai_error_review_problem where id = ? and package_id = ?")),
                any(RowMapper.class),
                eq(PROBLEM_ROW), eq(PKG)
        )).thenReturn(List.of(new ReviewProblemRatingService.ReviewProblemRow(PROBLEM_ROW, 42L, 1, submitted, null)));
    }

    private ReviewPackageResponse stubResponse () {
        return new ReviewPackageResponse(PKG, "logic_error", "逻辑错误", Map.of(),
                3, 1, false, List.of(), "2026-04-25T00:00:00Z",
                "review", "2026-04-26T00:00:00Z", 1.5, 4.0, 0.85);
    }
}
