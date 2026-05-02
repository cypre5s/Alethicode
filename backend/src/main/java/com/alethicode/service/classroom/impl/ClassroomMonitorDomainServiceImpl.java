package com.alethicode.service.classroom.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomMonitorDomainService;
import com.alethicode.service.monitor.ClassroomMonitorService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ClassroomMonitorDomainServiceImpl implements ClassroomMonitorDomainService {

    private final ClassroomMonitorService classroomMonitorService;

    public ClassroomMonitorDomainServiceImpl(ClassroomMonitorService classroomMonitorService) {
        this.classroomMonitorService = classroomMonitorService;
    }

    @Override
    public ApiResponse<Object> monitorStats(String classroomId, Authentication authentication) {
        return classroomMonitorService.monitorStats(classroomId, authentication);
    }

    @Override
    public ApiResponse<Object> monitorSnapshots(String classroomId, Authentication authentication) {
        return classroomMonitorService.monitorSnapshots(classroomId, authentication);
    }

    @Override
    public ApiResponse<Object> monitorPlayback(String classroomId, Map<String, String> params, Authentication authentication) {
        return classroomMonitorService.monitorPlayback(classroomId, params, authentication);
    }

    @Override
    public ApiResponse<Object> monitorCoach(String classroomId, Map<String, String> params, Authentication authentication) {
        return classroomMonitorService.monitorCoach(classroomId, params, authentication);
    }

    @Override
    public ApiResponse<Object> monitorErrorClusters(String classroomId, Map<String, String> params, Authentication authentication) {
        return classroomMonitorService.monitorErrorClusters(classroomId, params, authentication);
    }

    @Override
    public ApiResponse<Object> monitorInterventionCandidates(String classroomId, Map<String, String> params, Authentication authentication) {
        return classroomMonitorService.monitorInterventionCandidates(classroomId, params, authentication);
    }
}
