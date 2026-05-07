package com.alethicode.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;

/**
 * 启动时检查历史默认 {@code root/root123456} 管理员凭据。
 *
 * <p>生产类 profile 中若仍使用泄露默认密码则拒绝启动；非生产环境只记录警告，避免影响本地开发。</p>
 */
@Configuration
public class RootPasswordValidator {

    private static final Logger log = LoggerFactory.getLogger(RootPasswordValidator.class);
    private static final String ROOT_USERNAME = "root";
    private static final String LEAKED_DEFAULT_PASSWORD = "root123456";
    /** 需要强制校验 root 密码的生产类 profile。 */
    private static final Set<String> PROD_PROFILES = Set.of("prod", "production", "release");

    @Bean
    ApplicationRunner validateRootPassword(JdbcTemplate jdbcTemplate, Environment environment) {
        return args -> {
            boolean isProdLike = false;
            for (String profile : environment.getActiveProfiles()) {
                if (PROD_PROFILES.contains(profile)) {
                    isProdLike = true;
                    break;
                }
            }

            String storedHash;
            try {
                storedHash = jdbcTemplate.queryForObject(
                        "select password_hash from \"user\" where username = ?",
                        String.class,
                        ROOT_USERNAME);
            } catch (EmptyResultDataAccessException ignored) {
                if (isProdLike) {
                    log.info("No root account found in prod; skipping default-password validation");
                }
                return;
            } catch (DataAccessException e) {
                log.warn("Unable to read root password hash for validator: {}", e.getMessage());
                return;
            }

            if (storedHash == null || storedHash.isBlank()) {
                return;
            }

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            boolean stillDefault;
            try {
                stillDefault = encoder.matches(LEAKED_DEFAULT_PASSWORD, storedHash);
            } catch (IllegalArgumentException e) {
                log.warn("Root password hash is not a valid BCrypt string; skipping validator");
                return;
            }

            if (!stillDefault) {
                return;
            }

            if (isProdLike) {
                throw new IllegalStateException(
                        "Root account still uses the leaked default password 'root123456'. " +
                                "Rotate to a strong value (e.g. via /admin/users) before running in prod.");
            }
            log.warn("Root account still uses the leaked default password 'root123456'; " +
                    "this is acceptable for local development but MUST be rotated before prod");
        };
    }
}
