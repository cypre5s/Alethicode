package com.alethicode.dto.request;

public record UserChangeEmailRequest(
        String password,
        String newEmail,
        String tfaCode
) {
}
