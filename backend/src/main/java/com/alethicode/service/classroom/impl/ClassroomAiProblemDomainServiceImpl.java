package com.alethicode.service.classroom.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomAiProblemDomainService;
import com.alethicode.service.classroom.ClassroomAiProblemService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ClassroomAiProblemDomainServiceImpl implements ClassroomAiProblemDomainService {

    private final ClassroomAiProblemService classroomAiProblemService;

    public ClassroomAiProblemDomainServiceImpl(ClassroomAiProblemService classroomAiProblemService) {
        this.classroomAiProblemService = classroomAiProblemService;
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemList(String classroomId, Map<String, String> params, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemList(classroomId, params, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemCreate(String classroomId, Map<String, Object> request, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemCreate(classroomId, request, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemRetrieve(String classroomId, String aiProblemId, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemRetrieve(classroomId, aiProblemId, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemUpdate(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemUpdate(classroomId, aiProblemId, request, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemDelete(String classroomId, String aiProblemId, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemDelete(classroomId, aiProblemId, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemTaskStatus(String classroomId, String taskId, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemTaskStatus(classroomId, taskId, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemPublish(String classroomId, String aiProblemId, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemPublish(classroomId, aiProblemId, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemPromote(String classroomId, String aiProblemId, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemPromote(classroomId, aiProblemId, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemValidate(String classroomId, String aiProblemId, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemValidate(classroomId, aiProblemId, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemReviewPass(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemReviewPass(classroomId, aiProblemId, request, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemReviewReject(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemReviewReject(classroomId, aiProblemId, request, authentication);
    }

    @Override
    public ApiResponse<Object> aiGeneratedProblemExportReviewedJson(String classroomId, Authentication authentication) {
        return classroomAiProblemService.aiGeneratedProblemExportReviewedJson(classroomId, authentication);
    }

    @Override
    public ApiResponse<Object> aiGenerationKcOptions(String classroomId, Authentication authentication) {
        return classroomAiProblemService.aiGenerationKcOptions(classroomId, authentication);
    }
}
