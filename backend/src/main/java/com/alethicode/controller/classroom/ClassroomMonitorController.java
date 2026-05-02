package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomMonitorDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class ClassroomMonitorController {

    private final ClassroomMonitorDomainService classroomMonitorDomainService;

    public ClassroomMonitorController(ClassroomMonitorDomainService classroomMonitorDomainService) {
        this.classroomMonitorDomainService = classroomMonitorDomainService;
    }

    @GetMapping({
            "/api/classroom/{classroomId}/monitor/stats", "/api/classroom/{classroomId}/monitor/stats/"
    })
    public ApiResponse<Object> monitorStats(@PathVariable String classroomId, Authentication authentication) {
        return classroomMonitorDomainService.monitorStats(classroomId, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/monitor/snapshots", "/api/classroom/{classroomId}/monitor/snapshots/"
    })
    public ApiResponse<Object> monitorSnapshots(@PathVariable String classroomId, Authentication authentication) {
        return classroomMonitorDomainService.monitorSnapshots(classroomId, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/monitor/playback", "/api/classroom/{classroomId}/monitor/playback/"
    })
    public ApiResponse<Object> monitorPlayback(@PathVariable String classroomId,
                                               @RequestParam Map<String, String> params,
                                               Authentication authentication) {
        return classroomMonitorDomainService.monitorPlayback(classroomId, params, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/monitor/coach", "/api/classroom/{classroomId}/monitor/coach/"
    })
    public ApiResponse<Object> monitorCoach(@PathVariable String classroomId,
                                            @RequestParam Map<String, String> params,
                                            Authentication authentication) {
        return classroomMonitorDomainService.monitorCoach(classroomId, params, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/monitor/error-clusters", "/api/classroom/{classroomId}/monitor/error-clusters/"
    })
    public ApiResponse<Object> monitorErrorClusters(@PathVariable String classroomId,
                                                    @RequestParam Map<String, String> params,
                                                    Authentication authentication) {
        return classroomMonitorDomainService.monitorErrorClusters(classroomId, params, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/monitor/intervention-candidates", "/api/classroom/{classroomId}/monitor/intervention-candidates/"
    })
    public ApiResponse<Object> monitorInterventionCandidates(@PathVariable String classroomId,
                                                             @RequestParam Map<String, String> params,
                                                             Authentication authentication) {
        return classroomMonitorDomainService.monitorInterventionCandidates(classroomId, params, authentication);
    }
}
