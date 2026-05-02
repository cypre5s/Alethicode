package com.alethicode.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Set;

/**
 * Fail-fast check for the internal service key used to protect the
 * {@code /internal/ai-tutor/*} API. The key is shared with the {@code tutor_graph}
 * Python service; if a prod deployment forgets to set {@code INTERNAL_SERVICE_KEY}
 * and silently falls back to the {@code dev-internal-key} default, anyone with
 * network access to the Java backend can read student submissions, learner state,
 * or materialize problems. This validator turns that silent failure into a hard
 * startup error on prod profiles.
 */
@Configuration
public class InternalServiceKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceKeyValidator.class);
    private static final int MIN_KEY_LENGTH = 24;
    private static final String DEV_DEFAULT = "dev-internal-key";
    /** Profiles that are considered production-like and require a strong key. */
    private static final Set<String> PROD_PROFILES = Set.of("prod", "production", "release");

    @Bean
    ApplicationRunner validateInternalServiceKey(
            @Value("${alethicode.internal.service-key:}") String key,
            @Value("${alethicode.internal.previous-service-key:}") String previousKey,
            Environment environment) {
        return args -> {
            boolean isProdLike = false;
            for (String profile : environment.getActiveProfiles()) {
                if (PROD_PROFILES.contains(profile)) {
                    isProdLike = true;
                    break;
                }
            }

            if (!isProdLike) {
                // Development / test environments: accept the default but emit a single warn
                // so operators never forget the risk. No action required for unit tests.
                if (key.isBlank() || DEV_DEFAULT.equals(key)) {
                    log.warn("alethicode.internal.service-key is using the dev default; " +
                            "this is fine for local development but MUST be rotated before prod");
                }
                return;
            }

            validateStrongKey("alethicode.internal.service-key", key, true);
            validateStrongKey("alethicode.internal.previous-service-key", previousKey, false);
            if (!previousKey.isBlank() && key.equals(previousKey)) {
                throw new IllegalStateException(
                        "alethicode.internal.previous-service-key must differ from alethicode.internal.service-key");
            }
        };
    }

    private static void validateStrongKey(String propertyName, String key, boolean required) {
        if (key == null || key.isBlank()) {
            if (required) {
                throw new IllegalStateException(propertyName + " must be set to a non-empty value in prod");
            }
            return;
        }
        if (DEV_DEFAULT.equals(key)) {
            throw new IllegalStateException(
                    propertyName + " still uses the dev default '" + DEV_DEFAULT +
                            "'. Rotate to a strong random value before running in prod");
        }
        if (key.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    propertyName + " must be at least " + MIN_KEY_LENGTH +
                            " characters in prod (got " + key.length() + ")");
        }
    }
}
