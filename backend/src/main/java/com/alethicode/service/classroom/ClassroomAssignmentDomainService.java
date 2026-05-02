package com.alethicode.service.classroom;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ClassroomAssignmentDomainService {

    ApiResponse<Object> assignmentList(String classroomId, Authentication authentication);

    ApiResponse<Object> assignmentRetrieve(String classroomId, String assignmentId, Authentication authentication);

    ApiResponse<Object> assignmentCreate(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> assignmentUpdate(String classroomId, String assignmentId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> assignmentDelete(String classroomId, String assignmentId, Authentication authentication);

    ApiResponse<Object> assignmentSubmit(String classroomId, String assignmentId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> assignmentSubmissions(String classroomId, String assignmentId, Authentication authentication);

    ApiResponse<Object> assignmentGrade(String classroomId, String assignmentId, String submissionDetailId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> assignmentStats(String classroomId, String assignmentId, Authentication authentication);

    ApiResponse<Object> assignmentPreviewSmartCompose(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> assignmentProblemEnter(String classroomId, String assignmentId, String classroomProblemId, Authentication authentication);
}
