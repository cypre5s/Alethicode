package com.alethicode.service.aitutor.impl;

import com.alethicode.dto.request.PreflightCheckRequest;
import com.alethicode.dto.request.TutorInferenceRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.impl.AITutorServiceImpl;
import com.alethicode.service.aitutor.AITutorSessionDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AITutorSessionDomainServiceImpl implements AITutorSessionDomainService {

    private final AITutorServiceImpl aiTutorService;

    public AITutorSessionDomainServiceImpl(AITutorServiceImpl aiTutorService) {
        this.aiTutorService = aiTutorService;
    }

    @Override
    public ApiResponse<Object> inference(TutorInferenceRequest request, Authentication authentication) {
        return aiTutorService.inference(request, authentication);
    }

    @Override
    public ApiResponse<Object> taskStatus(String taskId, Authentication authentication) {
        return aiTutorService.taskStatus(taskId, authentication);
    }

    @Override
    public ApiResponse<Object> session(String sessionId, Authentication authentication) {
        return aiTutorService.session(sessionId, authentication);
    }

    @Override
    public ApiResponse<Object> closeSession(String sessionId, Authentication authentication) {
        return aiTutorService.closeSession(sessionId, authentication);
    }

    @Override
    public ApiResponse<Object> preflightCheck(PreflightCheckRequest request, Authentication authentication) {
        return aiTutorService.preflightCheck(request, authentication);
    }
}
