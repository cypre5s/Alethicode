package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.LanguagesResponse;
import com.alethicode.dto.response.WebsiteConfigResponse;
import com.alethicode.service.system.PlatformConfigService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;

    public PlatformConfigController(PlatformConfigService platformConfigService) {
        this.platformConfigService = platformConfigService;
    }

    @GetMapping({"/api/website", "/api/website/"})
    public ApiResponse<WebsiteConfigResponse> website() {
        return ApiResponse.success(platformConfigService.getWebsiteConfig());
    }

    @GetMapping({"/api/languages", "/api/languages/"})
    public ApiResponse<LanguagesResponse> languages() {
        return ApiResponse.success(platformConfigService.getLanguages());
    }

    @GetMapping({"/api/csrf", "/api/csrf/", "/csrf", "/csrf/"})
    public ApiResponse<Void> csrf(CsrfToken csrfToken) {
        return ApiResponse.success(null);
    }
}
