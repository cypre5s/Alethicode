package com.alethicode.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5436/alethicode",
        "spring.datasource.username=onlinejudge",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "logging.level.org.springframework.jdbc.core.JdbcTemplate=INFO"
})
@EnabledIfEnvironmentVariable(named = "ALETHICODE_MANUAL_RESUME", matches = "1")
class LanguagePackAlethicodeResumeManualTest {

    private static final String PACK_SLUG = "python-basic";

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

    @Test
    void resumeLatestPythonV2TaskAndPublish() throws Exception {
        Long taskId = jdbcTemplate.queryForObject(
                """
                select t.id
                from language_pack_init_task t
                join language_pack lp on lp.id = t.language_pack_id
                where lp.slug = ? and lp.version = 2
                order by t.id desc
                limit 1
                """,
                Long.class,
                PACK_SLUG
        );
        assertThat(taskId).isNotNull();

        Long languagePackId = jdbcTemplate.queryForObject(
                "select language_pack_id from language_pack_init_task where id = ?",
                Long.class,
                taskId
        );
        assertThat(languagePackId).isNotNull();

        String stage = jdbcTemplate.queryForObject(
                "select stage from language_pack_init_task where id = ?",
                String.class,
                taskId
        );
        assertThat(stage).isNotBlank();

        if (List.of("failed", "kc_ready", "segments_ready", "units_ready").contains(stage)) {
            stage = invokeStage(taskId, "extract-examples").path("stage").asText();
        }
        if (List.of("failed", "oj_candidates_ready").contains(stage)) {
            stage = invokeStage(taskId, "generate-problems").path("stage").asText();
        }
        if (List.of("failed", "problem_packages_ready").contains(stage)) {
            stage = invokeStage(taskId, "validate-problems").path("stage").asText();
        }
        if (List.of("failed", "problems_validated").contains(stage)) {
            stage = invokeStage(taskId, "publish").path("stage").asText();
        }

        assertThat(stage).isEqualTo("published");
        String packStatus = jdbcTemplate.queryForObject(
                "select status from language_pack where id = ?",
                String.class,
                languagePackId
        );
        assertThat(packStatus).isEqualTo("published");
    }

    private JsonNode invokeStage(Long taskId, String stageAction) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/language-packs/init-tasks/{taskId}/" + stageAction, taskId)
                                .with(admin())
                                .with(csrf())
                )
                .andReturn();
        return extractData(result, stageAction);
    }

    private JsonNode extractData(MvcResult result, String action) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        if (status != 200) {
            throw new AssertionError(action + " failed: status=" + status + ", body=" + body);
        }
        JsonNode root = objectMapper.readTree(body);
        if (!root.path("error").isNull() && !root.path("error").asText().isBlank()) {
            throw new AssertionError(action + " failed with api error: " + body);
        }
        return root.path("data");
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("root").roles("ADMIN");
    }
}
