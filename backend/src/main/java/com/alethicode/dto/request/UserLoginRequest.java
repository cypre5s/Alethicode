package com.alethicode.dto.request;

public record UserLoginRequest(
        String username,
        String password,
        String tfaCode
) {
}
