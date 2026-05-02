package com.alethicode.controller.classroom;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomMemberDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class ClassroomMemberController {

    private final ClassroomMemberDomainService classroomMemberDomainService;

    public ClassroomMemberController(ClassroomMemberDomainService classroomMemberDomainService) {
        this.classroomMemberDomainService = classroomMemberDomainService;
    }

    @GetMapping({
            "/api/classroom/{classroomId}/members", "/api/classroom/{classroomId}/members/"
    })
    public ApiResponse<Object> memberList(@PathVariable String classroomId,
                                          @RequestParam Map<String, String> params,
                                          Authentication authentication) {
        return classroomMemberDomainService.memberList(classroomId, params, authentication);
    }

    @GetMapping({
            "/api/classroom/{classroomId}/members/{memberId}", "/api/classroom/{classroomId}/members/{memberId}/"
    })
    public ApiResponse<Object> memberRetrieve(@PathVariable String classroomId,
                                              @PathVariable String memberId,
                                              Authentication authentication) {
        return classroomMemberDomainService.memberRetrieve(classroomId, memberId, authentication);
    }

    @DeleteMapping({
            "/api/classroom/{classroomId}/members/{memberId}", "/api/classroom/{classroomId}/members/{memberId}/"
    })
    public ApiResponse<Object> memberDelete(@PathVariable String classroomId,
                                            @PathVariable String memberId,
                                            Authentication authentication) {
        return classroomMemberDomainService.memberDelete(classroomId, memberId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/members/{memberId}/promote", "/api/classroom/{classroomId}/members/{memberId}/promote/"
    })
    public ApiResponse<Object> memberPromote(@PathVariable String classroomId,
                                             @PathVariable String memberId,
                                             Authentication authentication) {
        return classroomMemberDomainService.memberPromote(classroomId, memberId, authentication);
    }

    @PostMapping({
            "/api/classroom/{classroomId}/members/{memberId}/demote", "/api/classroom/{classroomId}/members/{memberId}/demote/"
    })
    public ApiResponse<Object> memberDemote(@PathVariable String classroomId,
                                            @PathVariable String memberId,
                                            Authentication authentication) {
        return classroomMemberDomainService.memberDemote(classroomId, memberId, authentication);
    }
}
