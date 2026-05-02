package com.alethicode.service.problem;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ProblemQueryService {

    ApiResponse<Object> getProblems(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> getProblemTags(String keyword, String languagePackIdParam, Authentication authentication);

    ApiResponse<Object> getTagProgress(String userIdParam, String languagePackIdParam, Authentication authentication);

    ApiResponse<Object> pickOne();
}
