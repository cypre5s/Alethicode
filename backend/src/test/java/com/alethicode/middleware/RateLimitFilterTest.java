package com.alethicode.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private FilterChain chain;

    private static final String DEFAULT_TRUSTED_PROXIES =
            "127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

    @Test
    void shouldPassThroughForNonApiPaths() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(redis, DEFAULT_TRUSTED_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturn429WhenLimitExceeded() throws Exception {
        when(redis.execute(any(DefaultRedisScript.class), any(), any()))
                .thenReturn(101L);

        RateLimitFilter filter = new RateLimitFilter(redis, DEFAULT_TRUSTED_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("rate_limit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowRequestWithinLimit() throws Exception {
        when(redis.execute(any(DefaultRedisScript.class), any(), any()))
                .thenReturn(5L);

        RateLimitFilter filter = new RateLimitFilter(redis, DEFAULT_TRUSTED_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("95");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldApplyLowerLimitForSensitivePaths() throws Exception {
        when(redis.execute(any(DefaultRedisScript.class), any(), any()))
                .thenReturn(11L);

        RateLimitFilter filter = new RateLimitFilter(redis, DEFAULT_TRUSTED_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldUseRealIpHeaderWhenComingFromTrustedProxy() throws Exception {
        // HIGH-4: nginx 上游(172.x trusted)设置的 X-Real-IP 是真实客户端 IP。
        RateLimitFilter filter = new RateLimitFilter(redis, DEFAULT_TRUSTED_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "1.2.3.4");

        java.lang.reflect.Method method = RateLimitFilter.class.getDeclaredMethod(
                "resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);
        String ip = (String) method.invoke(filter, request);

        assertThat(ip).isEqualTo("1.2.3.4");
    }

    @Test
    void shouldIgnoreXffEvenFromTrustedProxy() throws Exception {
        // HIGH-4: 即便客户端伪造 X-Forwarded-For，filter 也不解析它，
        // 仅采用 nginx 上游设置的 X-Real-IP。
        RateLimitFilter filter = new RateLimitFilter(redis, DEFAULT_TRUSTED_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "1.2.3.4");
        request.addHeader("X-Forwarded-For", "8.8.8.1, 8.8.8.2, 8.8.8.3");

        java.lang.reflect.Method method = RateLimitFilter.class.getDeclaredMethod(
                "resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);
        String ip = (String) method.invoke(filter, request);

        assertThat(ip).isEqualTo("1.2.3.4");
    }

    @Test
    void shouldNotTrustRealIpFromUntrustedSource() throws Exception {
        // HIGH-4: 攻击者直接打 backend (绕开 nginx)，自己伪造 X-Real-IP，必须忽略。
        RateLimitFilter filter = new RateLimitFilter(redis, DEFAULT_TRUSTED_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("X-Real-IP", "127.0.0.1");

        java.lang.reflect.Method method = RateLimitFilter.class.getDeclaredMethod(
                "resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);
        String ip = (String) method.invoke(filter, request);

        assertThat(ip).isEqualTo("8.8.8.8");
    }
}
