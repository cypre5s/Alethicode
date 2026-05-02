package com.alethicode.service.aitutor.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.impl.AITutorWorkflowAdminServiceImpl;
import com.alethicode.service.aitutor.AITutorAdminReviewDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AITutorAdminReviewDomainServiceImpl implements AITutorAdminReviewDomainService {

    private final AITutorWorkflowAdminServiceImpl aiTutorWorkflowAdminService;

    public AITutorAdminReviewDomainServiceImpl(AITutorWorkflowAdminServiceImpl aiTutorWorkflowAdminService) {
        this.aiTutorWorkflowAdminService = aiTutorWorkflowAdminService;
    }

    @Override
    public ApiResponse<Object> adminVariantReview(Map<String, String> params, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminVariantReview(params, authentication);
    }

    @Override
    public ApiResponse<Object> adminVariantApprove(String problemId, Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminVariantApprove(problemId, request, authentication);
    }

    @Override
    public ApiResponse<Object> adminVariantReject(String problemId, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminVariantReject(problemId, authentication);
    }

    @Override
    public ApiResponse<Object> adminKcList(Map<String, String> params, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminKcList(params, authentication);
    }

    @Override
    public ApiResponse<Object> adminKcDetailUpdate(String kcId, Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminKcDetailUpdate(kcId, request, authentication);
    }

    @Override
    public ApiResponse<Object> adminKcProblems(String kcId, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminKcProblems(kcId, authentication);
    }

    @Override
    public ApiResponse<Object> adminClassroomChapters(Authentication authentication) {
        return aiTutorWorkflowAdminService.adminClassroomChapters(authentication);
    }

    @Override
    public ApiResponse<Object> adminPreflightStats(Authentication authentication) {
        return aiTutorWorkflowAdminService.adminPreflightStats(authentication);
    }

    @Override
    public ApiResponse<Object> adminPreflightDiagnose(Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminPreflightDiagnose(request, authentication);
    }

    @Override
    public ApiResponse<Object> adminMcMiningPending(Authentication authentication) {
        return aiTutorWorkflowAdminService.adminMcMiningPending(authentication);
    }

    @Override
    public ApiResponse<Object> adminMcMiningApprove(Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminMcMiningApprove(request, authentication);
    }

    @Override
    public ApiResponse<Object> adminMcMiningReject(Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminMcMiningReject(request, authentication);
    }

    @Override
    public ApiResponse<Object> adminMcMiningMerge(Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowAdminService.adminMcMiningMerge(request, authentication);
    }

    @Override
    public ApiResponse<Object> adminMcMiningDiscover(Authentication authentication) {
        return aiTutorWorkflowAdminService.adminMcMiningDiscover(authentication);
    }
}
