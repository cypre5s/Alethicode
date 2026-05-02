package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.AITutorAdminReviewDomainService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
public class AdminAITutorController {

    private final AITutorAdminReviewDomainService aiTutorAdminReviewDomainService;

    public AdminAITutorController(AITutorAdminReviewDomainService aiTutorAdminReviewDomainService) {
        this.aiTutorAdminReviewDomainService = aiTutorAdminReviewDomainService;
    }

    @GetMapping({
            "/api/admin/ai/variant-review", "/api/admin/ai/variant-review/"
    })
    public ApiResponse<Object> variantReview(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminVariantReview(params, authentication);
    }

    @PostMapping({
            "/api/admin/ai/variant-review/{problemId}/approve", "/api/admin/ai/variant-review/{problemId}/approve/"
    })
    public ApiResponse<Object> variantApprove(@PathVariable String problemId,
                                              @RequestBody(required = false) Map<String, Object> request,
                                              Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminVariantApprove(problemId, request == null ? Map.of() : request, authentication);
    }

    @PostMapping({
            "/api/admin/ai/variant-review/{problemId}/reject", "/api/admin/ai/variant-review/{problemId}/reject/"
    })
    public ApiResponse<Object> variantReject(@PathVariable String problemId, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminVariantReject(problemId, authentication);
    }

    @GetMapping({
            "/api/admin/ai/kc-list", "/api/admin/ai/kc-list/"
    })
    public ApiResponse<Object> kcList(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminKcList(params, authentication);
    }

    @PutMapping({
            "/api/admin/ai/kc/{kcId}", "/api/admin/ai/kc/{kcId}/"
    })
    public ApiResponse<Object> kcDetailUpdate(@PathVariable String kcId,
                                              @RequestBody Map<String, Object> request,
                                              Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminKcDetailUpdate(kcId, request, authentication);
    }

    @GetMapping({
            "/api/admin/ai/kc/{kcId}/problems", "/api/admin/ai/kc/{kcId}/problems/"
    })
    public ApiResponse<Object> kcProblems(@PathVariable String kcId, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminKcProblems(kcId, authentication);
    }

    @GetMapping({
            "/api/admin/ai/classroom-chapters", "/api/admin/ai/classroom-chapters/"
    })
    public ApiResponse<Object> classroomChapters(Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminClassroomChapters(authentication);
    }

    @GetMapping({
            "/api/admin/ai/preflight/stats", "/api/admin/ai/preflight/stats/"
    })
    public ApiResponse<Object> preflightStats(Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminPreflightStats(authentication);
    }

    @PostMapping({
            "/api/admin/ai/preflight/diagnose", "/api/admin/ai/preflight/diagnose/"
    })
    public ApiResponse<Object> preflightDiagnose(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminPreflightDiagnose(request, authentication);
    }

    @GetMapping({
            "/api/admin/ai/mcmining/pending", "/api/admin/ai/mcmining/pending/"
    })
    public ApiResponse<Object> mcMiningPending(Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminMcMiningPending(authentication);
    }

    @PostMapping({
            "/api/admin/ai/mcmining/approve", "/api/admin/ai/mcmining/approve/"
    })
    public ApiResponse<Object> mcMiningApprove(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminMcMiningApprove(request, authentication);
    }

    @PostMapping({
            "/api/admin/ai/mcmining/reject", "/api/admin/ai/mcmining/reject/"
    })
    public ApiResponse<Object> mcMiningReject(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminMcMiningReject(request, authentication);
    }

    @PostMapping({
            "/api/admin/ai/mcmining/merge", "/api/admin/ai/mcmining/merge/"
    })
    public ApiResponse<Object> mcMiningMerge(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminMcMiningMerge(request, authentication);
    }

    @PostMapping({
            "/api/admin/ai/mcmining/discover", "/api/admin/ai/mcmining/discover/"
    })
    public ApiResponse<Object> mcMiningDiscover(Authentication authentication) {
        return aiTutorAdminReviewDomainService.adminMcMiningDiscover(authentication);
    }
}
