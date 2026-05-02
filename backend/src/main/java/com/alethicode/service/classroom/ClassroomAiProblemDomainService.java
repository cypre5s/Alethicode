package com.alethicode.service.classroom;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ClassroomAiProblemDomainService {

    ApiResponse<Object> aiGeneratedProblemList(String classroomId, Map<String, String> params, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemCreate(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemRetrieve(String classroomId, String aiProblemId, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemUpdate(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemDelete(String classroomId, String aiProblemId, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemTaskStatus(String classroomId, String taskId, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemPublish(String classroomId, String aiProblemId, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemPromote(String classroomId, String aiProblemId, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemValidate(String classroomId, String aiProblemId, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemReviewPass(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemReviewReject(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> aiGeneratedProblemExportReviewedJson(String classroomId, Authentication authentication);

    ApiResponse<Object> aiGenerationKcOptions(String classroomId, Authentication authentication);
}
