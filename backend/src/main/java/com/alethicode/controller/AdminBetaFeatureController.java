package com.alethicode.controller;

import com.alethicode.config.BetaFeatureRegistry;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
public class AdminBetaFeatureController {

    private final BetaFeatureRegistry betaFeatureRegistry;

    public AdminBetaFeatureController(BetaFeatureRegistry betaFeatureRegistry) {
        this.betaFeatureRegistry = betaFeatureRegistry;
    }

    @GetMapping({"/api/admin/beta-features", "/api/admin/beta-features/"})
    public ApiResponse<Object> list() {
        return ApiResponse.success(betaFeatureRegistry.listAll());
    }

    @PutMapping({"/api/admin/beta-features", "/api/admin/beta-features/"})
    public ApiResponse<Object> toggle(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        if (key == null || key.isBlank()) {
            return ApiResponse.error("error", "key is required");
        }
        if (!betaFeatureRegistry.isKnownFeature(key)) {
            return ApiResponse.error("error", "Unknown beta feature: " + key);
        }
        Object enabledObj = request.get("enabled");
        if (enabledObj == null) {
            return ApiResponse.error("error", "enabled is required");
        }
        boolean enabled = Boolean.TRUE.equals(enabledObj) || "true".equalsIgnoreCase(String.valueOf(enabledObj));
        betaFeatureRegistry.setOverride(key, enabled);
        return ApiResponse.success(betaFeatureRegistry.listAll());
    }
}
