package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AITutorWorkflowStateMachineIntegrationTest extends AITutorWorkflowIntegrationTestSupport {

    @Test
    void m8WorkflowAndAdminFlowShouldWork() throws Exception {
        String sessionId = createWorkflowSession();

        mockMvc.perform(get("/api/ai/workflow/session")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .param("problem_id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.session_id").value(sessionId));

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"IDEATING",
                                  "event_data":{"message":"可以直接给我答案吗"}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.phase").value("IDEATING"))
                .andExpect(jsonPath("$.data.guardrail_result.passed").value(false));

        MvcResult cps = mockMvc.perform(get("/api/ai/workflow/checkpoint")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .param("session_id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.checkpoints").isArray())
                .andReturn();

        JsonNode cpsData = objectMapper.readTree(cps.getResponse().getContentAsString()).path("data");
        assertThat(cpsData.path("checkpoints").size()).isGreaterThan(0);
        String checkpointId = cpsData.path("checkpoints").get(0).path("checkpoint_id").asText();

        mockMvc.perform(post("/api/ai/workflow/checkpoint/restore")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "session_id":"%s",
                                  "checkpoint_id":"%s"
                                }
                                """.formatted(sessionId, checkpointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.session_id").value(sessionId));

        mockMvc.perform(post("/api/ai/workflow/interrupt")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "session_id":"%s",
                                  "action":"confirm",
                                  "data":{"ok":true}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.session_id").value(sessionId));

        MvcResult transferResult = mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"TRANSFER",
                                  "event_data":{}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.phase").value("TRANSFER"))
                .andExpect(jsonPath("$.data.node_outputs.transfer.problem_display_id").value(org.hamcrest.Matchers.matchesPattern("^2\\.1\\.00\\d$")))
                .andExpect(jsonPath("$.data.node_outputs.transfer.title").value(org.hamcrest.Matchers.matchesPattern("^2\\.1\\.00\\d\\s+.*$")))
                .andExpect(jsonPath("$.data.node_outputs.transfer.samples.length()").value(1))
                .andReturn();

        JsonNode transferPayload = objectMapper.readTree(transferResult.getResponse().getContentAsString())
                .path("data").path("node_outputs").path("transfer");
        String transferDisplayId = transferPayload.path("problem_display_id").asText();
        String persistedTitle = jdbcTemplate.queryForObject(
                "select title from problem where _id = ?",
                String.class,
                transferDisplayId
        );
        assertThat(persistedTitle).isNotNull();
        assertThat(persistedTitle).startsWith(transferDisplayId + " ");

        mockMvc.perform(delete("/api/ai/workflow/session")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"problem_id\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.cleared").value(true));

        mockMvc.perform(get("/api/admin/ai/variant-review")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.results[0].display_id").value(transferDisplayId));

        mockMvc.perform(post("/api/admin/ai/variant-review/2002/approve")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"display_id\":\"2.2.11\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").value(2002))
                .andExpect(jsonPath("$.data.display_id").value("2.2.11"))
                .andExpect(jsonPath("$.data.title").value("2.2.11 AI Pending"));

        mockMvc.perform(post("/api/admin/ai/variant-review/2003/reject")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Rejected"));

        mockMvc.perform(get("/api/admin/ai/kc-list")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .param("chapter", "1")
                        .param("keyword", "循"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results[0].name").value("循环"));

        mockMvc.perform(put("/api/admin/ai/kc/1")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"循环基础\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.name").value("循环基础"));

        mockMvc.perform(get("/api/admin/ai/kc/1/problems")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.results[0].display_id").value(transferDisplayId));

        mockMvc.perform(get("/api/admin/ai/classroom-chapters")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results").isArray());

        mockMvc.perform(get("/api/admin/ai/preflight/stats")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results").isArray());

        mockMvc.perform(post("/api/admin/ai/preflight/diagnose")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"detector_name\":\"loop_detector\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.stats.helpful_rate").isNumber())
                .andExpect(jsonPath("$.data.current_template.question").value("请检查循环边界"))
                .andExpect(jsonPath("$.data.current_template.hint").value("手动模拟 i=0 和 i=n-1"))
                .andExpect(jsonPath("$.data.diagnosis").value(org.hamcrest.Matchers.containsString("loop_detector")));

        mockMvc.perform(get("/api/admin/ai/mcmining/pending")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(post("/api/admin/ai/mcmining/approve")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"misconception_id\":\"mc-p1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.status").value("approved"));

        mockMvc.perform(post("/api/admin/ai/mcmining/reject")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"misconception_id\":\"mc-p2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.status").value("rejected"));

        jdbcTemplate.update(
                "insert into ai_misconception(id, kc_id, source, status, name, description, correction_hint, evidence_count) values ('mc-merge', 1, 'mcmining', 'pending', 'MergeMe', 'desc', 'hint', 1)"
        );

        mockMvc.perform(post("/api/admin/ai/mcmining/merge")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"misconception_id\":\"mc-merge\",\"target_id\":\"mc-target\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.merged").value(true));

        mockMvc.perform(post("/api/admin/ai/mcmining/discover")
                        .with(SecurityMockMvcRequestPostProcessors.user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.dispatched").value(true));

        Long activeCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_workflow_session where user_id = (select id from \"user\" where username = 'student') and is_active = true",
                Long.class
        );
        assertThat(activeCount).isEqualTo(0);
    }

    @Test
    void workflowShouldPersistBehaviorMetricsAndSupportChatAndLayeredAcReview() throws Exception {
        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"CHAT",
                                  "event_data":{
                                    "message":"我现在不知道先写哪一步",
                                    "behavior_metrics":{
                                      "consecutiveErrors":2,
                                      "submissionCount":3,
                                      "editFrequency":4,
                                      "dwellTime":15,
                                      "deleteRatio":0.25
                                    }
                                  }
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.phase").value("READING"))
                .andExpect(jsonPath("$.data.node_outputs.chat.history[0].role").value("user"))
                .andExpect(jsonPath("$.data.node_outputs.chat.history[1].role").value("assistant"))
                .andExpect(jsonPath("$.data.execution_trace[0].message_type").value("ai_reply"))
                .andExpect(jsonPath("$.data.behavior_metrics.consecutiveErrors").value(2))
                .andExpect(jsonPath("$.data.behavior_metrics.submissionCount").value(3))
                .andExpect(jsonPath("$.data.behavior_metrics.editFrequency").value(4))
                .andExpect(jsonPath("$.data.behavior_metrics.dwellTime").value(15))
                .andExpect(jsonPath("$.data.behavior_metrics.deleteRatio").value(0.25));

        mockMvc.perform(get("/api/ai/workflow/session")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                .param("session_id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.behavior_metrics.consecutiveErrors").value(2))
                .andExpect(jsonPath("$.data.node_outputs.chat.history[0].role").value("user"))
                .andExpect(jsonPath("$.data.node_outputs.chat.history[1].role").value("assistant"));

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"AC_REVIEW",
                                  "event_data":{
                                    "code":"a, b = map(int, input().split())\\nprint(a + b)",
                                    "language":"Python3",
                                    "guidance_level":3,
                                    "behavior_metrics":{
                                      "consecutiveErrors":0,
                                      "submissionCount":4,
                                      "editFrequency":5,
                                      "dwellTime":18,
                                      "deleteRatio":0.1
                                    }
                                  }
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.node_outputs.post_ac.level_2.algorithm_diff").value("优秀解法会先抽出输入处理。"))
                .andExpect(jsonPath("$.data.node_outputs.post_ac.level_3.steps[0].title").value("先抽输入"));
    }

    @Test
    void workflowShouldRejectIllegalTransitionAndInvalidCheckpointRestore() throws Exception {
        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"CODING",
                                  "event_data":{"code":"print(1)"}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Illegal workflow transition: READING -> CODING"));

        jdbcTemplate.update(
                """
                insert into ai_workflow_checkpoint(session_id, checkpoint_id, channel_values, created_at)
                values (?, ?, cast(? as jsonb), now())
                """,
                sessionId,
                "invalid-phase-checkpoint",
                """
                {
                  "phase":"HACKED",
                  "node_outputs":{},
                  "behavior_metrics":{},
                  "pending_human_action":""
                }
                """
        );

        mockMvc.perform(post("/api/ai/workflow/checkpoint/restore")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "session_id":"%s",
                                  "checkpoint_id":"invalid-phase-checkpoint"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Illegal workflow checkpoint restore: READING -> HACKED"));
    }

    @Test
    void workflowAsyncEventShouldDispatchAndPersistResult() throws Exception {
        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"CHAT",
                                  "async":true,
                                  "event_data":{
                                    "message":"请提醒我下一步该做什么",
                                    "behavior_metrics":{
                                      "consecutiveErrors":1,
                                      "submissionCount":2,
                                      "editFrequency":3,
                                      "dwellTime":12,
                                      "deleteRatio":0.15
                                    }
                                  }
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.status").value("dispatched"))
                .andExpect(jsonPath("$.data.session_id").value(sessionId));

        for (int attempt = 0; attempt < 20; attempt++) {
            MvcResult sessionResult = mockMvc.perform(get("/api/ai/workflow/session")
                            .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                            .param("session_id", sessionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();

            JsonNode history = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                    .path("data").path("node_outputs").path("chat").path("history");
            if (history.isArray() && history.size() > 1) {
                assertThat(history.get(0).path("role").asText()).isEqualTo("user");
                assertThat(history.get(1).path("role").asText()).isEqualTo("assistant");
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }

        throw new AssertionError("async workflow result was not persisted in time");
    }
}
