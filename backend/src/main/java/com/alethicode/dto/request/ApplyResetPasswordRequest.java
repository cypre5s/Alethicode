package com.alethicode.dto.request;

public record ApplyResetPasswordRequest(
        String email,
        String captcha
) {
}
