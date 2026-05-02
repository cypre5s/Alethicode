package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomAiProblemDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class ClassroomAiProblemController {

    private final ClassroomAiProblemDomainService classroomAiProblemDomainService;

    public ClassroomAiProblemController(ClassroomAiProblemDomainService classroomAiProblemDomainService) {
        this.classroomAiProblemDomainService = classroomAiProblemDomainService;
    }

    @GetMapping({
            "/api/classroom/{classroomId}/ai/generated-problems", "/api/classroom/{classroomId}/ai/generated-problems/"
    })
    public ApiResponse<Object> aiGeneratedProblemList(@PathVariable String classroomId,
                                                      @RequestParam Map<String, String> params,
                                                      Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemList(classroomId, params, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/ai/generated-problems", "/api/classroom/{classroomId}/ai/generated-problems/"
    })
    public ApiResponse<Object> aiGeneratedProblemCreate(@PathVariable String classroomId,
                                                        @RequestBody Map<String, Object> request,
                                                        Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemCreate(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/export-reviewed-json", "/api/classroom/{classroomId}/ai/generated-problems/export-reviewed-json/"
    })
    public ApiResponse<Object> aiGeneratedProblemExportReviewedJson(@PathVariable String classroomId, Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemExportReviewedJson(classroomId, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/kc-options", "/api/classroom/{classroomId}/ai/generated-problems/kc-options/"
    })
    public ApiResponse<Object> aiGenerationKcOptions(@PathVariable String classroomId, Authentication authentication) {
        return classroomAiProblemDomainService.aiGenerationKcOptions(classroomId, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/task-status/{taskId}", "/api/classroom/{classroomId}/ai/generated-problems/task-status/{taskId}/"
    })
    public ApiResponse<Object> aiGeneratedProblemTaskStatus(@PathVariable String classroomId,
                                                            @PathVariable String taskId,
                                                            Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemTaskStatus(classroomId, taskId, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/"
    })
    public ApiResponse<Object> aiGeneratedProblemRetrieve(@PathVariable String classroomId,
                                                          @PathVariable String aiProblemId,
                                                          Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemRetrieve(classroomId, aiProblemId, authentication);
    }

    @PatchMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/"
    })
    public ApiResponse<Object> aiGeneratedProblemUpdate(@PathVariable String classroomId,
                                                        @PathVariable String aiProblemId,
                                                        @RequestBody Map<String, Object> request,
                                                        Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemUpdate(classroomId, aiProblemId, request, authentication);
    }

    @DeleteMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/"
    })
    public ApiResponse<Object> aiGeneratedProblemDelete(@PathVariable String classroomId,
                                                        @PathVariable String aiProblemId,
                                                        Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemDelete(classroomId, aiProblemId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/publish", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/publish/"
    })
    public ApiResponse<Object> aiGeneratedProblemPublish(@PathVariable String classroomId,
                                                         @PathVariable String aiProblemId,
                                                         Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemPublish(classroomId, aiProblemId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/promote", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/promote/"
    })
    public ApiResponse<Object> aiGeneratedProblemPromote(@PathVariable String classroomId,
                                                         @PathVariable String aiProblemId,
                                                         Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemPromote(classroomId, aiProblemId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/validate", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/validate/"
    })
    public ApiResponse<Object> aiGeneratedProblemValidate(@PathVariable String classroomId,
                                                          @PathVariable String aiProblemId,
                                                          Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemValidate(classroomId, aiProblemId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/review-pass", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/review-pass/"
    })
    public ApiResponse<Object> aiGeneratedProblemReviewPass(@PathVariable String classroomId,
                                                            @PathVariable String aiProblemId,
                                                            @RequestBody(required = false) Map<String, Object> request,
                                                            Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemReviewPass(classroomId, aiProblemId, request == null ? Map.of() : request, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/review-reject", "/api/classroom/{classroomId}/ai/generated-problems/{aiProblemId}/review-reject/"
    })
    public ApiResponse<Object> aiGeneratedProblemReviewReject(@PathVariable String classroomId,
                                                              @PathVariable String aiProblemId,
                                                              @RequestBody(required = false) Map<String, Object> request,
                                                              Authentication authentication) {
        return classroomAiProblemDomainService.aiGeneratedProblemReviewReject(classroomId, aiProblemId, request == null ? Map.of() : request, authentication);
    }
}
