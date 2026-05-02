package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LanguagePackQaFeedbackRequest(
        @JsonProperty("feedback_label")
        @NotBlank(message = "feedback_label is required")
        String feedbackLabel,
        @JsonProperty("comment")
        String comment
) {
}
