package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomCoreDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class ClassroomCoreController {

    private final ClassroomCoreDomainService classroomCoreDomainService;

    public ClassroomCoreController(ClassroomCoreDomainService classroomCoreDomainService) {
        this.classroomCoreDomainService = classroomCoreDomainService;
    }

    @GetMapping({"/api/classroom", "/api/classroom/"})
    public ApiResponse<Object> classroomList(@RequestParam Map<String, String> params, Authentication authentication) {
        return classroomCoreDomainService.classroomList(params, authentication);
    }

    @PostMapping({"/api/classroom", "/api/classroom/"})
    public ApiResponse<Object> classroomCreate(@RequestBody Map<String, Object> request, Authentication authentication) {
        return classroomCoreDomainService.classroomCreate(request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}", "/api/classroom/{classroomId}/"
    })
    public ApiResponse<Object> classroomRetrieve(@PathVariable String classroomId, Authentication authentication) {
        return classroomCoreDomainService.classroomRetrieve(classroomId, authentication);
    }

    @PutMapping({
            "/api/classroom/{classroomId}", "/api/classroom/{classroomId}/"
    })
    public ApiResponse<Object> classroomUpdatePut(@PathVariable String classroomId,
                                                   @RequestBody Map<String, Object> request,
                                                   Authentication authentication) {
        return classroomCoreDomainService.classroomUpdate(classroomId, request, authentication);
    }

    @PatchMapping({
            "/api/classroom/{classroomId}", "/api/classroom/{classroomId}/"
    })
    public ApiResponse<Object> classroomUpdatePatch(@PathVariable String classroomId,
                                                     @RequestBody Map<String, Object> request,
                                                     Authentication authentication) {
        return classroomCoreDomainService.classroomUpdate(classroomId, request, authentication);
    }

    @DeleteMapping({
            "/api/classroom/{classroomId}", "/api/classroom/{classroomId}/"
    })
    public ApiResponse<Object> classroomDelete(@PathVariable String classroomId, Authentication authentication) {
        return classroomCoreDomainService.classroomDelete(classroomId, authentication);
    }

    @PostMapping({
            "/api/classroom/invitation/join", "/api/classroom/invitation/join/"
    })
    public ApiResponse<Object> invitationJoin(@RequestBody Map<String, Object> request, Authentication authentication) {
        return classroomCoreDomainService.invitationJoin(request, authentication);
    }

    @PostMapping({
            "/api/classroom/invitation/generate/{classroomId}", "/api/classroom/invitation/generate/{classroomId}/"
    })
    public ApiResponse<Object> invitationGenerate(@PathVariable String classroomId,
                                                  @RequestBody Map<String, Object> request,
                                                  Authentication authentication) {
        return classroomCoreDomainService.invitationGenerate(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/invitation/list/{classroomId}", "/api/classroom/invitation/list/{classroomId}/"
    })
    public ApiResponse<Object> invitationList(@PathVariable String classroomId,
                                              @RequestParam Map<String, String> params,
                                              Authentication authentication) {
        return classroomCoreDomainService.invitationList(classroomId, params, authentication);
    }

    @PostMapping({
            "/api/classroom/invitation/{invitationId}/deactivate", "/api/classroom/invitation/{invitationId}/deactivate/"
    })
    public ApiResponse<Object> invitationDeactivate(@PathVariable String invitationId, Authentication authentication) {
        return classroomCoreDomainService.invitationDeactivate(invitationId, authentication);
    }

    @PostMapping({
            "/api/classroom/invitation/refresh/{classroomId}", "/api/classroom/invitation/refresh/{classroomId}/"
    })
    public ApiResponse<Object> invitationRefresh(@PathVariable String classroomId,
                                                 @RequestBody Map<String, Object> request,
                                                 Authentication authentication) {
        return classroomCoreDomainService.invitationRefresh(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/problems", "/api/classroom/{classroomId}/problems/"
    })
    public ApiResponse<Object> problemList(@PathVariable String classroomId, Authentication authentication) {
        return classroomCoreDomainService.problemList(classroomId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/problems", "/api/classroom/{classroomId}/problems/"
    })
    public ApiResponse<Object> problemCreate(@PathVariable String classroomId,
                                             @RequestBody Map<String, Object> request,
                                             Authentication authentication) {
        return classroomCoreDomainService.problemCreate(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/problems/{classroomProblemId}", "/api/classroom/{classroomId}/problems/{classroomProblemId}/"
    })
    public ApiResponse<Object> problemRetrieve(@PathVariable String classroomId,
                                               @PathVariable String classroomProblemId,
                                               Authentication authentication) {
        return classroomCoreDomainService.problemRetrieve(classroomId, classroomProblemId, authentication);
    }

    @PatchMapping({
            "/api/classroom/{classroomId}/problems/{classroomProblemId}", "/api/classroom/{classroomId}/problems/{classroomProblemId}/"
    })
    public ApiResponse<Object> problemUpdate(@PathVariable String classroomId,
                                             @PathVariable String classroomProblemId,
                                             @RequestBody Map<String, Object> request,
                                             Authentication authentication) {
        return classroomCoreDomainService.problemUpdate(classroomId, classroomProblemId, request, authentication);
    }

    @DeleteMapping({
            "/api/classroom/{classroomId}/problems/{classroomProblemId}", "/api/classroom/{classroomId}/problems/{classroomProblemId}/"
    })
    public ApiResponse<Object> problemDelete(@PathVariable String classroomId,
                                             @PathVariable String classroomProblemId,
                                             Authentication authentication) {
        return classroomCoreDomainService.problemDelete(classroomId, classroomProblemId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/problems/import-objective-json", "/api/classroom/{classroomId}/problems/import-objective-json/"
    })
    public ApiResponse<Object> problemImportObjectiveJson(@PathVariable String classroomId,
                                                          @RequestBody Map<String, Object> request,
                                                          Authentication authentication) {
        return classroomCoreDomainService.problemImportObjectiveJson(classroomId, request, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/problems/export-objective-json", "/api/classroom/{classroomId}/problems/export-objective-json/"
    })
    public ApiResponse<Object> problemExportObjectiveJson(@PathVariable String classroomId, Authentication authentication) {
        return classroomCoreDomainService.problemExportObjectiveJson(classroomId, authentication);
    }
}
