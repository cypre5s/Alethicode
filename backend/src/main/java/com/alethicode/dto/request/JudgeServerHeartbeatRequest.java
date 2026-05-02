package com.alethicode.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record JudgeServerHeartbeatRequest(
        @NotBlank String hostname,
        @NotBlank String judgerVersion,
        @NotNull @Min(1) Integer cpuCore,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double memory,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double cpu,
        @NotBlank @Pattern(regexp = "heartbeat") String action,
        @NotBlank String serviceUrl
) {
}
