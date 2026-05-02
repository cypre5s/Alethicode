package com.alethicode.dto.request;

public record ResetPasswordRequest(
        String token,
        String password,
        String captcha
) {
}
