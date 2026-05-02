package com.alethicode.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProblemSampleRequest(
        @NotBlank String input,
        @NotBlank String output
) {
}
