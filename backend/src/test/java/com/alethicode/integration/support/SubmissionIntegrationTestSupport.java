package com.alethicode.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
abstract class SubmissionIntegrationTestSupport extends AbstractJdbcIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean protected JudgeServerService judgeServerService;
    @MockBean protected SystemAdminService systemAdminService;
    @MockBean protected ReleaseNotesService releaseNotesService;
    @MockBean protected PlatformConfigService platformConfigService;
    @MockBean protected SystemOptionService systemOptionService;
    @MockBean protected AdminUploadService adminUploadService;
    @MockBean protected AiModelGateway aiModelGateway;

    protected long studentId;
    protected long teacherId;
    protected long baseProblemId;
    protected long objectiveProblemId;
    protected long wrongProblemId;

    @BeforeEach
    void seedSubmissionFixture() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "readability", 4,
                "readability_comment", "变量命名比较清晰",
                "efficiency", 4,
                "efficiency_comment", "复杂度符合题目要求",
                "style", 3,
                "style_comment", "可以补一行空行增强可读性"
        ));

        upsertThrottling("""
                {
                  "ip": {"capacity": 100, "fill_rate": 0.1, "default_capacity": 50},
                  "user": {"capacity": 20, "fill_rate": 0.03, "default_capacity": 10}
                }
                """);

        studentId = insertUser("student", "Regular User", "None", false);
        teacherId = insertUser("teacher", "Admin", "Own", false);
        insertUser("admin", "Admin", "Own", false);
        insertUser("root", "Admin", "All", false);

        jdbcTemplate.update(
                "insert into user_profile(user_id, acm_problems_status, real_name) values (?, cast(? as jsonb), ?)",
                studentId,
                "{}",
                "Student"
        );
        jdbcTemplate.update(
                "insert into user_profile(user_id, acm_problems_status, real_name) values (?, cast(? as jsonb), ?)",
                teacherId,
                "{}",
                "Teacher"
        );

        baseProblemId = insertProblem("P-BASE", teacherId, true, false, "{}", "[\"Python3\",\"C++\"]");
        objectiveProblemId = insertProblem(
                "P-OBJ",
                teacherId,
                true,
                false,
                "{\"objective_question\":{\"question_type\":\"choice\",\"answer\":\"A\"}}",
                "[\"Python3\"]"
        );
        wrongProblemId = insertProblem("P-W1", teacherId, true, false, "{}", "[\"Python3\"]");

        String solvedJson = "{\"problems\":{\"" + baseProblemId + "\":{\"status\":0,\"_id\":\"P-BASE\"}}}";
        jdbcTemplate.update(
                "update user_profile set acm_problems_status = cast(? as jsonb) where user_id = ?",
                solvedJson,
                studentId
        );

        insertSubmission("sub-own", baseProblemId, studentId, "student", -1, false,
                "{\"time_cost\":11,\"memory_cost\":22}",
                "{}"
        );
        insertSubmission("sub-shared", baseProblemId, teacherId, "teacher", 0, true,
                "{\"time_cost\":33,\"memory_cost\":44}",
                "{\"note\":\"shared\"}"
        );
        insertSubmission("sub-private", baseProblemId, teacherId, "teacher", -1, false,
                "{\"time_cost\":1,\"memory_cost\":1}",
                "{\"note\":\"private\"}"
        );
        insertSubmission("sub-review", baseProblemId, teacherId, "teacher", 6, false,
                "{\"needs_human_review\":true,\"problem_type\":\"choice\",\"llm_judge\":{\"model\":\"m\",\"parsed_verdict\":\"wa\",\"confidence\":0.75},\"knowledge_tags\":[\"loop\"]}",
                "{}"
        );
        insertSubmission("sub-ac", baseProblemId, teacherId, "teacher", 0, false,
                "{\"time_cost\":120,\"memory_cost\":256}",
                "{}"
        );
        insertSubmission("sub-wrong-a", wrongProblemId, studentId, "student", -1, false,
                "{\"time_cost\":50,\"memory_cost\":50}",
                "{}"
        );
        insertSubmission("sub-wrong-b", wrongProblemId, studentId, "student", -2, false,
                "{\"time_cost\":51,\"memory_cost\":51}",
                "{}"
        );
    }

    protected void registerJudgeServer(String hostname, String serviceUrl) {
        jdbcTemplate.update(
                "insert into judge_server(hostname, judger_version, cpu_core, memory_usage, cpu_usage, service_url, ip, is_disabled, last_heartbeat, create_time, task_number) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?)",
                hostname,
                "1.6.1",
                4,
                10.0,
                10.0,
                serviceUrl,
                "127.0.0.1",
                false,
                0
        );
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> problemListRow(String username, String displayId) throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/problems")
                        .param("limit", "10")
                        .param("offset", "0")
                        .with(user(username).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();
        Map<String, Object> listPayload = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) listPayload.get("data");
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        return results.stream()
                .filter(item -> displayId.equals(String.valueOf(item.get("_id"))))
                .findFirst()
                .orElseThrow();
    }

    protected long insertUser(String username, String adminType, String problemPermission, boolean disabled) {
        Long id = jdbcTemplate.queryForObject(
                "insert into \"user\"(username, create_time, admin_type, problem_permission, is_disabled) values (?, now(), ?, ?, ?) returning id",
                Long.class,
                username,
                adminType,
                problemPermission,
                disabled
        );
        return id == null ? 0L : id;
    }

    protected long insertProblem(String displayId, long createdById, boolean visible, boolean aiGenerated,
                                 String statisticInfoJson, String languagesJson) {
        Long id = jdbcTemplate.queryForObject(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    visible, difficulty, source, submission_number, accepted_number,
                    statistic_info, is_ai_generated, visibility_status
                ) values (
                    ?, ?, '', '', '',
                    cast(? as jsonb), ?, cast(? as jsonb), '',
                    cast(? as jsonb), cast(? as jsonb), ?, 1000, 256,
                    ?, 'Low', '', 0, 0,
                    cast(? as jsonb), ?, 'class_private'
                ) returning id
                """,
                Long.class,
                displayId,
                displayId,
                "[]",
                "tc-" + displayId,
                "[]",
                languagesJson,
                "{\"Python3\":\"print(1)\"}",
                createdById,
                visible,
                statisticInfoJson,
                aiGenerated
        );
        return id == null ? 0L : id;
    }

    protected void insertSubmission(String id, long problemId, long userId, String username, int result, boolean shared,
                                    String statisticInfoJson, String infoJson) {
        jdbcTemplate.update(
                """
                insert into submission(id, problem_id, create_time, user_id, username, code, result, info, language, shared, statistic_info, ip)
                values (?, ?, now(), ?, ?, ?, ?, cast(? as jsonb), ?, ?, cast(? as jsonb), ?)
                """,
                id,
                problemId,
                userId,
                username,
                "print(1)",
                result,
                infoJson,
                "Python3",
                shared,
                statisticInfoJson,
                "127.0.0.1"
        );
    }

    protected void upsertThrottling(String json) {
        jdbcTemplate.update(
                """
                insert into sys_options(key, value)
                values (?, cast(? as jsonb))
                on conflict (key) do update set value = excluded.value
                """,
                "throttling",
                json
        );
    }

    protected int awaitSubmissionResult(String submissionId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int last = 6;
        while (System.currentTimeMillis() < deadline) {
            Integer value = jdbcTemplate.queryForObject(
                    "select result from submission where id = ?",
                    Integer.class,
                    submissionId
            );
            if (value != null) {
                last = value;
                if (value != 6 && value != 7) {
                    return value;
                }
            }
            Thread.sleep(100);
        }
        return last;
    }

    protected Map<String, Object> awaitSubmissionStatisticInfo(String submissionId, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Map<String, Object> latest = Map.of();
        while (System.currentTimeMillis() < deadline) {
            String json = jdbcTemplate.queryForObject(
                    "select statistic_info::text from submission where id = ?",
                    String.class,
                    submissionId
            );
            latest = objectMapper.readValue(json, Map.class);
            if (!latest.isEmpty()) {
                return latest;
            }
            Thread.sleep(100);
        }
        return latest;
    }
}
