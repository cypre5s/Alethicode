package com.alethicode.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
class AccountAnonymousAccessIntegrationTest extends AbstractJdbcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        long studentId = insertUser("student", "student@example.com", true);
        insertProfile(studentId, "Student");
    }

    @Test
    void anonymousProfileRequestShouldReturnWrappedEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void anonymousTfaRequiredRequestShouldReturnWrappedResult() throws Exception {
        mockMvc.perform(post("/api/tfa-required")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"student"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.result").value(true));
    }

    private long insertUser(String username, String email, boolean twoFactorAuth) {
        Long id = jdbcTemplate.queryForObject(
                """
                insert into "user"(username, email, password_hash, admin_type, problem_permission, is_disabled, two_factor_auth, create_time)
                values (?, ?, ?, 'Regular User', 'None', false, ?, now())
                returning id
                """,
                Long.class,
                username,
                email,
                passwordEncoder.encode("student-pass"),
                twoFactorAuth
        );
        return id == null ? 0L : id;
    }

    private void insertProfile(long userId, String realName) {
        jdbcTemplate.update(
                """
                insert into user_profile(user_id, acm_problems_status, oi_problems_status, real_name, role)
                values (?, cast(? as jsonb), cast(? as jsonb), ?, 'Student')
                """,
                userId,
                "{}",
                "{}",
                realName
        );
    }
}
