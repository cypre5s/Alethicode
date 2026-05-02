package com.alethicode.controller;

import com.alethicode.dto.request.ReviewProblemRatingRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.exception.LegacyBusinessException;
import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import com.alethicode.service.aitutor.review.ReviewProblemRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorReviewPackageProblemRatingTest {

    private static final Long USER_ID = 11L;
    private static final String PKG = "pkg-1";
    private static final String ROW = "row-1";

    private ErrorReviewPackageService reviewPackageService;
    private ReviewProblemRatingService ratingService;
    private JdbcTemplate jdbcTemplate;
    private ErrorReviewPackageController controller;

    @BeforeEach
    void setUp() {
        reviewPackageService = mock(ErrorReviewPackageService.class);
        ratingService = mock(ReviewProblemRatingService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        controller = new ErrorReviewPackageController(reviewPackageService, ratingService, jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnSuccessResponseForValidGoodRating() {
        Authentication auth = stubAuthenticatedUser("alice");
        ReviewPackageResponse expected = stubResponse();
        when(ratingService.rateProblem(USER_ID, PKG, ROW, "good")).thenReturn(expected);

        ApiResponse<Object> resp = controller.rateProblem(PKG, ROW, new ReviewProblemRatingRequest("good"), auth);

        assertThat(resp.data()).isSameAs(expected);
    }

    @Test
    void shouldRejectAnonymousAccess() {
        Authentication anon = new AnonymousAuthenticationToken(
                "anon", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        assertThatThrownBy(() -> controller.rateProblem(PKG, ROW, new ReviewProblemRatingRequest("good"), anon))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("请先登录");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPropagatePermissionErrorFromService() {
        Authentication auth = stubAuthenticatedUser("bob");
        when(ratingService.rateProblem(eq(USER_ID), eq(PKG), eq(ROW), any()))
                .thenThrow(new LegacyBusinessException("permission-denied", "复习包不属于当前用户"));

        assertThatThrownBy(() -> controller.rateProblem(PKG, ROW, new ReviewProblemRatingRequest("good"), auth))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("复习包不属于当前用户");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPropagateNotFoundFromService() {
        Authentication auth = stubAuthenticatedUser("carol");
        when(ratingService.rateProblem(eq(USER_ID), eq(PKG), eq(ROW), any()))
                .thenThrow(new LegacyBusinessException("not-found", "题目不属于该复习包"));

        assertThatThrownBy(() -> controller.rateProblem(PKG, ROW, new ReviewProblemRatingRequest("good"), auth))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("题目不属于该复习包");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPropagateValidationErrorWhenProblemNotSubmitted() {
        Authentication auth = stubAuthenticatedUser("dora");
        when(ratingService.rateProblem(eq(USER_ID), eq(PKG), eq(ROW), any()))
                .thenThrow(new LegacyBusinessException("error", "请先完成本题再评分"));

        assertThatThrownBy(() -> controller.rateProblem(PKG, ROW, new ReviewProblemRatingRequest("good"), auth))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("请先完成本题再评分");
    }

    @SuppressWarnings("unchecked")
    private Authentication stubAuthenticatedUser(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(username);
        when(jdbcTemplate.query(
                eq("select id from \"user\" where lower(username) = ?"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq(username.toLowerCase())
        )).thenReturn(List.of(USER_ID));
        return auth;
    }

    private ReviewPackageResponse stubResponse () {
        return new ReviewPackageResponse(
                PKG, "logic_error", "逻辑错误", Map.of(),
                3, 1, false, List.of(), "2026-04-25T00:00:00Z",
                "review", "2026-04-26T00:00:00Z", 1.5, 4.0, 0.85
        );
    }
}
