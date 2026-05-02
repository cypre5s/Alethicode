package com.alethicode.dto.request;

public record UserRegisterRequest(
        String username,
        String password,
        String email,
        String captcha
) {
}
