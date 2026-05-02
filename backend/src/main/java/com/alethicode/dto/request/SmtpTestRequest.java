package com.alethicode.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SmtpTestRequest(
        @NotBlank @Email String email
) {
}
