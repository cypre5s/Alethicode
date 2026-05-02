package com.alethicode.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProblemTestCaseScoreRequest(
        @NotBlank String inputName,
        @NotBlank String outputName,
        @Min(0) Integer score
) {
}
