package com.alethicode.controller;

import com.alethicode.dto.request.CreateReviewPackageRequest;
import com.alethicode.dto.request.CreateReviewPackagesRequest;
import com.alethicode.dto.request.ReviewPackageReviewRequest;
import com.alethicode.dto.request.ReviewProblemRatingRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.service.aitutor.review.FsrsSchedulerService;
import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import com.alethicode.service.aitutor.review.ReviewProblemRatingService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/review-packages")
@Lazy
public class ErrorReviewPackageController {

    private final ErrorReviewPackageService reviewPackageService;
    private final ReviewProblemRatingService reviewProblemRatingService;
    private final JdbcTemplate jdbcTemplate;

    public ErrorReviewPackageController(ErrorReviewPackageService reviewPackageService,
                                        ReviewProblemRatingService reviewProblemRatingService,
                                        JdbcTemplate jdbcTemplate) {
        this.reviewPackageService = reviewPackageService;
        this.reviewProblemRatingService = reviewProblemRatingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ApiResponse<Object> createPackage(@RequestBody CreateReviewPackageRequest request,
                                             Authentication authentication) {
        if (request.languagePackId() == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
        }
        Long userId = resolveUserId(authentication);
        ReviewPackageResponse response = reviewPackageService.createPackage(
                userId,
                request.errorTaxonomy(),
                request.languagePackId(),
                request.problemId(),
                request.trigger()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/batches")
    public ApiResponse<Object> createPackages(@RequestBody CreateReviewPackagesRequest request,
                                              Authentication authentication) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "items is required");
        }
        Long userId = resolveUserId(authentication);
        List<ReviewPackageResponse> response = reviewPackageService.createPackages(userId, request.items());
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<Object> listPackages(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        List<ReviewPackageResponse> packages = reviewPackageService.listPackages(userId);
        return ApiResponse.success(packages);
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> getPackageDetail(@PathVariable String id,
                                                Authentication authentication) {
        Long userId = resolveUserId(authentication);
        ReviewPackageResponse response = reviewPackageService.getPackageDetail(userId, id);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reviews")
    public ApiResponse<Object> reviewPackage(@PathVariable String id,
                                             @RequestBody ReviewPackageReviewRequest request,
                                             Authentication authentication) {
        Long userId = resolveUserId(authentication);
        ReviewPackageResponse response = reviewPackageService.reviewPackage(
                userId,
                id,
                FsrsSchedulerService.ReviewRating.from(request == null ? null : request.rating()),
                java.time.Instant.now()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/{packageId}/problems/{problemId}/rating")
    public ApiResponse<Object> rateProblem(@PathVariable String packageId,
                                           @PathVariable String problemId,
                                           @RequestBody ReviewProblemRatingRequest request,
                                           Authentication authentication) {
        Long userId = resolveUserId(authentication);
        ReviewPackageResponse response = reviewProblemRatingService.rateProblem(
                userId,
                packageId,
                problemId,
                request == null ? null : request.rating()
        );
        return ApiResponse.success(response);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long userId = jdbcTemplate.query(
                "select id from \"user\" where lower(username) = ?",
                (rs, rowNum) -> rs.getLong("id"),
                username.toLowerCase()
        ).stream().findFirst().orElse(null);
        if (userId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "User not found");
        }
        return userId;
    }
}
