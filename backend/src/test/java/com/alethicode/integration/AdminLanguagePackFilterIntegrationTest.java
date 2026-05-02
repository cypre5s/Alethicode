package com.alethicode.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLanguagePackFilterIntegrationTest extends ProblemIntegrationTestSupport {

    @Test
    void adminProblemsShouldSupportLanguagePackFilter() throws Exception {
        Long packA = insertLanguagePack("python-basic", 1, "Python 基础");
        Long packB = insertLanguagePack("java-basic", 1, "Java 基础");

        Long existingProblemId = jdbcTemplate.queryForObject(
                "select id from problem where _id = ?",
                Long.class,
                "PPT2-001"
        );
        mapProblemToLanguagePack(existingProblemId, packA);

        Long packBProblemId = insertProblem("PACK-B-001", "Pack B Problem", true, false);
        mapProblemToLanguagePack(packBProblemId, packB);

        mockMvc.perform(get("/api/admin/problems")
                        .param("limit", "20")
                        .param("offset", "0")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/admin/problems")
                        .param("limit", "20")
                        .param("offset", "0")
                        .param("language_pack_id", String.valueOf(packA))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0]._id").value("PPT2-001"));

        mockMvc.perform(get("/api/admin/problems")
                        .param("limit", "20")
                        .param("offset", "0")
                        .param("language_pack_id", "invalid")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Invalid language_pack_id"));

        mockMvc.perform(get("/api/admin/problems")
                        .param("limit", "20")
                        .param("offset", "0")
                        .param("language_pack_id", "-1")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Invalid language_pack_id"));
    }

    @Test
    void adminVariantReviewShouldSupportLanguagePackFilter() throws Exception {
        Long packA = insertLanguagePack("variant-pack-a", 1, "Variant A");
        Long packB = insertLanguagePack("variant-pack-b", 1, "Variant B");

        Long variantA = insertProblem("VAR-A-001", "Variant A Problem", false, true);
        Long variantB = insertProblem("VAR-B-001", "Variant B Problem", false, true);
        mapProblemToLanguagePack(variantA, packA);
        mapProblemToLanguagePack(variantB, packB);

        mockMvc.perform(get("/api/admin/ai/variant-review")
                        .param("page", "1")
                        .param("limit", "20")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/admin/ai/variant-review")
                        .param("page", "1")
                        .param("limit", "20")
                        .param("language_pack_id", String.valueOf(packA))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].display_id").value("VAR-A-001"));

        mockMvc.perform(get("/api/admin/ai/variant-review")
                        .param("page", "1")
                        .param("limit", "20")
                        .param("language_pack_id", "bad")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Invalid language_pack_id"));

        mockMvc.perform(get("/api/admin/ai/variant-review")
                        .param("page", "1")
                        .param("limit", "20")
                        .param("language_pack_id", "-1")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Invalid language_pack_id"));
    }

    @Test
    void teacherShouldHaveFullAiVariantReviewPermissions() throws Exception {
        insertTeacherUser("teacher");
        Long packA = insertLanguagePack("variant-pack-teacher-a", 1, "Variant Teacher A");
        Long packB = insertLanguagePack("variant-pack-teacher-b", 1, "Variant Teacher B");

        Long variantA = insertProblem("VAR-TA-001", "Variant Teacher A Problem", false, true);
        Long variantB = insertProblem("VAR-TB-001", "Variant Teacher B Problem", false, true);
        mapProblemToLanguagePack(variantA, packA);
        mapProblemToLanguagePack(variantB, packB);

        mockMvc.perform(get("/api/admin/ai/variant-review")
                        .param("page", "1")
                        .param("limit", "20")
                        .param("language_pack_id", String.valueOf(packB))
                        .with(user("teacher").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].display_id").value("VAR-TB-001"));

        mockMvc.perform(post("/api/admin/ai/variant-review/" + variantB + "/reject")
                        .with(user("teacher").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void adminKcListShouldSupportLanguagePackFilter() throws Exception {
        Long packA = insertLanguagePack("kc-pack-a", 1, "KC A");
        Long packB = insertLanguagePack("kc-pack-b", 1, "KC B");

        insertKnowledgeComponent(packA, "循环结构", "loop_structure");
        insertKnowledgeComponent(packB, "数组基础", "array_basic");

        mockMvc.perform(get("/api/admin/ai/kc-list")
                        .param("page", "1")
                        .param("page_size", "20")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/admin/ai/kc-list")
                        .param("page", "1")
                        .param("page_size", "20")
                        .param("language_pack_id", String.valueOf(packA))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].name").value("循环结构"));

        mockMvc.perform(get("/api/admin/ai/kc-list")
                        .param("page", "1")
                        .param("page_size", "20")
                        .param("language_pack_id", "oops")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Invalid language_pack_id"));

        mockMvc.perform(get("/api/admin/ai/kc-list")
                        .param("page", "1")
                        .param("page_size", "20")
                        .param("language_pack_id", "-1")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Invalid language_pack_id"));
    }

    @Test
    void teacherShouldHaveFullAiKnowledgeManagementPermissions() throws Exception {
        insertTeacherUser("teacher");
        Long packA = insertLanguagePack("kc-pack-teacher-a", 1, "KC Teacher A");
        Long packB = insertLanguagePack("kc-pack-teacher-b", 1, "KC Teacher B");

        insertKnowledgeComponent(packA, "循环结构", "loop_structure");
        Long kcId = insertKnowledgeComponent(packB, "数组基础", "array_basic");

        mockMvc.perform(get("/api/admin/ai/kc-list")
                        .param("page", "1")
                        .param("page_size", "20")
                        .param("language_pack_id", String.valueOf(packB))
                        .with(user("teacher").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].name").value("数组基础"));

        mockMvc.perform(put("/api/admin/ai/kc/" + kcId)
                        .contentType("application/json")
                        .content("{\"description\":\"老师可管理全部 AI 教学知识点\"}")
                        .with(user("teacher").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").value(kcId.intValue()));
    }

    private Long insertTeacherUser(String username) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO "user"(username, admin_type, problem_permission, is_disabled, create_time)
                VALUES (?, 'Teacher', 'Own', false, now())
                RETURNING id
                """,
                Long.class,
                username
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

    private Long insertProblem(String displayId, String title, boolean visible, boolean aiGenerated) {
        return jdbcTemplate.queryForObject(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    visible, difficulty, source, submission_number, accepted_number,
                    statistic_info, is_ai_generated, visibility_status, create_time, last_update_time
                ) values (
                    ?, ?, '', '', '',
                    cast('[]' as jsonb), ?, cast('[]' as jsonb), '',
                    cast('["Python3"]' as jsonb), cast('{"Python3":"print(1)"}' as jsonb), ?, 1000, 256,
                    ?, 'Low', 'book', 0, 0,
                    cast('{}' as jsonb), ?, 'class_private', now(), now()
                )
                returning id
                """,
                Long.class,
                displayId,
                title,
                "tc-" + displayId.toLowerCase(),
                studentId,
                visible,
                aiGenerated
        );
    }

    private void mapProblemToLanguagePack(Long problemId, Long languagePackId) {
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_problem_mapping(language_pack_id, problem_id, generation_log_id, create_time)
                VALUES (?, ?, NULL, now())
                """,
                languagePackId,
                problemId
        );
    }

    private Long insertKnowledgeComponent(Long languagePackId, String name, String normalizedName) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO ai_knowledge_component(
                    name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess, language_pack_id, name_normalized
                ) VALUES (?, '', '1', '', 0.1, 0.15, 0.1, 0.2, ?, ?)
                RETURNING id
                """,
                Long.class,
                name,
                languagePackId,
                normalizedName
        );
    }
}
