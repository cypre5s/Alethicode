package com.alethicode.service.system;

public interface SmtpMailService {

    void send(
            String server,
            Integer port,
            String email,
            String password,
            boolean tls,
            String fromName,
            String toEmail,
            String toName,
            String subject,
            String content
    );
}
