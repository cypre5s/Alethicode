package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.eval.CareerEvalHarness;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminCareerEvalController {

    private final CareerEvalHarness careerEvalHarness;

    public AdminCareerEvalController(CareerEvalHarness careerEvalHarness) {
        this.careerEvalHarness = careerEvalHarness;
    }

    @PostMapping({"/api/admin/ai/evaluations/career", "/api/admin/ai/evaluations/career/"})
    public ApiResponse<Map<String, Object>> runCareerEvaluation(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ApiResponse.success(careerEvalHarness.evaluateBatch(limit));
    }
}
