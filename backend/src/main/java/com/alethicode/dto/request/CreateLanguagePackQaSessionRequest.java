package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record CreateLanguagePackQaSessionRequest(
        @JsonProperty("language_pack_id")
        @NotNull(message = "language_pack_id is required")
        Long languagePackId
) {
}
