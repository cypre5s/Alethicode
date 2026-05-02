package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LanguagePackQaMessageRequest(
        @JsonProperty("content")
        @NotBlank(message = "content is required")
        String content
) {
}
