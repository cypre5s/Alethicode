package com.alethicode.controller;

import com.alethicode.dto.request.AdminProblemUpsertRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminProblemQueryService;
import com.alethicode.service.adminproblemcommand.AdminProblemExportDomainService;
import com.alethicode.service.adminproblemcommand.AdminProblemFpsDomainService;
import com.alethicode.service.adminproblemcommand.AdminProblemImportDomainService;
import com.alethicode.service.adminproblemcommand.AdminProblemMutationDomainService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class AdminProblemController {

    private final AdminProblemQueryService adminProblemQueryService;
    private final AdminProblemMutationDomainService adminProblemMutationDomainService;
    private final AdminProblemImportDomainService adminProblemImportDomainService;
    private final AdminProblemExportDomainService adminProblemExportDomainService;
    private final AdminProblemFpsDomainService adminProblemFpsDomainService;

    public AdminProblemController(
            AdminProblemQueryService adminProblemQueryService,
            AdminProblemMutationDomainService adminProblemMutationDomainService,
            AdminProblemImportDomainService adminProblemImportDomainService,
            AdminProblemExportDomainService adminProblemExportDomainService,
            AdminProblemFpsDomainService adminProblemFpsDomainService
    ) {
        this.adminProblemQueryService = adminProblemQueryService;
        this.adminProblemMutationDomainService = adminProblemMutationDomainService;
        this.adminProblemImportDomainService = adminProblemImportDomainService;
        this.adminProblemExportDomainService = adminProblemExportDomainService;
        this.adminProblemFpsDomainService = adminProblemFpsDomainService;
    }

    @GetMapping({
            "/api/admin/problems",
            "/api/admin/problems/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> adminProblems(
            @RequestParam Map<String, String> params,
            Authentication authentication
    ) {
        return adminProblemQueryService.getAdminProblems(params, authentication);
    }

    @PostMapping({
            "/api/admin/problems",
            "/api/admin/problems/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> createProblem(
            @Valid @RequestBody AdminProblemUpsertRequest request,
            Authentication authentication
    ) {
        return adminProblemMutationDomainService.createProblem(request, authentication);
    }

    @PutMapping({
            "/api/admin/problems",
            "/api/admin/problems/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> updateProblem(
            @Valid @RequestBody AdminProblemUpsertRequest request,
            Authentication authentication
    ) {
        return adminProblemMutationDomainService.updateProblem(request, authentication);
    }

    @DeleteMapping({
            "/api/admin/problems",
            "/api/admin/problems/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> deleteProblem(
            @RequestParam(name = "id", required = false) String id,
            Authentication authentication
    ) {
        return adminProblemMutationDomainService.deleteProblem(id, authentication);
    }

    @GetMapping({
            "/api/admin/export-problems",
            "/api/admin/export-problems/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> exportProblems(
            @RequestParam(name = "problem_id", required = false) List<String> problemIds,
            Authentication authentication
    ) {
        return adminProblemExportDomainService.exportProblems(problemIds, authentication);
    }

    @PostMapping({
            "/api/admin/import-problems",
            "/api/admin/import-problems/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> importProblems(
            @RequestParam(name = "auto_kc", required = false) String autoKc,
            @RequestParam(name = "language_pack_id", required = false) String languagePackId,
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication authentication
    ) {
        return adminProblemImportDomainService.importProblems(file, autoKc, languagePackId, authentication);
    }

    @PostMapping({
            "/api/admin/import-fps",
            "/api/admin/import-fps/"
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> importFps(
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication authentication
    ) {
        return adminProblemFpsDomainService.importFps(file, authentication);
    }
}
