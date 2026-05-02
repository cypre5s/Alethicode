package com.alethicode.service.aitutor;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface AITutorAnalyticsDomainService {

    ApiResponse<Object> skillRadar(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> skillHeatmap(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> recommendProblems(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> errorAttribution(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> antiPatternAnalyze(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> evalFeedback(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> safetyFeedback(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> frustrationAnalyze(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> frustrationEvent(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> frustrationAlert(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> submissionRiver(String problemId, Map<String, String> params, Authentication authentication);
}
