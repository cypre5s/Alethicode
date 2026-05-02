package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminTestCaseService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping
public class AdminTestCaseController {

    private final AdminTestCaseService adminTestCaseService;

    public AdminTestCaseController(AdminTestCaseService adminTestCaseService) {
        this.adminTestCaseService = adminTestCaseService;
    }

    @GetMapping({
            "/api/admin/test-cases",
            "/api/admin/test-cases/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadTestCases(
            @RequestParam(name = "problem_id", required = false) String problemId,
            Authentication authentication
    ) {
        return adminTestCaseService.downloadTestCases(problemId, authentication);
    }

    @PostMapping({
            "/api/admin/test-cases",
            "/api/admin/test-cases/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> uploadTestCases(
            @RequestParam(name = "spj", required = false) String spj,
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication authentication
    ) {
        return adminTestCaseService.uploadTestCases(spj, file, authentication);
    }

    @GetMapping({
            "/api/admin/test-cases/inline",
            "/api/admin/test-cases/inline/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> getInlineTestCases(
            @RequestParam(name = "problem_id", required = false) String problemId,
            Authentication authentication
    ) {
        return adminTestCaseService.getInlineTestCases(problemId, authentication);
    }

    @PostMapping({
            "/api/admin/test-cases/inline",
            "/api/admin/test-cases/inline/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> uploadInlineTestCases(@RequestBody Map<String, Object> request,
                                                     Authentication authentication) {
        return adminTestCaseService.uploadInlineTestCases(request, authentication);
    }
}
