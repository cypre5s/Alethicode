package com.alethicode.service.account.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.middleware.SessionAuthenticationFilter;
import com.alethicode.service.account.PasswordResetMailService;
import com.alethicode.service.account.PasswordResetThrottle;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * NEW-1 (2026-05-02 渗透报告 v2): 验证 logout 真正切断会话——
 * 单 session.invalidate() 旧 cookie 仍能命中 /api/profile，本次修复要求：
 *   (1) SecurityContextHolder 在本请求结束前已清空；
 *   (2) 响应里 Set-Cookie SESSION/csrftoken Max-Age=0，让浏览器立即丢弃。
 */
class AccountServiceImplLogoutTest {

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        AlethicodeProperties properties = new AlethicodeProperties();
        service = new AccountServiceImpl(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                properties,
                mock(PasswordResetMailService.class),
                mock(PasswordResetThrottle.class)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutClearsSecurityContextAndExpiresCookies() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "alice", null, AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionAuthenticationFilter.AUTH_USERNAME_KEY, "alice");
        session.setAttribute(SessionAuthenticationFilter.AUTH_USER_ID_KEY, 42L);
        session.setAttribute(SessionAuthenticationFilter.AUTH_ROLES_KEY,
                Arrays.asList("ROLE_USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<Object> result = service.logout(request, response);

        assertThat(result.error()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        Cookie sessionCookie = response.getCookie("SESSION");
        assertThat(sessionCookie).as("SESSION cookie 必须设过期").isNotNull();
        assertThat(sessionCookie.getMaxAge()).isZero();
        assertThat(sessionCookie.getValue()).isEmpty();
        assertThat(sessionCookie.getPath()).isEqualTo("/");
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getSecure()).isFalse();

        Cookie csrfCookie = response.getCookie("csrftoken");
        assertThat(csrfCookie).as("csrftoken cookie 必须设过期").isNotNull();
        assertThat(csrfCookie.getMaxAge()).isZero();
        assertThat(csrfCookie.getValue()).isEmpty();
        assertThat(csrfCookie.getPath()).isEqualTo("/");
        assertThat(csrfCookie.isHttpOnly()).isFalse();
    }

    @Test
    void logoutWithoutExistingSessionStillExpiresCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<Object> result = service.logout(request, response);

        assertThat(result.error()).isNull();
        assertThat(response.getCookie("SESSION")).isNotNull();
        assertThat(response.getCookie("SESSION").getMaxAge()).isZero();
        assertThat(response.getCookie("csrftoken")).isNotNull();
        assertThat(response.getCookie("csrftoken").getMaxAge()).isZero();
    }

    @Test
    void logoutHonorsCookieSecurePropertyForHttpsDeploy() {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getSystem().setCookieSecure(true);
        AccountServiceImpl httpsService = new AccountServiceImpl(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                properties,
                mock(PasswordResetMailService.class),
                mock(PasswordResetThrottle.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        httpsService.logout(request, response);

        assertThat(response.getCookie("SESSION").getSecure()).isTrue();
        assertThat(response.getCookie("csrftoken").getSecure()).isTrue();
    }
}
