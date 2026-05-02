package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
class ClassroomM10IntegrationTest extends AbstractJdbcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private JudgeServerService judgeServerService;
    @MockBean private SystemAdminService systemAdminService;
    @MockBean private ReleaseNotesService releaseNotesService;
    @MockBean private PlatformConfigService platformConfigService;
    @MockBean private SystemOptionService systemOptionService;
    @MockBean private AdminUploadService adminUploadService;

    @BeforeEach
    void setUp() {
        insertUser("root", "Admin");
        insertUser("student", "Regular User");
        jdbcTemplate.update(
                """
                insert into problem(id, _id, title, description, visible, is_public, difficulty,
                                    statistic_info, source, submission_number, accepted_number,
                                    created_by_id, create_time, last_update_time)
                values (1001, 'P1001', 'A+B', 'desc', true, true, 'Low', cast('{}' as jsonb), 'test', 0, 0,
                        (select id from "user" where username='root'), now(), now())
                """
        );
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void m10CollabMonitorFlowShouldWork() throws Exception {
        String classroomId = createClassroom();
        String classroomProblemId = addClassroomProblem(classroomId);
        Long studentUserId = jdbcTemplate.queryForObject("select id from \"user\" where username='student'", Long.class);
        assertThat(studentUserId).isNotNull();

        MvcResult createSession = mockMvc.perform(post("/api/classroom/" + classroomId + "/sessions")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Relay Session",
                                  "mode":"RELAY",
                                  "problem_id":1001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.mode").value("relay"))
                .andReturn();
        String sessionId = json(createSession).at("/data/id").asText();
        assertThat(sessionId).isNotBlank();

        mockMvc.perform(get("/api/classroom/" + classroomId + "/sessions")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/classroom/" + classroomId + "/sessions/" + sessionId + "/transfer-token")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"target_user_id\":" + studentUserId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Token transferred"));

        mockMvc.perform(post("/api/classroom/" + classroomId + "/sessions/" + sessionId + "/end")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Session ended"));

        mockMvc.perform(delete("/api/classroom/" + classroomId + "/sessions/" + sessionId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Session deleted"));

        jdbcTemplate.update(
                """
                insert into student_monitoring_snapshot(id, classroom_id, user_id, classroom_problem_id, activity_status, error_taxonomy,
                                                        code_snapshot, code_hash, edit_distance, submission_count,
                                                        ac_count, elapsed_time_seconds, snapshot_time)
                values ('snap-1', ?, ?, ?, 'typing', null, 'print(1)', 'h1', 8, 2, 1, 300, now())
                """,
                classroomId,
                studentUserId,
                classroomProblemId
        );

        mockMvc.perform(get("/api/classroom/" + classroomId + "/monitor/stats")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total_members").value(1))
                .andExpect(jsonPath("$.data.online_count").exists())
                .andExpect(jsonPath("$.data.coding_count").exists())
                .andExpect(jsonPath("$.data.active_coding").exists())
                .andExpect(jsonPath("$.data.avg_progress").exists());

        mockMvc.perform(get("/api/classroom/" + classroomId + "/monitor/error-clusters")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .param("time_window", "1440"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.clusters").isArray());

        mockMvc.perform(get("/api/classroom/" + classroomId + "/monitor/snapshots")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/classroom/" + classroomId + "/monitor/playback")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .param("student_id", String.valueOf(studentUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.frames").isArray());

        mockMvc.perform(get("/api/classroom/" + classroomId + "/monitor/coach")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .param("action", "clusters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.clusters").isArray());

        mockMvc.perform(get("/api/classroom/" + classroomId + "/monitor/review-queue")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/classroom/" + classroomId + "/monitor/review-verdict")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"submission_id\":\"sub-m10-1\",\"verdict\":\"AC\"}"))
                .andExpect(status().isNotFound());
    }

    private String createClassroom() throws Exception {
        MvcResult createClassroom = mockMvc.perform(post("/api/classroom")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Java 班\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();
        String classroomId = json(createClassroom).at("/data/id").asText();
        assertThat(classroomId).isNotBlank();

        MvcResult invitation = mockMvc.perform(post("/api/classroom/invitation/generate/" + classroomId)
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"max_uses\":3,\"expire_hours\":24,\"default_role\":\"student\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = json(invitation).at("/data/code").asText();

        mockMvc.perform(post("/api/classroom/invitation/join")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());
        return classroomId;
    }

    private String addClassroomProblem(String classroomId) throws Exception {
        MvcResult createClassroomProblem = mockMvc.perform(post("/api/classroom/" + classroomId + "/problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"problem_id\":1001,\"is_visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();
        String classroomProblemId = json(createClassroomProblem).at("/data/id").asText();
        assertThat(classroomProblemId).isNotBlank();
        return classroomProblemId;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void insertUser(String username, String adminType) {
        jdbcTemplate.update(
                "insert into \"user\"(username, admin_type, create_time) values (?, ?, now())",
                username,
                adminType
        );
    }
}
