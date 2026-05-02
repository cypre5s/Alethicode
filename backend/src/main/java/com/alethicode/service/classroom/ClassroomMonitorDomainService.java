package com.alethicode.service.classroom;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ClassroomMonitorDomainService {

    ApiResponse<Object> monitorStats(String classroomId, Authentication authentication);

    ApiResponse<Object> monitorSnapshots(String classroomId, Authentication authentication);

    ApiResponse<Object> monitorPlayback(String classroomId, Map<String, String> params, Authentication authentication);

    ApiResponse<Object> monitorCoach(String classroomId, Map<String, String> params, Authentication authentication);

    ApiResponse<Object> monitorErrorClusters(String classroomId, Map<String, String> params, Authentication authentication);

    ApiResponse<Object> monitorInterventionCandidates(String classroomId, Map<String, String> params, Authentication authentication);
}
