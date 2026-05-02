package com.alethicode.service.adminproblemcommand;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface AdminProblemQueryService {

    ApiResponse<Object> getAdminProblems(Map<String, String> params, Authentication authentication);
}
