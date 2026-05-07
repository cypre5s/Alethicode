package com.alethicode.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部服务密钥的常量时间匹配器。
 *
 * <p>轮换策略：
 * <ul>
 *   <li>出站客户端始终发送 {@code alethicode.internal.service-key}。</li>
 *   <li>入站内部接口同时接受当前密钥和可选的
 *       {@code alethicode.internal.previous-service-key}。</li>
 *   <li>所有服务完成轮换后，运维清空 previous key。</li>
 * </ul>
 */
@Component
public class InternalServiceKeyMatcher {

    private final String currentKey;
    private final String previousKey;

    public InternalServiceKeyMatcher(
            @Value("${alethicode.internal.service-key:}") String currentKey,
            @Value("${alethicode.internal.previous-service-key:}") String previousKey
    ) {
        this.currentKey = currentKey == null ? "" : currentKey;
        this.previousKey = previousKey == null ? "" : previousKey;
    }

    public boolean isConfigured() {
        return !currentKey.isBlank();
    }

    public boolean matches(String candidate) {
        if (!isConfigured()) {
            return false;
        }
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (constantTimeEquals(currentKey, candidate)) {
            return true;
        }
        return !previousKey.isBlank() && constantTimeEquals(previousKey, candidate);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
