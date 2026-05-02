package com.alethicode.config;

import com.alethicode.middleware.EnsureApiCsrfCookieFilter;
import com.alethicode.middleware.InternalApiKeyFilter;
import com.alethicode.middleware.RateLimitFilter;
import com.alethicode.middleware.SessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AlethicodeProperties properties;

    public SecurityConfig(AlethicodeProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("csrftoken");
        csrfTokenRepository.setHeaderName("X-CSRFToken");
        csrfTokenRepository.setCookiePath("/");
        // MED-1 (2026-05-02 渗透报告): 由独立 cookie-secure 开关控制 (默认 false)，
        // 与 force-https 解耦——HTTP 部署不能开 Secure（浏览器/curl 在 HTTP 拒收
        // Secure cookie 会导致 csrftoken 永远不回传，登录被 CSRF filter 401）。
        // HTTPS 部署后通过 env ALETHICODE_SYSTEM_COOKIE_SECURE=true 启用。
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(properties.getSystem().isCookieSecure()));
        return csrfTokenRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            EnsureApiCsrfCookieFilter ensureApiCsrfCookieFilter,
            InternalApiKeyFilter internalApiKeyFilter,
            RateLimitFilter rateLimitFilter,
            SessionAuthenticationFilter sessionAuthenticationFilter
    ) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/website", "/api/website/", "/api/languages", "/api/languages/").permitAll()
                        .requestMatchers("/api/profile", "/api/profile/").permitAll()
                        .requestMatchers("/api/csrf", "/api/csrf/", "/csrf", "/csrf/", "/api/captcha", "/api/captcha/").permitAll()
                        .requestMatchers("/api/login", "/api/login/", "/api/register", "/api/register/").permitAll()
                        .requestMatchers("/api/tfa-required", "/api/tfa-required/").permitAll()
                        .requestMatchers("/api/check-username-or-email", "/api/check-username-or-email/").permitAll()
                        .requestMatchers("/api/apply-reset-password", "/api/apply-reset-password/").permitAll()
                        .requestMatchers("/api/reset-password", "/api/reset-password/").permitAll()
                        .requestMatchers("/api/sso", "/api/sso/").permitAll()
                        .requestMatchers("/api/announcements", "/api/announcements/").permitAll()
                        .requestMatchers("/api/problems", "/api/problems/", "/api/problems/tags", "/api/problems/tags/").permitAll()
                        .requestMatchers("/api/problems/random", "/api/problems/random/").permitAll()
                        .requestMatchers("/api/language-packs/**").permitAll()
                        .requestMatchers("/api/judge-server-heartbeat", "/api/judge-server-heartbeat/").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        // MED-4 (2026-05-02 渗透报告): 未注册的 /ws/* 路径之前会落到 Spring
                        // WebSocket 框架抛 500；现在 Spring Security 先要求认证，匿名直接 401，
                        // 已认证用户再由 WebSocket handshake interceptor 二次校验 session。
                        .requestMatchers("/ws/**").authenticated()
                        .requestMatchers("/sse", "/sse/**", "/mcp/**").permitAll()
                        .requestMatchers(request -> {
                            String uri = request.getRequestURI();
                            return !uri.startsWith("/api/") && !uri.startsWith("/ws/");
                        }).permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/beta/telemetry/events", "/api/beta/telemetry/events/",
                                "/api/beta/telemetry/web-vitals", "/api/beta/telemetry/web-vitals/"
                        ).permitAll()
                        .requestMatchers("/api/beta/**").authenticated()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(new OrRequestMatcher(
                                new AntPathRequestMatcher("/api/judge-server-heartbeat", "POST"),
                                new AntPathRequestMatcher("/api/judge-server-heartbeat/", "POST"),
                                new AntPathRequestMatcher("/internal/**"),
                                new AntPathRequestMatcher("/sse/**"),
                                new AntPathRequestMatcher("/mcp/**"),
                                new AntPathRequestMatcher("/api/beta/telemetry/events", "POST"),
                                new AntPathRequestMatcher("/api/beta/telemetry/events/", "POST"),
                                new AntPathRequestMatcher("/api/beta/telemetry/web-vitals", "POST"),
                                new AntPathRequestMatcher("/api/beta/telemetry/web-vitals/", "POST")
                        )))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' ws: wss:; frame-src 'self'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // LOW-4 (2026-05-02 渗透报告): 收紧 Permissions-Policy，禁掉 OJ
                        // 业务用不上的传感器与硬件 API；防止第三方注入脚本调用敏感 API。
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), "
                                        + "usb=(), serial=(), midi=(), bluetooth=(), "
                                        + "accelerometer=(), gyroscope=(), magnetometer=(), "
                                        + "ambient-light-sensor=(), autoplay=(self), encrypted-media=()")))
                .cors(cors -> cors.configurationSource(mcpCorsConfigurationSource()))
                .exceptionHandling(ex -> ex
                        // 默认 Spring Security 对未登录的 authenticated() 请求返回 403。
                        // 公测反馈方案要求未登录返回 401，这里用 HttpStatusEntryPoint 显式覆盖；
                        // 真实「权限不足」（已登录但缺角色）仍走默认 AccessDeniedHandler → 403。
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(unauthenticatedAwareAccessDeniedHandler()))
                .addFilterAfter(ensureApiCsrfCookieFilter, CsrfFilter.class)
                .addFilterBefore(internalApiKeyFilter, CsrfFilter.class)
                .addFilterBefore(rateLimitFilter, CsrfFilter.class)
                .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * AccessDeniedHandler 在 CSRF 失败、AuthorizationFilter 拒绝匿名等场景被触发。
     * 区分两种情况：
     *   1. 当前是匿名（AnonymousAuthenticationToken）→ 返回 401，告诉前端"先登录"
     *   2. 已登录但权限不够 → 返回 403，告诉前端"角色不足"
     */
    private AccessDeniedHandler unauthenticatedAwareAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth instanceof AnonymousAuthenticationToken) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        };
    }

    private CorsConfigurationSource mcpCorsConfigurationSource() {
        CorsConfiguration mcpCors = new CorsConfiguration();
        mcpCors.addAllowedOriginPattern("*");
        mcpCors.addAllowedMethod("*");
        mcpCors.addAllowedHeader("*");
        mcpCors.setAllowCredentials(false);
        mcpCors.setMaxAge(3600L);

        CorsConfiguration defaultCors = new CorsConfiguration();
        defaultCors.applyPermitDefaultValues();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/sse/**", mcpCors);
        source.registerCorsConfiguration("/mcp/**", mcpCors);
        return source;
    }
}
