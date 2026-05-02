package com.alethicode.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSmtpConfigRequest(
        @NotBlank String server,
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotBlank @Email String email,
        String password,
        @NotNull Boolean tls
) {
}
