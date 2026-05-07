package com.alethicode.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
class AccountAnnouncementAiIntegrationTest extends AbstractJdbcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JudgeServerService judgeServerService;

    @MockBean
    private SystemAdminService systemAdminService;

    @MockBean
    private ReleaseNotesService releaseNotesService;

    @MockBean
    private PlatformConfigService platformConfigService;

    @MockBean
    private SystemOptionService systemOptionService;

    @MockBean
    private AdminUploadService adminUploadService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        long rootId = insertUser("root", "root@example.com", "Admin", false, "root-pass");
        long studentId = insertUser("student", "student@example.com", "Regular User", false, "student-pass");

        insertProfile(rootId, "Root");
        insertProfile(studentId, "Student");

        jdbcTemplate.update(
                "insert into problem(id, _id, title, visible, is_public, difficulty, submission_number, accepted_number) values (1001, 'P1001', 'A+B', true, true, 'Low', 0, 0)"
        );
        jdbcTemplate.update(
                "insert into submission(id, problem_id, user_id, username, result, language, code, create_time) values ('sub-wrong', 1001, ?, 'student', -1, 'Python3', 'print(1)', now())",
                studentId
        );
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void accountAnnouncementAndAiMainFlowShouldWork() throws Exception {
        MockHttpSession captchaSession = new MockHttpSession();
        String captcha = "1234";
        captchaSession.setAttribute("CAPTCHA_CODE", captcha);

        mockMvc.perform(post("/api/register")
                        .session(captchaSession)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"newbie",
                                  "password":"newbie-pass",
                                  "email":"newbie@example.com",
                                  "captcha":"%s"
                                }
                                """.formatted(captcha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Succeeded"));
        mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"student-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Succeeded"));

        mockMvc.perform(get("/api/profile").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.username").value("student"));
        mockMvc.perform(post("/api/admin/announcements")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"notice\",\"content\":\"hello\",\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.title").value("notice"));

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1));
        MvcResult inferenceResult = mockMvc.perform(post("/api/ai/tutor/inference")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"problem_id\":1001,\"language\":\"Python3\",\"code_snippet\":\"print(1)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andReturn();

        String taskId = objectMapper.readTree(inferenceResult.getResponse().getContentAsString())
                .get("data")
                .get("task_id")
                .asText();
        String sessionId = objectMapper.readTree(inferenceResult.getResponse().getContentAsString())
                .get("data")
                .get("session_id")
                .asText();

        mockMvc.perform(get("/api/ai/tutor/task")
                        .with(user("student").roles("USER"))
                        .param("task_id", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.task_id").value(taskId));

        mockMvc.perform(get("/api/ai/tutor/session")
                        .with(user("student").roles("USER"))
                        .param("session_id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.session_id").value(sessionId));

        mockMvc.perform(delete("/api/ai/tutor/session")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .param("session_id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Session closed"));

        mockMvc.perform(get("/api/ai/skill/radar").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.user_id").exists());

        mockMvc.perform(get("/api/ai/review/due").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.due_reviews").isArray())
                .andExpect(jsonPath("$.data.due_reviews[0].problem_key").value("P1001"))
                .andExpect(jsonPath("$.data.due_reviews[0].title").value("A+B"));

        mockMvc.perform(post("/api/ai/preflight/check")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"problem_id\":1001,\"detector_name\":\"loop\",\"line_number\":1,\"code_snippet\":\"for i in range(1): pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.should_trigger").isBoolean());
        byte[] minimalPng = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADElEQVR4nGMAAQAABQABDQotmwAAAABJRU5ErkJggg==");
        mockMvc.perform(multipart("/api/upload-avatar")
                        .file(new MockMultipartFile("image", "avatar.png", "image/png", minimalPng))
                        .with(user("student").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Succeeded"));
        mockMvc.perform(multipart("/api/upload-avatar")
                        .file(new MockMultipartFile("image", "avatar.png", "image/png",
                                "<?php phpinfo(); ?>".getBytes()))
                        .with(user("student").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("file is not a valid image"));

        mockMvc.perform(post("/api/ai/tutor/notebook")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"problem_id\":1001,\"error_taxonomy\":\"logic_error\",\"root_cause\":\"off-by-one\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        mockMvc.perform(get("/api/ai/tutor/notebook").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/ai/calibration/status").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.needs_calibration").isBoolean());

        mockMvc.perform(get("/api/ai/knowledge-graph").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.nodes").isArray());

        mockMvc.perform(get("/api/ai/submission-river/1001").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.timeline").isArray());

        Long taskCount = jdbcTemplate.queryForObject("select count(*) from ai_inference_task", Long.class);
        assertThat(taskCount).isEqualTo(1);
    }

    @Test
    void reviewDueShouldIgnoreNonStandardNotebookTaxonomy() throws Exception {
        mockMvc.perform(post("/api/ai/tutor/notebook")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id": 1001,
                                  "language": "Python3",
                                  "error_taxonomy": "kc_review",
                                  "root_cause": "telemetry event should not become review card"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        mockMvc.perform(get("/api/ai/review/due").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.due_reviews").isEmpty())
                .andExpect(jsonPath("$.data.stats.due_count").value(0))
                .andExpect(jsonPath("$.data.stats.focus_count").value(0));
    }

    @Test
    void profileShouldClearBrokenAvatarReference() throws Exception {
        jdbcTemplate.update(
                """
                update user_profile up
                set avatar = '/public/avatar/missing-avatar.png'
                where up.user_id = (select id from "user" where username = 'student' limit 1)
                """
        );

        MvcResult result = mockMvc.perform(get("/api/profile").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).path("data").path("avatar").asText()).isEqualTo("");

        String avatar = jdbcTemplate.queryForObject(
                """
                select up.avatar
                from user_profile up
                join "user" u on u.id = up.user_id
                where u.username = 'student'
                limit 1
                """,
                String.class
        );
        assertThat(avatar).isEqualTo("");
    }

    @Test
    void profileShouldKeepAvatarWhenBlobExists() throws Exception {
        jdbcTemplate.update(
                """
                update user_profile up
                set avatar = '/public/avatar/existing-avatar.png'
                where up.user_id = (select id from "user" where username = 'student' limit 1)
                """
        );
        jdbcTemplate.update(
                """
                insert into sys_options(key, value, created_at, updated_at)
                values ('avatar_blob:existing-avatar.png', cast('{"base64":"aGVsbG8="}' as jsonb), now(), now())
                on conflict (key) do update
                set value = excluded.value,
                    updated_at = now()
                """
        );

        MvcResult result = mockMvc.perform(get("/api/profile").with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).path("data").path("avatar").asText())
                .isEqualTo("/public/avatar/existing-avatar.png");

        String avatar = jdbcTemplate.queryForObject(
                """
                select up.avatar
                from user_profile up
                join "user" u on u.id = up.user_id
                where u.username = 'student'
                limit 1
                """,
                String.class
        );
        assertThat(avatar).isEqualTo("/public/avatar/existing-avatar.png");
    }

    private long insertUser(String username, String email, String adminType, boolean disabled, String password) {
        Long id = jdbcTemplate.queryForObject(
                """
                insert into "user"(username, email, password_hash, admin_type, problem_permission, is_disabled, create_time)
                values (?, ?, ?, ?, 'None', ?, now())
                returning id
                """,
                Long.class,
                username,
                email,
                passwordEncoder.encode(password),
                adminType,
                disabled
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
