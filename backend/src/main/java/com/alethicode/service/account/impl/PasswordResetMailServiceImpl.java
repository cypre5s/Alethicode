package com.alethicode.service.account.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.WebsiteConfigResponse;
import com.alethicode.exception.BadRequestException;
import com.alethicode.service.account.PasswordResetMailService;
import com.alethicode.service.system.SmtpMailService;
import com.alethicode.service.system.SystemOptionService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 直接通过 {@link SmtpMailService} 发送重置密码邮件。
 *
 * <p>SMTP 配置统一从 {@code sys_options.smtp_config} 读取（与 {@link
 * com.alethicode.service.betafeedback.impl.BetaFeedbackMailNotifier} 一致），缺
 * server / port / email / password 任一字段即抛 {@link BadRequestException}。
 * 站点 base-url 走 {@link SystemOptionService#getWebsiteConfig()}（自带 admin
 * 配置 → properties 默认值的 fallback），保证生成的链接与浏览器实际访问的域名一致。</p>
 */
@Service
public class PasswordResetMailServiceImpl implements PasswordResetMailService {

    private static final String SMTP_CONFIG_KEY = "smtp_config";
    private static final String SMTP_NOT_CONFIGURED_MESSAGE = "Please setup SMTP config at first";
    private static final String EMAIL_SUBJECT = "[Alethicode] 重置密码";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SystemOptionService systemOptionService;
    private final SmtpMailService smtpMailService;

    public PasswordResetMailServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SystemOptionService systemOptionService,
            SmtpMailService smtpMailService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.systemOptionService = systemOptionService;
        this.smtpMailService = smtpMailService;
    }

    @Override
    public void sendResetEmail(String username, String email, String token) {
        SmtpConfig smtpConfig = requireSmtpConfig();
        WebsiteConfigResponse website = systemOptionService.getWebsiteConfig();
        String baseUrl = trimOrEmpty(website == null ? null : website.websiteBaseUrl());
        String fromName = trimOrEmpty(website == null ? null : website.websiteNameShortcut());

        String resetLink = baseUrl + "/reset-password/" + token;
        String content = """
                你好 %s，

                我们收到了重置 Alethicode 账号密码的请求。
                请点击下方链接，链接 20 分钟内有效：

                %s

                如果你没有发起本次请求，可以忽略本邮件，账号密码不会被修改。

                —— Alethicode 团队
                """.formatted(username, resetLink);

        smtpMailService.send(
                smtpConfig.server,
                smtpConfig.port,
                smtpConfig.email,
                smtpConfig.password,
                smtpConfig.tls,
                fromName,
                email,
                username,
                EMAIL_SUBJECT,
                content
        );
    }

    private SmtpConfig requireSmtpConfig() {
        Map<String, Object> raw = readSmtpOption();
        if (raw == null) {
            throw new BadRequestException(SMTP_NOT_CONFIGURED_MESSAGE);
        }
        String server = trimOrEmpty(asString(raw.get("server")));
        Integer port = asNullableInteger(raw.get("port"));
        String email = trimOrEmpty(asString(raw.get("email")));
        String password = trimOrEmpty(asString(raw.get("password")));
        boolean tls = asBoolean(raw.get("tls"), true);
        if (server.isEmpty() || port == null || email.isEmpty() || password.isEmpty()) {
            throw new BadRequestException(SMTP_NOT_CONFIGURED_MESSAGE);
        }
        return new SmtpConfig(server, port, email, password, tls);
    }

    @Nullable
    private Map<String, Object> readSmtpOption() {
        String rawJson;
        try {
            rawJson = jdbcTemplate.queryForObject(
                    "select value::text from sys_options where key = ?",
                    String.class,
                    SMTP_CONFIG_KEY
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
        if (rawJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private static Integer asNullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record SmtpConfig(String server, Integer port, String email, String password, boolean tls) {
    }
}
