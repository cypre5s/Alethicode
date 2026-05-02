package com.alethicode.service.system.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.LanguagesResponse;
import com.alethicode.dto.response.WebsiteConfigResponse;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.system.SystemOptionService;
import org.springframework.stereotype.Service;

@Service
public class PlatformConfigServiceImpl implements PlatformConfigService {

    private final SystemOptionService systemOptionService;

    public PlatformConfigServiceImpl(SystemOptionService systemOptionService) {
        this.systemOptionService = systemOptionService;
    }

    @Override
    public WebsiteConfigResponse getWebsiteConfig() {
        return systemOptionService.getWebsiteConfig();
    }

    @Override
    public LanguagesResponse getLanguages() {
        return systemOptionService.getLanguages();
    }
}
