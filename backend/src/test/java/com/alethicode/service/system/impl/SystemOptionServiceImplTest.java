package com.alethicode.service.system.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.CreateSmtpConfigRequest;
import com.alethicode.dto.request.UpdateSmtpConfigRequest;
import com.alethicode.dto.request.WebsiteConfigRequest;
import com.alethicode.exception.BadRequestException;
import com.alethicode.service.system.SmtpMailService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemOptionServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SmtpMailService smtpMailService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void websiteAndLanguagesShouldReadPersistedOptions() {
        AlethicodeProperties properties = new AlethicodeProperties();
        doReturn("""
                {"privacy_notice_version":"2026-04-28-v1","wjx_url":"https://example.com/form"}
                """).when(jdbcTemplate).queryForObject(
                "select value::text from sys_options where key = ?",
                String.class,
                "beta_feedback_config"
        );
        doReturn("""
                {"website_base_url":"http://127.0.0.1","website_name":"Alethicode","website_name_shortcut":"Alethicode","website_footer":"<a>footer</a>","allow_register":true,"submission_list_show_all":false}
                """).when(jdbcTemplate).queryForObject(
                "select value::text from sys_options where key = ?",
                String.class,
                "website_config"
        );
        doReturn("""
                {"languages":["Python3","Java"],"spj_languages":["Java"]}
                """).when(jdbcTemplate).queryForObject(
                "select value::text from sys_options where key = ?",
                String.class,
                "languages"
        );

        SystemOptionService service = new SystemOptionServiceImpl(jdbcTemplate, objectMapper, properties, smtpMailService);

        var website = service.getWebsiteConfig();
        var languages = service.getLanguages();

        assertThat(website.websiteName()).isEqualTo("Alethicode");
        assertThat(website.submissionListShowAll()).isFalse();
        assertThat(website.betaPrivacyVersion()).isEqualTo("2026-04-28-v1");
        assertThat(website.betaWjxUrl()).isEqualTo("https://example.com/form");
        assertThat(languages.languages()).containsExactly("Python3", "Java");
        assertThat(languages.spjLanguages()).containsExactly("Java");
    }

    @Test
    void smtpLifecycleShouldPersistAndReusePassword() {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getWebsite().setNameShortcut("Alethicode");
        SystemOptionServiceImpl service = new SystemOptionServiceImpl(
                jdbcTemplate,
                objectMapper,
                properties,
                smtpMailService
        );

        service.createSmtpConfig(new CreateSmtpConfigRequest(
                "smtp.example.com",
                25,
                "admin@example.com",
                "secret",
                true
        ));

        ArgumentCaptor<String> createPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                eq("insert into sys_options(key, value, updated_at) values (?, cast(? as jsonb), now()) on conflict (key) do update set value = excluded.value, updated_at = now()"),
                eq("smtp_config"),
                createPayloadCaptor.capture()
        );
        assertThat(createPayloadCaptor.getValue()).contains("\"password\":\"secret\"");

        doReturn("""
                {"server":"smtp.example.com","port":25,"email":"admin@example.com","password":"secret","tls":true}
                """).when(jdbcTemplate).queryForObject(
                "select value::text from sys_options where key = ?",
                String.class,
                "smtp_config"
        );

        service.updateSmtpConfig(new UpdateSmtpConfigRequest(
                "smtp.example.com",
                587,
                "admin@example.com",
                null,
                true
        ));

        ArgumentCaptor<String> updatePayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(
                eq("insert into sys_options(key, value, updated_at) values (?, cast(? as jsonb), now()) on conflict (key) do update set value = excluded.value, updated_at = now()"),
                eq("smtp_config"),
                updatePayloadCaptor.capture()
        );
        assertThat(updatePayloadCaptor.getAllValues().get(1)).contains("\"password\":\"secret\"");

        doReturn("""
                {"server":"smtp.example.com","port":587,"email":"admin@example.com","password":"secret","tls":true}
                """).when(jdbcTemplate).queryForObject(
                "select value::text from sys_options where key = ?",
                String.class,
                "smtp_config"
        );

        doNothing().when(smtpMailService).send(
                "smtp.example.com",
                587,
                "admin@example.com",
                "secret",
                true,
                "Alethicode",
                "student@example.com",
                "root",
                "You have successfully configured SMTP",
                "You have successfully configured SMTP"
        );

        service.testSmtp("student@example.com", "root");

        verify(smtpMailService).send(
                "smtp.example.com",
                587,
                "admin@example.com",
                "secret",
                true,
                "Alethicode",
                "student@example.com",
                "root",
                "You have successfully configured SMTP",
                "You have successfully configured SMTP"
        );
    }

    @Test
    void smtpTestShouldFailFastWhenConfigMissing() {
        AlethicodeProperties properties = new AlethicodeProperties();
        SystemOptionServiceImpl service = new SystemOptionServiceImpl(
                jdbcTemplate,
                objectMapper,
                properties,
                smtpMailService
        );

        assertThatThrownBy(() -> service.testSmtp("student@example.com", "root"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please setup SMTP config at first");
    }

    @Test
    void getSmtpConfigShouldTreatIncompleteStoredConfigAsMissing() {
        AlethicodeProperties properties = new AlethicodeProperties();
        doReturn("""
                {"server":"smtp.example.com","port":null,"email":"admin@example.com","password":"secret","tls":true}
                """).when(jdbcTemplate).queryForObject(
                "select value::text from sys_options where key = ?",
                String.class,
                "smtp_config"
        );

        SystemOptionServiceImpl service = new SystemOptionServiceImpl(
                jdbcTemplate,
                objectMapper,
                properties,
                smtpMailService
        );

        assertThat(service.getSmtpConfig()).isNull();
    }

    @Test
    void websiteUpdateShouldPersistSanitizedFooter() {
        AlethicodeProperties properties = new AlethicodeProperties();
        SystemOptionServiceImpl service = new SystemOptionServiceImpl(
                jdbcTemplate,
                objectMapper,
                properties,
                smtpMailService
        );

        service.updateWebsiteConfig(new WebsiteConfigRequest(
                "http://127.0.0.1",
                "Alethicode",
                "Alethicode",
                "<script>alert(1)</script><a href=\"https://example.com\">footer</a>",
                true,
                false
        ));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                eq("insert into sys_options(key, value, updated_at) values (?, cast(? as jsonb), now()) on conflict (key) do update set value = excluded.value, updated_at = now()"),
                eq("website_config"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).doesNotContain("<script>");
        assertThat(payloadCaptor.getValue()).contains("https://example.com");
    }
}
