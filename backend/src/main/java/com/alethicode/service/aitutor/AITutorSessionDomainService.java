package com.alethicode.service.aitutor;

import com.alethicode.dto.request.PreflightCheckRequest;
import com.alethicode.dto.request.TutorInferenceRequest;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

public interface AITutorSessionDomainService {

    ApiResponse<Object> inference(TutorInferenceRequest request, Authentication authentication);

    ApiResponse<Object> taskStatus(String taskId, Authentication authentication);

    ApiResponse<Object> session(String sessionId, Authentication authentication);

    ApiResponse<Object> closeSession(String sessionId, Authentication authentication);

    ApiResponse<Object> preflightCheck(PreflightCheckRequest request, Authentication authentication);
}
