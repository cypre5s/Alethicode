package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomAssignmentDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class ClassroomAssignmentController {

    private final ClassroomAssignmentDomainService classroomAssignmentDomainService;

    public ClassroomAssignmentController(ClassroomAssignmentDomainService classroomAssignmentDomainService) {
        this.classroomAssignmentDomainService = classroomAssignmentDomainService;
    }

    @GetMapping({
            "/api/classroom/{classroomId}/assignments", "/api/classroom/{classroomId}/assignments/"
    })
    public ApiResponse<Object> assignmentList(@PathVariable String classroomId, Authentication authentication) {
        return classroomAssignmentDomainService.assignmentList(classroomId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/assignments", "/api/classroom/{classroomId}/assignments/"
    })
    public ApiResponse<Object> assignmentCreate(@PathVariable String classroomId,
                                                @RequestBody Map<String, Object> request,
                                                Authentication authentication) {
        return classroomAssignmentDomainService.assignmentCreate(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}", "/api/classroom/{classroomId}/assignments/{assignmentId}/"
    })
    public ApiResponse<Object> assignmentRetrieve(@PathVariable String classroomId,
                                                  @PathVariable String assignmentId,
                                                  Authentication authentication) {
        return classroomAssignmentDomainService.assignmentRetrieve(classroomId, assignmentId, authentication);
    }

    @PutMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}", "/api/classroom/{classroomId}/assignments/{assignmentId}/"
    })
    public ApiResponse<Object> assignmentUpdatePut(@PathVariable String classroomId,
                                                    @PathVariable String assignmentId,
                                                    @RequestBody Map<String, Object> request,
                                                    Authentication authentication) {
        return classroomAssignmentDomainService.assignmentUpdate(classroomId, assignmentId, request, authentication);
    }

    @PatchMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}", "/api/classroom/{classroomId}/assignments/{assignmentId}/"
    })
    public ApiResponse<Object> assignmentUpdatePatch(@PathVariable String classroomId,
                                                      @PathVariable String assignmentId,
                                                      @RequestBody Map<String, Object> request,
                                                      Authentication authentication) {
        return classroomAssignmentDomainService.assignmentUpdate(classroomId, assignmentId, request, authentication);
    }

    @DeleteMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}", "/api/classroom/{classroomId}/assignments/{assignmentId}/"
    })
    public ApiResponse<Object> assignmentDelete(@PathVariable String classroomId,
                                                @PathVariable String assignmentId,
                                                Authentication authentication) {
        return classroomAssignmentDomainService.assignmentDelete(classroomId, assignmentId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}/submit", "/api/classroom/{classroomId}/assignments/{assignmentId}/submit/"
    })
    public ApiResponse<Object> assignmentSubmit(@PathVariable String classroomId,
                                                @PathVariable String assignmentId,
                                                @RequestBody Map<String, Object> request,
                                                Authentication authentication) {
        return classroomAssignmentDomainService.assignmentSubmit(classroomId, assignmentId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}/submissions", "/api/classroom/{classroomId}/assignments/{assignmentId}/submissions/"
    })
    public ApiResponse<Object> assignmentSubmissions(@PathVariable String classroomId,
                                                     @PathVariable String assignmentId,
                                                     Authentication authentication) {
        return classroomAssignmentDomainService.assignmentSubmissions(classroomId, assignmentId, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}/stats", "/api/classroom/{classroomId}/assignments/{assignmentId}/stats/"
    })
    public ApiResponse<Object> assignmentStats(@PathVariable String classroomId,
                                               @PathVariable String assignmentId,
                                               Authentication authentication) {
        return classroomAssignmentDomainService.assignmentStats(classroomId, assignmentId, authentication);
    }

    @PutMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}/submissions/{submissionDetailId}/grade",
            "/api/classroom/{classroomId}/assignments/{assignmentId}/submissions/{submissionDetailId}/grade/"
    })
    public ApiResponse<Object> assignmentGrade(@PathVariable String classroomId,
                                               @PathVariable String assignmentId,
                                               @PathVariable String submissionDetailId,
                                               @RequestBody Map<String, Object> request,
                                               Authentication authentication) {
        return classroomAssignmentDomainService.assignmentGrade(classroomId, assignmentId, submissionDetailId, request, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/assignments/preview-smart-compose",
            "/api/classroom/{classroomId}/assignments/preview-smart-compose/"
    })
    public ApiResponse<Object> assignmentPreviewSmartCompose(@PathVariable String classroomId,
                                                              @RequestBody Map<String, Object> request,
                                                              Authentication authentication) {
        return classroomAssignmentDomainService.assignmentPreviewSmartCompose(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/assignments/{assignmentId}/problems/{classroomProblemId}/tutor-context",
            "/api/classroom/{classroomId}/assignments/{assignmentId}/problems/{classroomProblemId}/tutor-context/"
    })
    public ApiResponse<Object> assignmentProblemEnter(@PathVariable String classroomId,
                                                       @PathVariable String assignmentId,
                                                       @PathVariable String classroomProblemId,
                                                       Authentication authentication) {
        return classroomAssignmentDomainService.assignmentProblemEnter(classroomId, assignmentId, classroomProblemId, authentication);
    }
}
