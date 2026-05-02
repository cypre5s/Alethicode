package com.alethicode.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebsiteConfigRequest(
        @NotBlank String websiteBaseUrl,
        @NotBlank String websiteName,
        @NotBlank String websiteNameShortcut,
        @NotBlank String websiteFooter,
        @NotNull Boolean allowRegister,
        @NotNull Boolean submissionListShowAll
) {
}
