package com.alethicode.service.system.impl;

import com.alethicode.service.system.SmtpMailService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class JavaMailSmtpMailService implements SmtpMailService {

    @Override
    public void send(
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
    ) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(server);
        mailSender.setPort(port);
        mailSender.setUsername(email);
        mailSender.setPassword(password);
        boolean useSsl = tls && port != null && port == 465;
        String connTimeout = "15000";
        String readTimeout = "30000";
        String writeTimeout = "30000";
        if (useSsl) {
            mailSender.setProtocol("smtps");
            mailSender.getJavaMailProperties().put("mail.smtps.auth", "true");
            mailSender.getJavaMailProperties().put("mail.smtps.ssl.enable", "true");
            mailSender.getJavaMailProperties().put("mail.smtps.ssl.trust", server);
            mailSender.getJavaMailProperties().put("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3");
            mailSender.getJavaMailProperties().put("mail.smtps.connectiontimeout", connTimeout);
            mailSender.getJavaMailProperties().put("mail.smtps.timeout", readTimeout);
            mailSender.getJavaMailProperties().put("mail.smtps.writetimeout", writeTimeout);
        } else {
            mailSender.getJavaMailProperties().put("mail.transport.protocol", "smtp");
            mailSender.getJavaMailProperties().put("mail.smtp.auth", "true");
            mailSender.getJavaMailProperties().put("mail.smtp.starttls.enable", Boolean.toString(tls));
            if (tls) {
                mailSender.getJavaMailProperties().put("mail.smtp.starttls.required", "true");
                mailSender.getJavaMailProperties().put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                mailSender.getJavaMailProperties().put("mail.smtp.ssl.trust", server);
            }
            mailSender.getJavaMailProperties().put("mail.smtp.connectiontimeout", connTimeout);
            mailSender.getJavaMailProperties().put("mail.smtp.timeout", readTimeout);
            mailSender.getJavaMailProperties().put("mail.smtp.writetimeout", writeTimeout);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(email);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }
}
