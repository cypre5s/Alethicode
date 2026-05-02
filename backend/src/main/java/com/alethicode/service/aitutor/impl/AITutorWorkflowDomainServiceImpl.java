package com.alethicode.service.aitutor.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.impl.AITutorServiceImpl;
import com.alethicode.service.aitutor.impl.AITutorWorkflowAdminServiceImpl;
import com.alethicode.service.aitutor.AITutorWorkflowDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Workflow execution methods (session CRUD, event, checkpoint, interrupt)
 * have been fully migrated to LangGraph tutor-graph service.
 * Only non-workflow convenience methods remain.
 */
@Service
public class AITutorWorkflowDomainServiceImpl implements AITutorWorkflowDomainService {

    private final AITutorServiceImpl aiTutorService;
    private final AITutorWorkflowAdminServiceImpl aiTutorWorkflowAdminService;

    public AITutorWorkflowDomainServiceImpl(AITutorServiceImpl aiTutorService,
                                            AITutorWorkflowAdminServiceImpl aiTutorWorkflowAdminService) {
        this.aiTutorService = aiTutorService;
        this.aiTutorWorkflowAdminService = aiTutorWorkflowAdminService;
    }

    @Override
    public ApiResponse<Object> ideateSkeleton(Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowAdminService.ideateSkeleton(request, authentication);
    }

    @Override
    public ApiResponse<Object> codeSnapshot(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.codeSnapshot(request, authentication);
    }

    @Override
    public ApiResponse<Object> learningEventsBatch(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.learningEventsBatch(request, authentication);
    }

    @Override
    public ApiResponse<Object> calibrationStatus(Authentication authentication) {
        return aiTutorService.calibrationStatus(authentication);
    }

    @Override
    public ApiResponse<Object> calibrationAnswer(Map<String, Object> request, Authentication authentication) {
        return aiTutorService.calibrationAnswer(request, authentication);
    }

    @Override
    public ApiResponse<Object> calibrationSkip(Authentication authentication) {
        return aiTutorService.calibrationSkip(authentication);
    }
}
