package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemReadContractIntegrationTest extends ProblemIntegrationTestSupport {

    @Test
    void problemsListAndDetailShouldMatchCoreContract() throws Exception {
        mockMvc.perform(get("/api/problems")
                        .param("limit", "10")
                        .param("offset", "0")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0]._id").value("PPT2-001"))
                .andExpect(jsonPath("$.data.results[0].my_status").value(0))
                .andExpect(jsonPath("$.data.results[0].languages[0]").value("Python3"))
                .andExpect(jsonPath("$.data.results[0].languages[1]").value("C"))
                .andExpect(jsonPath("$.data.results[0].languages[2]").value("C++"))
                .andExpect(jsonPath("$.data.results[0].languages[3]").value("Java"))
                .andExpect(jsonPath("$.data.results[0].template.Python3", containsString("print(1)")))
                .andExpect(jsonPath("$.data.results[0].template.C", containsString("#include <stdio.h>")))
                .andExpect(jsonPath("$.data.results[0].template.Java", containsString("public class Main")))
                .andExpect(jsonPath("$.data.results[0].tags").isArray());

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", "PPT2-001")
                        .param("with_kcs", "true")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data._id").value("PPT2-001"))
                .andExpect(jsonPath("$.data.ai_tutor_enabled").value(true))
                .andExpect(jsonPath("$.data.created_by.username").value("student"))
                .andExpect(jsonPath("$.data.my_status").value(0))
                .andExpect(jsonPath("$.data.languages[0]").value("Python3"))
                .andExpect(jsonPath("$.data.languages[1]").value("C"))
                .andExpect(jsonPath("$.data.languages[2]").value("C++"))
                .andExpect(jsonPath("$.data.languages[3]").value("Java"))
                .andExpect(jsonPath("$.data.template.C++", containsString("#include <iostream>")))
                .andExpect(jsonPath("$.data.kc_names").isArray());
    }

    @Test
    void objectiveProblemInProblemBankShouldNotEnableAiTutor() throws Exception {
        jdbcTemplate.update(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    visible, difficulty, source, submission_number, accepted_number,
                    statistic_info, is_ai_generated, visibility_status
                ) values (
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, cast(? as jsonb), ?,
                    cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, ?
                )
                """,
                "OBJ-001",
                "Objective Demo",
                "<p>private desc</p>",
                "<p>private input</p>",
                "<p>private output</p>",
                "[]",
                "tc-obj-001",
                "[]",
                "",
                "[\"Python3\"]",
                "{\"Python3\":\"//PREPEND BEGIN\\n\\n//PREPEND END\\n\\n//TEMPLATE BEGIN\\nprint(1)\\n//TEMPLATE END\\n\\n//APPEND BEGIN\\n\\n//APPEND END\"}",
                studentId,
                1000,
                256,
                true,
                "Low",
                "book",
                0,
                0,
                "{\"objective_question\":{\"question_type\":\"choice\",\"answer\":\"A\"}}",
                false,
                "class_private"
        );
        seedProblemTestCase("tc-obj-001");

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", "OBJ-001")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data._id").value("OBJ-001"))
                .andExpect(jsonPath("$.data.ai_tutor_enabled").value(false));
    }

    @SuppressWarnings("unchecked")
    @Test
    void studentPrivateAiProblemShouldOnlyBeVisibleToCreatorInProblemListAndDetail() throws Exception {
        jdbcTemplate.update(
                "insert into \"user\"(username, create_time, admin_type, problem_permission, is_disabled) values (?, now(), ?, ?, ?)",
                "other",
                "Regular User",
                "None",
                false
        );

        jdbcTemplate.update(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    visible, difficulty, source, submission_number, accepted_number,
                    statistic_info, is_ai_generated, visibility_status
                ) values (
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, cast(? as jsonb), ?,
                    cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, ?
                )
                """,
                "2.1.999",
                "2.1.999 Creator Only",
                "<p>private desc</p>",
                "<p>private input</p>",
                "<p>private output</p>",
                "[{\"input\":\"2 3\",\"output\":\"5\"}]",
                "tc-private-ai",
                "[]",
                "<p>private hint</p>",
                "[\"Python3\"]",
                "{\"Python3\":\"//PREPEND BEGIN\\n\\n//PREPEND END\\n\\n//TEMPLATE BEGIN\\nprint(2)\\n//TEMPLATE END\\n\\n//APPEND BEGIN\\n\\n//APPEND END\"}",
                studentId,
                1000,
                256,
                false,
                "Low",
                "AI-Transfer Temp",
                0,
                0,
                "{}",
                true,
                "student_private"
        );
        seedProblemTestCase("tc-private-ai");

        MvcResult creatorListResult = mockMvc.perform(get("/api/problems")
                        .param("limit", "10")
                        .param("offset", "0")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();

        Map<String, Object> creatorWrapped = objectMapper.readValue(
                creatorListResult.getResponse().getContentAsString(StandardCharsets.UTF_8),
                Map.class
        );
        Map<String, Object> creatorData = (Map<String, Object>) creatorWrapped.get("data");
        assertThat(creatorData.get("total")).isEqualTo(2);
        assertThat(((java.util.List<Map<String, Object>>) creatorData.get("results")).stream()
                .map(row -> String.valueOf(row.get("_id"))))
                .contains("PPT2-001", "2.1.999");

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", "2.1.999")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data._id").value("2.1.999"))
                .andExpect(jsonPath("$.data.visibility_status").value("student_private"))
                .andExpect(jsonPath("$.data.ai_tutor_enabled").value(false))
                .andExpect(jsonPath("$.data.languages[0]").value("Python3"));

        Long privateProblemPk = jdbcTemplate.queryForObject(
                "select id from problem where _id = ?",
                Long.class,
                "2.1.999"
        );
        assertThat(privateProblemPk).isNotNull();

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", String.valueOf(privateProblemPk))
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data._id").value("2.1.999"));

        mockMvc.perform(get("/api/problems")
                        .param("limit", "10")
                        .param("offset", "0")
                        .with(user("other").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0]._id").value("PPT2-001"));

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", "2.1.999")
                        .with(user("other").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Problem does not exist"));

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", String.valueOf(privateProblemPk))
                        .with(user("other").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Problem does not exist"));

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", "2.1.999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Problem does not exist"));

        mockMvc.perform(get("/api/problems")
                        .param("problem_id", String.valueOf(privateProblemPk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Problem does not exist"));
    }

    @Test
    void tagProgressAndRandomShouldWorkOnDatabaseBackedFlow() throws Exception {
        mockMvc.perform(get("/api/problems/tag-progress")
                        .param("user_id", String.valueOf(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("permission-denied"))
                .andExpect(jsonPath("$.data").value("请先登录"));

        mockMvc.perform(get("/api/problems/tag-progress")
                        .param("user_id", String.valueOf(studentId))
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.user_id").value((int) studentId))
                .andExpect(jsonPath("$.data.tags").isArray())
                .andExpect(jsonPath("$.data.tags[0].solved").value(1));

        mockMvc.perform(get("/api/problems/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("PPT2-001"));
    }
}
