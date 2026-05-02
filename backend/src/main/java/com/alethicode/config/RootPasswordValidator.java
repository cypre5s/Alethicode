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
 * Fail-fast check for the historical {@code root/root123456} super-user credential.
 * The bootstrap fixtures used to ship a default {@code root} account whose password
 * was leaked in {@code AGENTS.md}; if a prod deployment never rotates it, anyone with
 * network access can take over the platform with admin rights. This validator turns
 * that silent failure into a hard startup error on prod profiles.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>prod-like profiles: refuse to start when the {@code root} user still matches
 *       the well-known default. Missing {@code root} (custom deployment) is accepted.</li>
 *   <li>non-prod profiles: emit a single WARN so developers never lose track of the
 *       risk, but never block startup.</li>
 * </ul>
 */
@Configuration
public class RootPasswordValidator {

    private static final Logger log = LoggerFactory.getLogger(RootPasswordValidator.class);
    private static final String ROOT_USERNAME = "root";
    private static final String LEAKED_DEFAULT_PASSWORD = "root123456";
    /** Profiles that are considered production-like and require a strong root password. */
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
