package com.alethicode.service.classroom;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ClassroomSessionDomainService {

    ApiResponse<Object> sessionList(String classroomId, Authentication authentication);

    ApiResponse<Object> sessionCreate(String classroomId, Map<String, Object> request, Authentication authentication);

    ApiResponse<Object> sessionRetrieve(String classroomId, String sessionId, Authentication authentication);

    ApiResponse<Object> sessionDelete(String classroomId, String sessionId, Authentication authentication);

    ApiResponse<Object> sessionEnd(String classroomId, String sessionId, Authentication authentication);

    ApiResponse<Object> sessionTransferToken(String classroomId, String sessionId, Map<String, Object> request, Authentication authentication);
}
