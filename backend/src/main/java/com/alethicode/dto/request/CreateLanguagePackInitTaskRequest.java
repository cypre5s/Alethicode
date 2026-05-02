package com.alethicode.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateLanguagePackInitTaskRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*[a-z0-9]$") String slug,
        @NotBlank String primaryLanguage,
        Boolean enableObjectiveQuestions
) {
}
