package com.alethicode.controller;

import com.alethicode.dto.request.AiProviderConfigRequest;
import com.alethicode.dto.request.AiProviderValidationRunRequest;
import com.alethicode.dto.request.CreateSmtpConfigRequest;
import com.alethicode.dto.request.InfraSecretsRequest;
import com.alethicode.dto.request.SmtpTestRequest;
import com.alethicode.dto.request.SystemPathsConfigRequest;
import com.alethicode.dto.request.UpdateSmtpConfigRequest;
import com.alethicode.dto.request.WebsiteConfigRequest;
import com.alethicode.dto.response.AiProviderConfigResponse;
import com.alethicode.dto.response.AiProviderValidationRunResponse;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.EnvSnapshotResponse;
import com.alethicode.dto.response.InfraSecretsResponse;
import com.alethicode.dto.response.ObservabilityConfigResponse;
import com.alethicode.dto.response.SmtpConfigResponse;
import com.alethicode.dto.response.SystemPathsConfigResponse;
import com.alethicode.dto.response.WebsiteConfigResponse;
import com.alethicode.service.system.SystemOptionService;
import com.alethicode.service.ai.AiProviderValidationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping
public class AdminConfigController {

    private final SystemOptionService systemOptionService;
    private final AiProviderValidationService validationService;

    public AdminConfigController(SystemOptionService systemOptionService,
                                 AiProviderValidationService validationService) {
        this.systemOptionService = systemOptionService;
        this.validationService = validationService;
    }

    @GetMapping({
            "/api/admin/website",
            "/api/admin/website/"
    })
    public ApiResponse<WebsiteConfigResponse> websiteConfig() {
        return ApiResponse.success(systemOptionService.getWebsiteConfig());
    }

    @PostMapping({
            "/api/admin/website",
            "/api/admin/website/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> updateWebsiteConfig(@Valid @RequestBody WebsiteConfigRequest request) {
        systemOptionService.updateWebsiteConfig(request);
        return ApiResponse.success(null);
    }

    @GetMapping({
            "/api/admin/smtp",
            "/api/admin/smtp/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<SmtpConfigResponse> smtpConfig() {
        return ApiResponse.success(systemOptionService.getSmtpConfig());
    }

    @PostMapping({
            "/api/admin/smtp",
            "/api/admin/smtp/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> createSmtpConfig(@Valid @RequestBody CreateSmtpConfigRequest request) {
        systemOptionService.createSmtpConfig(request);
        return ApiResponse.success(null);
    }

    @PutMapping({
            "/api/admin/smtp",
            "/api/admin/smtp/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> updateSmtpConfig(@Valid @RequestBody UpdateSmtpConfigRequest request) {
        systemOptionService.updateSmtpConfig(request);
        return ApiResponse.success(null);
    }

    @PostMapping({
            "/api/admin/smtp-test",
            "/api/admin/smtp-test/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Object> testSmtp(
            @Valid @RequestBody SmtpTestRequest request,
            Principal principal
    ) {
        systemOptionService.testSmtp(request.email(), principal == null ? "" : principal.getName());
        return ApiResponse.success(null);
    }

    @GetMapping({
            "/api/admin/super/ai-config",
            "/api/admin/super/ai-config/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<AiProviderConfigResponse> getAiProviderConfig() {
        return ApiResponse.success(systemOptionService.getAiProviderConfig());
    }

    @PutMapping({
            "/api/admin/super/ai-config",
            "/api/admin/super/ai-config/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> updateAiProviderConfig(@RequestBody AiProviderConfigRequest request) {
        systemOptionService.updateAiProviderConfig(request);
        return ApiResponse.success(null);
    }

    @GetMapping({
            "/api/admin/super/env-snapshot",
            "/api/admin/super/env-snapshot/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<EnvSnapshotResponse> getEnvSnapshot() {
        return ApiResponse.success(systemOptionService.getEnvSnapshot());
    }

    @GetMapping({
            "/api/admin/super/system-paths",
            "/api/admin/super/system-paths/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<SystemPathsConfigResponse> getSystemPaths() {
        return ApiResponse.success(systemOptionService.getSystemPathsConfig());
    }

    @PutMapping({
            "/api/admin/super/system-paths",
            "/api/admin/super/system-paths/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> updateSystemPaths(@RequestBody SystemPathsConfigRequest request) {
        systemOptionService.updateSystemPathsConfig(request);
        return ApiResponse.success(null);
    }

    @GetMapping({
            "/api/admin/super/infra-secrets",
            "/api/admin/super/infra-secrets/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<InfraSecretsResponse> getInfraSecrets() {
        return ApiResponse.success(systemOptionService.getInfraSecrets());
    }

    @PutMapping({
            "/api/admin/super/infra-secrets",
            "/api/admin/super/infra-secrets/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> updateInfraSecrets(@RequestBody InfraSecretsRequest request) {
        systemOptionService.updateInfraSecrets(request);
        return ApiResponse.success(null);
    }

    @GetMapping({
            "/api/admin/super/observability-config",
            "/api/admin/super/observability-config/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<ObservabilityConfigResponse> getObservabilityConfig() {
        return ApiResponse.success(systemOptionService.getObservabilityConfig());
    }

    @PostMapping({
            "/api/admin/super/ai-config/validation-runs",
            "/api/admin/super/ai-config/validation-runs/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<AiProviderValidationRunResponse> createValidationRun(@RequestBody AiProviderValidationRunRequest request) {
        return ApiResponse.success(validationService.createValidationRun(request));
    }
}
