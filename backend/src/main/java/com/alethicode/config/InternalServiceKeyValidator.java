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
 * 启动时校验保护内部 AI Tutor 接口的服务密钥。
 *
 * 生产类 profile 中若缺失密钥或仍使用开发默认值，直接拒绝启动，避免内部接口暴露学生提交、
 * 学情状态和题目物化能力。
 */
@Configuration
public class InternalServiceKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceKeyValidator.class);
    private static final int MIN_KEY_LENGTH = 24;
    private static final String DEV_DEFAULT = "dev-internal-key";
    /** 需要强制使用强密钥的生产类 profile。 */
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
                // 非生产环境允许开发默认值，但保留启动警告。
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
