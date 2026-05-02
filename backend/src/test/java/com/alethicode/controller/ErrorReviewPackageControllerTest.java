package com.alethicode.controller;

import com.alethicode.dto.request.CreateReviewPackageRequest;
import com.alethicode.dto.request.CreateReviewPackagesRequest;
import com.alethicode.dto.request.ReviewPackageReviewRequest;
import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import com.alethicode.service.aitutor.review.FsrsSchedulerService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorReviewPackageControllerTest {

    @Test
    void createPackagesShouldUseBatchResourceAndReturnMultiplePackages() {
        ErrorReviewPackageService reviewPackageService = mock(ErrorReviewPackageService.class);
        com.alethicode.service.aitutor.review.ReviewProblemRatingService ratingService =
                mock(com.alethicode.service.aitutor.review.ReviewProblemRatingService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Authentication authentication = mock(Authentication.class);
        ErrorReviewPackageController controller = new ErrorReviewPackageController(reviewPackageService, ratingService, jdbcTemplate);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");
        when(jdbcTemplate.query(
                eq("select id from \"user\" where lower(username) = ?"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("alice")
        )).thenReturn(List.of(7L));
        List<CreateReviewPackageRequest> items = List.of(
                new CreateReviewPackageRequest("logic_error", 3L, 101L, "wrong_answer"),
                new CreateReviewPackageRequest("input_parsing", 3L, 102L, "wrong_answer")
        );
        List<ReviewPackageResponse> expected = List.of(
                new ReviewPackageResponse("pkg-logic", "logic_error", "逻辑错误", Map.of(),
                        3, 0, false, List.of(), "2026-04-25T00:00:00Z",
                        "new", "2026-04-26T00:00:00Z", 1.0, 5.0, 1.0),
                new ReviewPackageResponse("pkg-input", "input_parsing", "输入解析错误", Map.of(),
                        3, 0, false, List.of(), "2026-04-25T00:00:00Z",
                        "new", "2026-04-26T00:00:00Z", 1.0, 5.0, 1.0)
        );
        when(reviewPackageService.createPackages(eq(7L), eq(items))).thenReturn(expected);

        Object data = controller.createPackages(new CreateReviewPackagesRequest(items), authentication).data();

        assertThat(data).isSameAs(expected);
        verify(reviewPackageService).createPackages(eq(7L), eq(items));
    }

    @Test
    void reviewPackageShouldUseNestedReviewResourceAndExplicitFsrsRating() {
        ErrorReviewPackageService reviewPackageService = mock(ErrorReviewPackageService.class);
        com.alethicode.service.aitutor.review.ReviewProblemRatingService ratingService =
                mock(com.alethicode.service.aitutor.review.ReviewProblemRatingService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Authentication authentication = mock(Authentication.class);
        ErrorReviewPackageController controller = new ErrorReviewPackageController(reviewPackageService, ratingService, jdbcTemplate);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");
        when(jdbcTemplate.query(
                eq("select id from \"user\" where lower(username) = ?"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("alice")
        )).thenReturn(List.of(7L));
        ReviewPackageResponse expected = new ReviewPackageResponse(
                "pkg-7", "logic_error", "逻辑错误", Map.of(),
                1, 1, true, List.of(), "2026-04-25T00:00:00Z",
                "review", "2026-04-26T00:00:00Z", 2.0, 4.0, 0.8
        );
        when(reviewPackageService.reviewPackage(eq(7L), eq("pkg-7"), eq(FsrsSchedulerService.ReviewRating.GOOD), any()))
                .thenReturn(expected);

        Object data = controller.reviewPackage("pkg-7", new ReviewPackageReviewRequest("good"), authentication).data();

        assertThat(data).isSameAs(expected);
        verify(reviewPackageService).reviewPackage(eq(7L), eq("pkg-7"), eq(FsrsSchedulerService.ReviewRating.GOOD), any());
    }
}
