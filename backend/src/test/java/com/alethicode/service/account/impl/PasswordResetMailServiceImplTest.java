package com.alethicode.service.account.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.alethicode.dto.response.WebsiteConfigResponse;
import com.alethicode.exception.BadRequestException;
import com.alethicode.service.account.PasswordResetMailService;
import com.alethicode.service.system.SmtpMailService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验证重置密码邮件领域服务：
 * - SMTP 完整配置 → 用 sys_options 中的真实参数调用 {@link SmtpMailService}，正文含一次性 token 链接
 * - SMTP 配置缺字段 → 与 admin {@code testSmtp} 一致 fail-fast
 * - SMTP 完全未配置（DB 无 smtp_config 行）→ 同样 fail-fast
 * - baseUrl 三层 fallback：requestBaseUrl 非空 → 用之；为空 → admin website_base_url；都空 → fail-fast
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetMailServiceImplTest {

    private static final String SMTP_OPTION_SQL =
            "select value::text from sys_options where key = ?";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SystemOptionService systemOptionService;

    @Mock
    private SmtpMailService smtpMailService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private PasswordResetMailService newService() {
        return new PasswordResetMailServiceImpl(jdbcTemplate, objectMapper, systemOptionService, smtpMailService);
    }

    @Test
    void sendsResetEmailWithTokenLinkWhenSmtpFullyConfigured() {
        doReturn("""
                {"server":"smtp.example.com","port":465,"email":"noreply@example.com","password":"secret","tls":true}
                """).when(jdbcTemplate).queryForObject(SMTP_OPTION_SQL, String.class, "smtp_config");
        doReturn(new WebsiteConfigResponse(
                "https://oj.example.com",
                "Alethicode",
                "Alethicode",
                "",
                true,
                true,
                "",
                ""
        )).when(systemOptionService).getWebsiteConfig();
        newService().sendResetEmail("alice", "alice@example.com", "tok-1234567890abcdef", null);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(smtpMailService).send(
                org.mockito.ArgumentMatchers.eq("smtp.example.com"),
                org.mockito.ArgumentMatchers.eq(465),
                org.mockito.ArgumentMatchers.eq("noreply@example.com"),
                org.mockito.ArgumentMatchers.eq("secret"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("Alethicode"),
                org.mockito.ArgumentMatchers.eq("alice@example.com"),
                org.mockito.ArgumentMatchers.eq("alice"),
                subjectCaptor.capture(),
                contentCaptor.capture()
        );
        assertThat(subjectCaptor.getValue()).contains("重置密码");
        assertThat(contentCaptor.getValue())
                .contains("alice")
                .contains("https://oj.example.com/reset-password/tok-1234567890abcdef")
                .contains("20 分钟");
    }

    @Test
    void prefersRequestBaseUrlOverAdminBaseUrlForResetLink() {
        doReturn("""
                {"server":"smtp.example.com","port":465,"email":"noreply@example.com","password":"secret","tls":true}
                """).when(jdbcTemplate).queryForObject(SMTP_OPTION_SQL, String.class, "smtp_config");
        doReturn(new WebsiteConfigResponse(
                "https://admin-configured.example.com",
                "Alethicode",
                "Alethicode",
                "",
                true,
                true,
                "",
                ""
        )).when(systemOptionService).getWebsiteConfig();

        newService().sendResetEmail("bob", "bob@example.com", "tok-bob",
                "https://request-host.example.com");

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(smtpMailService).send(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyString(), anyString(), anyString(),
                anyString(), contentCaptor.capture()
        );
        assertThat(contentCaptor.getValue())
                .contains("https://request-host.example.com/reset-password/tok-bob")
                .doesNotContain("admin-configured.example.com");
    }

    @Test
    void failsFastWhenBothRequestAndAdminBaseUrlAreEmpty() {
        doReturn("""
                {"server":"smtp.example.com","port":465,"email":"noreply@example.com","password":"secret","tls":true}
                """).when(jdbcTemplate).queryForObject(SMTP_OPTION_SQL, String.class, "smtp_config");
        doReturn(new WebsiteConfigResponse(
                "", "Alethicode", "Alethicode", "", true, true, "", ""
        )).when(systemOptionService).getWebsiteConfig();

        PasswordResetMailService service = newService();

        assertThatThrownBy(() ->
                service.sendResetEmail("alice", "alice@example.com", "tok", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please configure website base url");
        verify(smtpMailService, never()).send(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyString(), anyString(), anyString(),
                anyString(), anyString()
        );
    }

    @Test
    void failsFastWhenSmtpConfigMissingPasswordField() {
        doReturn("""
                {"server":"smtp.example.com","port":465,"email":"noreply@example.com","password":"","tls":true}
                """).when(jdbcTemplate).queryForObject(SMTP_OPTION_SQL, String.class, "smtp_config");

        PasswordResetMailService service = newService();

        assertThatThrownBy(() -> service.sendResetEmail("alice", "alice@example.com", "tok", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please setup SMTP config at first");
        verify(smtpMailService, never()).send(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void failsFastWhenSmtpConfigEntirelyMissing() {
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(SMTP_OPTION_SQL, String.class, "smtp_config");

        PasswordResetMailService service = newService();

        assertThatThrownBy(() -> service.sendResetEmail("alice", "alice@example.com", "tok", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please setup SMTP config at first");
    }
}
