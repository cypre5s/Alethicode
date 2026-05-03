package com.alethicode.service.account.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.ApplyResetPasswordRequest;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.LegacyBusinessException;
import com.alethicode.service.account.PasswordResetMailService;
import com.alethicode.service.account.PasswordResetThrottle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证重置密码申请主路径行为：
 * <ul>
 *   <li>captcha 错 → 抛错，不发邮件、不写 token</li>
 *   <li>已登录用户调用 → 抛错</li>
 *   <li>用户存在 → 写 token + 调 PasswordResetMailService</li>
 *   <li>用户不存在 → 静默成功，不发邮件、不写 token（不泄露存在性）</li>
 *   <li>60 秒内重复同邮箱申请 → 限流抛错；mailService 仅被调一次</li>
 *   <li>SMTP 缺失（mailService 抛 BadRequest）→ 异常向上传播</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplApplyResetPasswordTest {

    private static final String USER_BY_EMAIL_SQL_FRAGMENT = "where lower(email) = ?";
    private static final String USER_BY_USERNAME_SQL_FRAGMENT = "where lower(username) = ?";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordResetMailService passwordResetMailService;

    @Mock
    private PasswordResetThrottle passwordResetThrottle;

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        AlethicodeProperties properties = new AlethicodeProperties();
        service = new AccountServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                properties,
                passwordResetMailService,
                passwordResetThrottle
        );
    }

    @Test
    void rejectsRequestWithInvalidCaptcha() {
        MockHttpServletRequest request = withCaptcha("CORRECT");

        assertThatThrownBy(() -> service.applyResetPassword(
                new ApplyResetPasswordRequest("alice@example.com", "WRONG"),
                anonymousAuth(),
                request
        ))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessage("Invalid captcha");

        verify(passwordResetThrottle, never()).tryAcquire(anyString());
        verify(passwordResetMailService, never()).sendResetEmail(anyString(), anyString(), anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), anyLong());
    }

    @Test
    void rejectsRequestFromAuthenticatedUser() {
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                "alice", null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        stubFindUserByUsername("alice", aliceRow());

        assertThatThrownBy(() -> service.applyResetPassword(
                new ApplyResetPasswordRequest("alice@example.com", "anything"),
                authenticated,
                withCaptcha("anything")
        ))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("already logged in");

        verify(passwordResetThrottle, never()).tryAcquire(anyString());
        verify(passwordResetMailService, never()).sendResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void writesTokenAndSendsMailWhenUserExists() {
        when(passwordResetThrottle.tryAcquire("alice@example.com")).thenReturn(true);
        stubFindUserByEmail("alice@example.com", aliceRow());

        service.applyResetPassword(
                new ApplyResetPasswordRequest("Alice@Example.com", "captcha"),
                anonymousAuth(),
                withCaptcha("captcha")
        );

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Timestamp> expireCaptor = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbcTemplate).update(
                eq("update \"user\" set reset_password_token = ?, reset_password_token_expire_time = ? where id = ?"),
                tokenCaptor.capture(),
                expireCaptor.capture(),
                eq(42L)
        );
        String token = tokenCaptor.getValue();
        assertThat(token).isNotBlank().hasSize(32);

        Instant expectedExpire = Instant.now().plusSeconds(20 * 60);
        long deltaSeconds = Math.abs(expireCaptor.getValue().toInstant().getEpochSecond()
                - expectedExpire.getEpochSecond());
        assertThat(deltaSeconds).isLessThan(5);

        verify(passwordResetMailService).sendResetEmail("alice", "alice@example.com", token);
    }

    @Test
    void returnsSuccessSilentlyWhenUserDoesNotExist() {
        when(passwordResetThrottle.tryAcquire("ghost@example.com")).thenReturn(true);
        stubFindUserByEmail("ghost@example.com", null);

        service.applyResetPassword(
                new ApplyResetPasswordRequest("ghost@example.com", "captcha"),
                anonymousAuth(),
                withCaptcha("captcha")
        );

        verify(passwordResetMailService, never()).sendResetEmail(anyString(), anyString(), anyString());
        verify(jdbcTemplate, never()).update(
                eq("update \"user\" set reset_password_token = ?, reset_password_token_expire_time = ? where id = ?"),
                any(), any(), anyLong()
        );
    }

    @Test
    void returnsSuccessSilentlyWhenUserIsDisabled() {
        when(passwordResetThrottle.tryAcquire("banned@example.com")).thenReturn(true);
        stubFindUserByEmail("banned@example.com", new AccountServiceImpl.UserRow(
                42L, "banned", "banned@example.com",
                "Regular User", "None", true,
                "$2a$10$hashhashhash", false, "", false, "", null
        ));

        service.applyResetPassword(
                new ApplyResetPasswordRequest("banned@example.com", "captcha"),
                anonymousAuth(),
                withCaptcha("captcha")
        );

        verify(passwordResetMailService, never()).sendResetEmail(anyString(), anyString(), anyString());
        verify(jdbcTemplate, never()).update(
                eq("update \"user\" set reset_password_token = ?, reset_password_token_expire_time = ? where id = ?"),
                any(), any(), anyLong()
        );
    }

    @Test
    void rejectsSecondRequestWithinThrottleWindow() {
        when(passwordResetThrottle.tryAcquire("alice@example.com"))
                .thenReturn(true)
                .thenReturn(false);
        stubFindUserByEmail("alice@example.com", aliceRow());

        service.applyResetPassword(
                new ApplyResetPasswordRequest("alice@example.com", "captcha"),
                anonymousAuth(),
                withCaptcha("captcha")
        );

        assertThatThrownBy(() -> service.applyResetPassword(
                new ApplyResetPasswordRequest("alice@example.com", "captcha"),
                anonymousAuth(),
                withCaptcha("captcha")
        ))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("already sent recently");

        verify(passwordResetMailService, times(1))
                .sendResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void propagatesBadRequestWhenSmtpNotConfigured() {
        when(passwordResetThrottle.tryAcquire("alice@example.com")).thenReturn(true);
        stubFindUserByEmail("alice@example.com", aliceRow());
        org.mockito.Mockito.doThrow(new BadRequestException("Please setup SMTP config at first"))
                .when(passwordResetMailService).sendResetEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.applyResetPassword(
                new ApplyResetPasswordRequest("alice@example.com", "captcha"),
                anonymousAuth(),
                withCaptcha("captcha")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please setup SMTP config at first");
    }

    private MockHttpServletRequest withCaptcha(String captcha) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("CAPTCHA_CODE", captcha);
        return request;
    }

    private Authentication anonymousAuth() {
        return null;
    }

    @SuppressWarnings("unchecked")
    private void stubFindUserByEmail(String email, AccountServiceImpl.UserRow row) {
        doReturn(row).when(jdbcTemplate).queryForObject(
                org.mockito.ArgumentMatchers.contains(USER_BY_EMAIL_SQL_FRAGMENT),
                any(RowMapper.class),
                eq(email)
        );
    }

    @SuppressWarnings("unchecked")
    private void stubFindUserByUsername(String username, AccountServiceImpl.UserRow row) {
        doReturn(row).when(jdbcTemplate).queryForObject(
                org.mockito.ArgumentMatchers.contains(USER_BY_USERNAME_SQL_FRAGMENT),
                any(RowMapper.class),
                eq(username)
        );
    }

    private AccountServiceImpl.UserRow aliceRow() {
        return new AccountServiceImpl.UserRow(
                42L, "alice", "alice@example.com",
                "Regular User", "None", false,
                "$2a$10$hashhashhash", false, "", false, "", null
        );
    }
}
