package com.alethicode.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateJudgeServerRequest(
        @NotNull Long id,
        @NotNull Boolean isDisabled
) {
}
