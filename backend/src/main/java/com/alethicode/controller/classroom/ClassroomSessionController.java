package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomSessionDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class ClassroomSessionController {

    private final ClassroomSessionDomainService classroomSessionDomainService;

    public ClassroomSessionController(ClassroomSessionDomainService classroomSessionDomainService) {
        this.classroomSessionDomainService = classroomSessionDomainService;
    }

    @GetMapping({
            "/api/classroom/{classroomId}/sessions", "/api/classroom/{classroomId}/sessions/"
    })
    public ApiResponse<Object> sessionList(@PathVariable String classroomId, Authentication authentication) {
        return classroomSessionDomainService.sessionList(classroomId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/sessions", "/api/classroom/{classroomId}/sessions/"
    })
    public ApiResponse<Object> sessionCreate(@PathVariable String classroomId,
                                             @RequestBody Map<String, Object> request,
                                             Authentication authentication) {
        return classroomSessionDomainService.sessionCreate(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/sessions/{sessionId}", "/api/classroom/{classroomId}/sessions/{sessionId}/"
    })
    public ApiResponse<Object> sessionRetrieve(@PathVariable String classroomId,
                                               @PathVariable String sessionId,
                                               Authentication authentication) {
        return classroomSessionDomainService.sessionRetrieve(classroomId, sessionId, authentication);
    }

    @DeleteMapping({
            "/api/classroom/{classroomId}/sessions/{sessionId}", "/api/classroom/{classroomId}/sessions/{sessionId}/"
    })
    public ApiResponse<Object> sessionDelete(@PathVariable String classroomId,
                                             @PathVariable String sessionId,
                                             Authentication authentication) {
        return classroomSessionDomainService.sessionDelete(classroomId, sessionId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/sessions/{sessionId}", "/api/classroom/{classroomId}/sessions/{sessionId}/"
    })
    public ApiResponse<Object> sessionDeleteByPost(@PathVariable String classroomId,
                                                   @PathVariable String sessionId,
                                                   Authentication authentication) {
        return classroomSessionDomainService.sessionDelete(classroomId, sessionId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/sessions/{sessionId}/end", "/api/classroom/{classroomId}/sessions/{sessionId}/end/"
    })
    public ApiResponse<Object> sessionEnd(@PathVariable String classroomId,
                                          @PathVariable String sessionId,
                                          Authentication authentication) {
        return classroomSessionDomainService.sessionEnd(classroomId, sessionId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/sessions/{sessionId}/transfer-token", "/api/classroom/{classroomId}/sessions/{sessionId}/transfer-token/"
    })
    public ApiResponse<Object> sessionTransferToken(@PathVariable String classroomId,
                                                    @PathVariable String sessionId,
                                                    @RequestBody Map<String, Object> request,
                                                    Authentication authentication) {
        return classroomSessionDomainService.sessionTransferToken(classroomId, sessionId, request, authentication);
    }
}
