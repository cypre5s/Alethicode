package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.OrphanTestCaseResponse;
import com.alethicode.service.system.SystemAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class SystemAdminController {

    private final SystemAdminService systemAdminService;

    public SystemAdminController(SystemAdminService systemAdminService) {
        this.systemAdminService = systemAdminService;
    }

    @GetMapping({
            "/api/admin/prune-test-case",
            "/api/admin/prune-test-case/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<List<OrphanTestCaseResponse>> pruneTestCaseList() {
        return ApiResponse.success(systemAdminService.getOrphanTestCases());
    }

    @DeleteMapping({
            "/api/admin/prune-test-case",
            "/api/admin/prune-test-case/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> pruneTestCaseDelete(
            @RequestParam(name = "id", required = false) String testCaseId
    ) {
        if (testCaseId == null || testCaseId.isBlank()) {
            systemAdminService.deleteAllOrphanTestCases();
        } else {
            systemAdminService.deleteOrphanTestCase(testCaseId);
        }
        return ApiResponse.success(null);
    }
}
