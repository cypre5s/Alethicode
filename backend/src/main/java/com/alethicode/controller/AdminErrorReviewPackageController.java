package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.ReviewPackageStatsResponse;
import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/review-packages")
@Lazy
@PreAuthorize("hasRole('ADMIN')")
public class AdminErrorReviewPackageController {

    private final ErrorReviewPackageService reviewPackageService;

    public AdminErrorReviewPackageController(ErrorReviewPackageService reviewPackageService) {
        this.reviewPackageService = reviewPackageService;
    }

    @GetMapping("/stats")
    public ApiResponse<Object> getStats() {
        ReviewPackageStatsResponse stats = reviewPackageService.getStats();
        return ApiResponse.success(stats);
    }
}
