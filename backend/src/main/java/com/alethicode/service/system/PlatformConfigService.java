package com.alethicode.service.system;

import com.alethicode.dto.response.LanguagesResponse;
import com.alethicode.dto.response.WebsiteConfigResponse;

public interface PlatformConfigService {

    WebsiteConfigResponse getWebsiteConfig();

    LanguagesResponse getLanguages();
}
