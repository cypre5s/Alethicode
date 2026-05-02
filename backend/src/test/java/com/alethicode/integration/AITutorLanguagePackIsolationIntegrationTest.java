package com.alethicode.integration;

import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
class AITutorLanguagePackIsolationIntegrationTest extends AbstractJdbcIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private JudgeServerService judgeServerService;
    @MockBean private SystemAdminService systemAdminService;
    @MockBean private ReleaseNotesService releaseNotesService;
    @MockBean private PlatformConfigService platformConfigService;
    @MockBean private SystemOptionService systemOptionService;
    @MockBean private AdminUploadService adminUploadService;

    private Long rootUserId;
    private Long studentUserId;
    private Long languagePackIdA;
    private Long languagePackIdB;
    private Long kcIdInPackB;

    @BeforeEach
    void setUp() {
        rootUserId = insertUser("root", "Admin");
        studentUserId = insertUser("student", "User");
        languagePackIdA = insertLanguagePack("python-basic", 1, "Python基础");
        languagePackIdB = insertLanguagePack("python-advanced", 1, "Python进阶");

        jdbcTemplate.update(
                """
                INSERT INTO classroom(id, name, created_by_id, is_active, create_time, update_time)
                VALUES (?, ?, ?, true, now(), now())
                """,
                "class-1",
                "Class 1",
                rootUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO classroom_member(id, classroom_id, user_id, role, join_time, update_time)
                VALUES (?, ?, ?, 'student', now(), now())
                """,
                "member-1",
                "class-1",
                studentUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO classroom_language_pack(classroom_id, language_pack_id, create_time)
                VALUES (?, ?, now())
                """,
                "class-1",
                languagePackIdA
        );
        kcIdInPackB = insertKnowledgeComponent(languagePackIdB, "循环结构", "loop_structure");
    }

    @Test
    void studentMissingLanguagePackIdShouldReturnBadRequestForKnowledgeEndpoints() throws Exception {
        mockMvc.perform(get("/api/ai/skill/radar")
                        .with(user("student").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));

        mockMvc.perform(get("/api/ai/knowledge-graph")
                        .with(user("student").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));

        mockMvc.perform(get("/api/ai/knowledge-graph/snapshot")
                        .param("before_date", "2026-04-01")
                        .with(user("student").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));

        mockMvc.perform(get("/api/ai/knowledge-graph/kc/1/detail")
                        .with(user("student").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));
    }

    @Test
    void studentUnauthorizedLanguagePackShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/ai/knowledge-graph")
                        .param("language_pack_id", String.valueOf(languagePackIdB))
                        .with(user("student").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("permission-denied"))
                .andExpect(jsonPath("$.data").value("Permission denied"));
    }

    @Test
    void adminMissingLanguagePackIdShouldReturnBadRequestForKnowledgeEndpoints() throws Exception {
        mockMvc.perform(get("/api/ai/skill/radar")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));

        mockMvc.perform(get("/api/ai/knowledge-graph")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));

        mockMvc.perform(get("/api/ai/knowledge-graph/snapshot")
                        .param("before_date", "2026-04-01")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));

        mockMvc.perform(get("/api/ai/knowledge-graph/kc/" + kcIdInPackB + "/detail")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").value("language_pack_id is required"));
    }

    @Test
    void adminCanQueryAnyPackForKnowledgeEndpoints() throws Exception {
        mockMvc.perform(get("/api/ai/skill/radar")
                        .param("language_pack_id", String.valueOf(languagePackIdB))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.radar_data").isMap());

        mockMvc.perform(get("/api/ai/knowledge-graph")
                        .param("language_pack_id", String.valueOf(languagePackIdB))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").isMap());

        mockMvc.perform(get("/api/ai/knowledge-graph/snapshot")
                        .param("before_date", "2026-04-01")
                        .param("language_pack_id", String.valueOf(languagePackIdB))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.mastery_map").isMap());

        mockMvc.perform(get("/api/ai/knowledge-graph/kc/" + kcIdInPackB + "/detail")
                        .param("language_pack_id", String.valueOf(languagePackIdB))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.kc.id").value(kcIdInPackB.intValue()));
    }

    private Long insertUser(String username, String adminType) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO "user"(username, admin_type, create_time)
                VALUES (?, ?, now())
                RETURNING id
                """,
                Long.class,
                username,
                adminType
        );
    }

    private Long insertLanguagePack(String slug, int version, String name) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack(slug, version, name, primary_language, status, create_time, update_time)
                VALUES (?, ?, ?, 'Python3', 'published', now(), now())
                RETURNING id
                """,
                Long.class,
                slug,
                version,
                name
        );
    }

    private Long insertKnowledgeComponent(Long languagePackId, String name, String normalizedName) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO ai_knowledge_component(
                    name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess, language_pack_id, name_normalized
                )
                VALUES (?, '', '1', '', 0.3, 0.2, 0.1, 0.2, ?, ?)
                RETURNING id
                """,
                Long.class,
                name,
                languagePackId,
                normalizedName
        );
    }
}
