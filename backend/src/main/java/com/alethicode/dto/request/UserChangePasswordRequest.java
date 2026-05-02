package com.alethicode.dto.request;

public record UserChangePasswordRequest(
        String oldPassword,
        String newPassword,
        String tfaCode
) {
}
