package com.alethicode.service.classroom;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ClassroomCoreDomainService {

    ApiResponse<Object> classroomList(Map<String, String> params, Authentication authentication);

    ApiResponse<Object> classroomRetrieve(String classroomId, Authentication authentication);

    ApiResponse<Object> classroomCreate(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> classroomUpdate(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> classroomDelete(String classroomId, Authentication authentication);

    ApiResponse<Object> invitationJoin(Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> invitationGenerate(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> invitationList(String classroomId, Map<String, String> params, Authentication authentication);

    ApiResponse<Object> invitationDeactivate(String invitationId, Authentication authentication);

    ApiResponse<Object> invitationRefresh(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> problemList(String classroomId, Authentication authentication);

    ApiResponse<Object> problemRetrieve(String classroomId, String classroomProblemId, Authentication authentication);

    ApiResponse<Object> problemCreate(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> problemUpdate(String classroomId, String classroomProblemId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> problemDelete(String classroomId, String classroomProblemId, Authentication authentication);

    ApiResponse<Object> problemImportObjectiveJson(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> problemExportObjectiveJson(String classroomId, Authentication authentication);
}
