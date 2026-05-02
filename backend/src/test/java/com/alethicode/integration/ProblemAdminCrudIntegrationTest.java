package com.alethicode.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemAdminCrudIntegrationTest extends ProblemIntegrationTestSupport {

    @Test
    void adminProblemsShouldWorkOnDatabaseBackedFlow() throws Exception {
        mockMvc.perform(get("/api/admin/problems")
                        .param("limit", "10")
                        .param("offset", "0")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0]._id").value("PPT2-001"))
                .andExpect(jsonPath("$.data.results[0].created_by.username").value("student"));

        mockMvc.perform(get("/api/admin/problems")
                        .param("id", "99999")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Problem does not exist"));
    }

    @Test
    void adminProblemCrudShouldWorkOnDatabaseBackedFlow() throws Exception {
        String createPayload = """
                {
                  "_id": "PPT2-888",
                  "title": "Created From Integration",
                  "description": "<p>desc</p>",
                  "input_description": "<p>in</p>",
                  "output_description": "<p>out</p>",
                  "samples": [{"input":"1","output":"1"}],
                  "test_case_id": "tc-create",
                  "test_case_score": [{"input_name":"1.in","output_name":"1.out","score":100}],
                  "time_limit": 1000,
                  "memory_limit": 256,
                  "languages": ["Python3"],
                  "template": {"Python3":"print(1)"},
                  "visible": true,
                  "difficulty": "Low",
                  "tags": ["graphs"],
                  "hint": "",
                  "source": ""
                }
                """;

        mockMvc.perform(post("/api/admin/problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data._id").value("PPT2-888"));

        Long createdId = jdbcTemplate.queryForObject(
                "select id from problem where _id = ?",
                Long.class,
                "PPT2-888"
        );

        String updatePayload = """
                {
                  "id": %d,
                  "_id": "PPT2-888",
                  "title": "Updated Title",
                  "description": "<p>desc2</p>",
                  "input_description": "<p>in2</p>",
                  "output_description": "<p>out2</p>",
                  "samples": [{"input":"2","output":"2"}],
                  "test_case_id": "tc-create",
                  "test_case_score": [{"input_name":"1.in","output_name":"1.out","score":100}],
                  "time_limit": 2000,
                  "memory_limit": 512,
                  "languages": ["Python3"],
                  "template": {"Python3":"print(2)"},
                  "visible": true,
                  "difficulty": "Mid",
                  "tags": ["graphs","dp"],
                  "hint": "",
                  "source": ""
                }
                """.formatted(createdId);

        mockMvc.perform(put("/api/admin/problems")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/admin/problems")
                        .param("id", String.valueOf(createdId))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.title").value("Updated Title"))
                .andExpect(jsonPath("$.data.tags").isArray());

        mockMvc.perform(delete("/api/admin/problems")
                        .param("id", String.valueOf(createdId))
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
