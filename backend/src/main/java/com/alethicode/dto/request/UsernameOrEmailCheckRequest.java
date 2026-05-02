package com.alethicode.dto.request;

public record UsernameOrEmailCheckRequest(
        String username,
        String email
) {
}
