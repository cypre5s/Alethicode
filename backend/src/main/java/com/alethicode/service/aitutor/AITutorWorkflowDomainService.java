package com.alethicode.service.aitutor;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

/**
 * Retained non-workflow methods from the original domain service.
 * All workflow execution methods (session CRUD, event, checkpoint, interrupt)
 * have been migrated to LangGraph tutor-graph service and
 * {@link com.alethicode.controller.TutorWorkflowController}.
 */
public interface AITutorWorkflowDomainService {

    ApiResponse<Object> ideateSkeleton(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> codeSnapshot(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> learningEventsBatch(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> calibrationStatus(Authentication authentication);

    ApiResponse<Object> calibrationAnswer(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> calibrationSkip(Authentication authentication);

}
