package com.alethicode.service.aitutor.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.impl.AITutorServiceImpl;
import com.alethicode.service.aitutor.AITutorAnalyticsDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AITutorAnalyticsDomainServiceImpl implements AITutorAnalyticsDomainService {

    private final AITutorServiceImpl aiTutorService;

    public AITutorAnalyticsDomainServiceImpl(AITutorServiceImpl aiTutorService) {
        this.aiTutorService = aiTutorService;
    }

    @Override
    public ApiResponse<Object> skillRadar(Map<String, String> params, Authentication authentication) {
        return aiTutorService.skillRadar(params, authentication);
    }

    @Override
    public ApiResponse<Object> skillHeatmap(Map<String, String> params, Authentication authentication) {
        return aiTutorService.skillHeatmap(params, authentication);
    }

    @Override
    public ApiResponse<Object> recommendProblems(Map<String, String> params, Authentication authentication) {
        return aiTutorService.recommendProblems(params, authentication);
    }

    @Override
    public ApiResponse<Object> errorAttribution(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.errorAttribution(request, authentication);
    }

    @Override
    public ApiResponse<Object> antiPatternAnalyze(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.antiPatternAnalyze(request, authentication);
    }

    @Override
    public ApiResponse<Object> evalFeedback(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.evalFeedback(request, authentication);
    }

    @Override
    public ApiResponse<Object> safetyFeedback(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.safetyFeedback(request, authentication);
    }

    @Override
    public ApiResponse<Object> frustrationAnalyze(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.frustrationAnalyze(request, authentication);
    }

    @Override
    public ApiResponse<Object> frustrationEvent(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.frustrationEvent(request, authentication);
    }

    @Override
    public ApiResponse<Object> frustrationAlert(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.frustrationAlert(request, authentication);
    }

    @Override
    public ApiResponse<Object> submissionRiver(String problemId, Map<String, String> params, Authentication authentication) {
        return aiTutorService.submissionRiver(problemId, params, authentication);
    }
}
