package com.alethicode.dto.response;

public record WebsiteConfigResponse(
        String websiteBaseUrl,
        String websiteName,
        String websiteNameShortcut,
        String websiteFooter,
        boolean allowRegister,
        boolean submissionListShowAll,
        String betaPrivacyVersion,
        String betaWjxUrl
) {
}
