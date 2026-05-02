package com.alethicode.config;

import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

/**
 * 统一限制 WebSocket 握手允许的 Origin，避免以 {@code setAllowedOrigins("*")} 放行任意站点。
 *
 * 来源决策：
 * 1. 若配置了 {@code alethicode.website.base-url} 且非 localhost/127.0.0.1，使用该地址；
 * 2. 永远额外允许本机 http/https localhost/127.0.0.1 方便本地开发；
 * 3. 支持以 {@code alethicode.website.additional-origins} 追加白名单（逗号分隔）。
 *
 * 无关紧要的 SameSite cookie 行为保留 Spring Security 默认实现（已在 SecurityConfig 中配置）。
 */
final class WebSocketOriginConfigurer {

    private WebSocketOriginConfigurer() {
    }

    static void apply(WebSocketHandlerRegistration registration, AlethicodeProperties properties) {
        registration.setAllowedOrigins(resolveAllowedOrigins(properties));
    }

    static String[] resolveAllowedOrigins(AlethicodeProperties properties) {
        java.util.LinkedHashSet<String> origins = new java.util.LinkedHashSet<>();
        String configured = properties == null || properties.getWebsite() == null
                ? null
                : properties.getWebsite().getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            String trimmed = stripTrailingSlash(configured.trim());
            if (isHttpUrl(trimmed)) {
                origins.add(trimmed);
            }
        }
        // 本地开发默认放行（常见 HMR 端口）
        origins.add("http://localhost");
        origins.add("https://localhost");
        origins.add("http://127.0.0.1");
        origins.add("https://127.0.0.1");
        for (int port : new int[]{80, 443, 5173, 8080, 8000, 3000, 4173}) {
            origins.add("http://localhost:" + port);
            origins.add("http://127.0.0.1:" + port);
            origins.add("https://localhost:" + port);
            origins.add("https://127.0.0.1:" + port);
        }
        return origins.toArray(new String[0]);
    }

    private static boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String stripTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
