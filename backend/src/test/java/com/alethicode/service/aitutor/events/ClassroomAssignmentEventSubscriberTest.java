package com.alethicode.service.aitutor.events;

import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassroomAssignmentEventSubscriberTest {

    private static final Long USER_ID = 9L;
    private static final String ASSIGNMENT_ID = "assign-1";
    private static final Long PROBLEM_ID = 379L;
    private static final Long LP_ID = 11L;
    private static final String DETAIL_ID = "detail-1";

    @Test
    void acShouldUpdateMasteryAndNotCreateReviewPackage() {
        MasteryService mastery = mock(MasteryService.class);
        ErrorReviewPackageService review = mock(ErrorReviewPackageService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomAssignmentEventSubscriber subscriber = new ClassroomAssignmentEventSubscriber(mastery, review, jdbc);

        ClassroomAssignmentEventSubscriber.Outcome outcome = subscriber.onAssignmentSubmissionGraded(
                USER_ID, ASSIGNMENT_ID, PROBLEM_ID, true, null, LP_ID, DETAIL_ID
        );

        assertThat(outcome.masteryUpdated()).isTrue();
        assertThat(outcome.reviewPackageId()).isNull();
        verify(mastery).applyEvidence(USER_ID, PROBLEM_ID, true, "classroom_assignment", null);
        verify(review, never()).createPackage(any(), any(), any(), any(), any());
    }

    @Test
    void waWithTaxonomyShouldCreateReviewPackageAndPersistId() {
        MasteryService mastery = mock(MasteryService.class);
        ErrorReviewPackageService review = mock(ErrorReviewPackageService.class);
        ReviewPackageResponse pkg = new ReviewPackageResponse(
                "pkg-1", "logic_error", "label", Map.of(), 3, 0, false, List.of(),
                "2026-05-02T01:00:00Z", "scheduled", "2026-05-09T01:00:00Z", 0.0, 0.0, 0.5
        );
        when(review.createPackage(eq(USER_ID), eq("logic_error"), eq(LP_ID), eq(PROBLEM_ID), eq("wrong_answer")))
                .thenReturn(pkg);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomAssignmentEventSubscriber subscriber = new ClassroomAssignmentEventSubscriber(mastery, review, jdbc);

        ClassroomAssignmentEventSubscriber.Outcome outcome = subscriber.onAssignmentSubmissionGraded(
                USER_ID, ASSIGNMENT_ID, PROBLEM_ID, false, "logic_error", LP_ID, DETAIL_ID
        );

        assertThat(outcome.masteryUpdated()).isTrue();
        assertThat(outcome.reviewPackageId()).isEqualTo("pkg-1");
        verify(mastery).applyEvidence(USER_ID, PROBLEM_ID, false, "classroom_assignment", "logic_error");
        verify(jdbc, times(1)).update(
                anyString(),
                eq("pkg-1"), eq("logic_error"), eq(DETAIL_ID)
        );
    }

    @Test
    void waWithoutTaxonomyShouldOnlyUpdateMastery() {
        MasteryService mastery = mock(MasteryService.class);
        ErrorReviewPackageService review = mock(ErrorReviewPackageService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomAssignmentEventSubscriber subscriber = new ClassroomAssignmentEventSubscriber(mastery, review, jdbc);

        ClassroomAssignmentEventSubscriber.Outcome outcome = subscriber.onAssignmentSubmissionGraded(
                USER_ID, ASSIGNMENT_ID, PROBLEM_ID, false, null, LP_ID, DETAIL_ID
        );

        assertThat(outcome.masteryUpdated()).isTrue();
        assertThat(outcome.reviewPackageId()).isNull();
        verify(review, never()).createPackage(any(), any(), any(), any(), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void shouldFailfastSilentlyWhenIdentifiersMissing() {
        MasteryService mastery = mock(MasteryService.class);
        ErrorReviewPackageService review = mock(ErrorReviewPackageService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomAssignmentEventSubscriber subscriber = new ClassroomAssignmentEventSubscriber(mastery, review, jdbc);

        ClassroomAssignmentEventSubscriber.Outcome outcome = subscriber.onAssignmentSubmissionGraded(
                null, ASSIGNMENT_ID, PROBLEM_ID, true, null, LP_ID, DETAIL_ID
        );

        assertThat(outcome.masteryUpdated()).isFalse();
        assertThat(outcome.reviewPackageId()).isNull();
        verify(mastery, never()).applyEvidence(anyLong(), anyLong(), anyBoolean(), anyString(), any());
    }

    @Test
    void masteryFailureShouldNotPropagateButReviewMaySucceed() {
        MasteryService mastery = mock(MasteryService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("nfk-down")).when(mastery)
                .applyEvidence(anyLong(), anyLong(), anyBoolean(), anyString(), any());
        ErrorReviewPackageService review = mock(ErrorReviewPackageService.class);
        ReviewPackageResponse pkg = new ReviewPackageResponse(
                "pkg-2", "syntax_error", "label", Map.of(), 3, 0, false, List.of(),
                "2026-05-02T01:00:00Z", "scheduled", "2026-05-09T01:00:00Z", 0.0, 0.0, 0.5
        );
        when(review.createPackage(any(), any(), any(), any(), any())).thenReturn(pkg);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassroomAssignmentEventSubscriber subscriber = new ClassroomAssignmentEventSubscriber(mastery, review, jdbc);

        ClassroomAssignmentEventSubscriber.Outcome outcome = subscriber.onAssignmentSubmissionGraded(
                USER_ID, ASSIGNMENT_ID, PROBLEM_ID, false, "syntax_error", LP_ID, DETAIL_ID
        );

        assertThat(outcome.masteryUpdated()).isFalse();
        assertThat(outcome.reviewPackageId()).isEqualTo("pkg-2");
    }
}
