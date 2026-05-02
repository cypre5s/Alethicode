package com.alethicode.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time matcher for internal service keys.
 *
 * <p>Rolling strategy:
 * <ul>
 *   <li>Outbound clients always send {@code alethicode.internal.service-key} (current).</li>
 *   <li>Inbound internal controllers accept current or
 *       {@code alethicode.internal.previous-service-key} (previous, optional).</li>
 *   <li>Once every service has rolled to the new current key, operators clear the previous key.</li>
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
