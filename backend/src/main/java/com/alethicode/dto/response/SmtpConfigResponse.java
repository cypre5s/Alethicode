package com.alethicode.dto.response;

public record SmtpConfigResponse(
        String server,
        Integer port,
        String email,
        boolean tls
) {
}
