package com.alethicode.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionPermissionQueryIntegrationTest extends SubmissionIntegrationTestSupport {

    @Test
    void submissionGetAndShareShouldRespectPermission() throws Exception {
        mockMvc.perform(get("/api/submission")
                        .param("id", "sub-private")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("No permission for this submission"));

        // SEC CRIT-2: 非 admin 角色不得看到 per-case exit_code/signal/cpu_time（侧信道泄题），
        // 也不得看到提交者真实 IP（隐私）。
        mockMvc.perform(get("/api/submission")
                        .param("id", "sub-shared")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.problem").value("P-BASE"))
                .andExpect(jsonPath("$.data.info").doesNotExist())
                .andExpect(jsonPath("$.data.ip").doesNotExist());

        mockMvc.perform(get("/api/submission")
                        .param("id", "sub-private")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.info.note").value("private"))
                .andExpect(jsonPath("$.data.problem").value((int) baseProblemId));

        mockMvc.perform(put("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"id\":\"sub-shared\",\"shared\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("No permission to share the submission"));

        mockMvc.perform(put("/api/submission")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"id\":\"sub-shared\",\"shared\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        Boolean shared = jdbcTemplate.queryForObject(
                "select shared from submission where id = ?",
                Boolean.class,
                "sub-shared"
        );
        assertThat(shared).isFalse();
    }

    @Test
    void submissionListExistsAndStatisticsShouldWork() throws Exception {
        mockMvc.perform(get("/api/submissions")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Limit is needed"));

        // SEC HIGH-1: 非 admin/teacher 默认强制按 user_id 过滤，只能看自己的提交
        // (即便不带 myself=1 / 即便站点 submission_list_show_all=true)。
        mockMvc.perform(get("/api/submissions")
                        .param("limit", "10")
                        .param("offset", "0")
                        .param("problem_id", "P-BASE")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].username").value("student"));

        // SEC HIGH-1: 学生带 username 参数也只能看自己（参数被忽略）。
        mockMvc.perform(get("/api/submissions")
                        .param("limit", "10")
                        .param("offset", "0")
                        .param("problem_id", "P-BASE")
                        .param("username", "teacher")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.results[0].username").value("student"));

        // admin 默认仍能查全平台、用 username 过滤特定用户。
        mockMvc.perform(get("/api/submissions")
                        .param("limit", "10")
                        .param("offset", "0")
                        .param("problem_id", "P-BASE")
                        .param("username", "teacher")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].username").value("teacher"));

        mockMvc.perform(get("/api/submissions")
                        .param("limit", "10")
                        .param("offset", "0")
                        .param("myself", "1")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].username").value("student"));

        mockMvc.perform(get("/api/submission-exists")
                        .param("problem_id", String.valueOf(baseProblemId))
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/submission-exists")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Parameter error, problem_id is required"));

        mockMvc.perform(get("/api/problems/statistics")
                        .param("problem_id", "P-BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.problem_display_id").value("P-BASE"))
                .andExpect(jsonPath("$.data.total_count").value(2));
    }
}
