package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AITutorWorkflowGovernanceIntegrationTest extends AITutorWorkflowIntegrationTestSupport {

    @Test
    void workflowShouldPromoteGrayRolloutWhenHistoricalOpeSupportsBandit() throws Exception {
        for (int i = 0; i < 6; i++) {
            jdbcTemplate.update(
                    """
                    insert into ai_tutor_trace(
                        session_id, phase, event_type, trace_status, evidence_summary,
                        learner_state, decision, guardrail, schema_validation, created_at
                    )
                    values (?, 'READING', 'READING', 'ok', '{}'::jsonb, '{}'::jsonb, cast(? as jsonb), '{"passed":true}'::jsonb, '{"schema_pass":true}'::jsonb, now() - (? || ' minute')::interval)
                    """,
                    "history-" + i,
                    """
                    {
                      "logged_action":"problem_guide",
                      "propensity":0.5,
                      "reward":0.88
                    }
                    """,
                    String.valueOf(i + 1)
            );
        }

        long studentId = findUserId("student");
        jdbcTemplate.update(
                """
                insert into ai_courseware_chunk(classroom_id, lesson_id, chapter, kc_id, problem_id, title, content, metadata)
                values (1, 1, '1', 1, 1001, '历史导读', '先看输入输出再开始。', cast(? as jsonb))
                """,
                "{\"source\":\"history\"}"
        );
        jdbcTemplate.update(
                """
                insert into ai_learner_memory(user_id, memory_key, memory_value, confidence, source_problem_id, expires_at, enabled, created_at, updated_at)
                values (?, 'reading_pref_history', '历史上更适合先看导读', 0.8, 1001, now() + interval '3 day', true, now(), now())
                """,
                studentId
        );

        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"READING",
                                  "event_data":{}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.rollout_policy.rollout_mode").value("gray"))
                .andExpect(jsonPath("$.data.rollout_policy.metrics.ope_eligible").value(true))
                .andExpect(jsonPath("$.data.rollout_policy.metrics.ope_score").value(org.hamcrest.Matchers.greaterThan(0.8)));
    }

    @Test
    void workflowShouldStayInBaselineWhenBanditIsDisabled() throws Exception {
        when(aiModelGateway.readConfigOrDefault(eq("AI_BANDIT_ENABLED"), eq("true"))).thenReturn("false");
        jdbcTemplate.update(
                """
                insert into ai_courseware_chunk(classroom_id, lesson_id, chapter, kc_id, problem_id, title, content, metadata)
                values (1, 1, '1', 1, 1001, '基线导读', '先看输入输出再开始。', cast(? as jsonb))
                """,
                "{\"source\":\"baseline\"}"
        );

        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"READING",
                                  "event_data":{}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.rollout_policy.rollout_mode").value("baseline"))
                .andExpect(jsonPath("$.data.rollout_policy.reason").value(org.hamcrest.Matchers.containsString("bandit disabled")));
    }

    @Test
    void workflowShouldKeepDarkLaunchWhenOpeSampleIsInsufficient() throws Exception {
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.update(
                    """
                    insert into ai_tutor_trace(
                        session_id, phase, event_type, trace_status, evidence_summary,
                        learner_state, decision, guardrail, schema_validation, created_at
                    )
                    values (?, 'READING', 'READING', 'ok', '{}'::jsonb, '{}'::jsonb, cast(? as jsonb), '{"passed":true}'::jsonb, '{"schema_pass":true}'::jsonb, now() - (? || ' minute')::interval)
                    """,
                    "few-history-" + i,
                    """
                    {
                      "logged_action":"problem_guide",
                      "propensity":0.5,
                      "reward":0.9
                    }
                    """,
                    String.valueOf(i + 1)
            );
        }
        jdbcTemplate.update(
                """
                insert into ai_courseware_chunk(classroom_id, lesson_id, chapter, kc_id, problem_id, title, content, metadata)
                values (1, 1, '1', 1, 1001, '暗发导读', '先看输入输出再开始。', cast(? as jsonb))
                """,
                "{\"source\":\"dark-launch\"}"
        );

        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"READING",
                                  "event_data":{}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.rollout_policy.rollout_mode").value("dark_launch"))
                .andExpect(jsonPath("$.data.rollout_policy.reason").value(org.hamcrest.Matchers.containsString("ope not ready")));
    }

    @Test
    void workflowShouldRollbackWhenAnswerLeakRiskIsTriggered() throws Exception {
        String sessionId = createWorkflowSession();

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
                .andExpect(jsonPath("$.data.guardrail_result.passed").value(false))
                .andExpect(jsonPath("$.data.trace_grade.answer_leak").value(1.0))
                .andExpect(jsonPath("$.data.rollout_policy.rollout_mode").value("rollback"));
    }

    @Test
    void workflowShouldPersistSchemaViolationTraceAndFailFast() throws Exception {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "plain_task", "只返回一个字段，故意制造 schema 错误"
        ));

        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/workflow/event")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "problem_id":1001,
                                  "session_id":"%s",
                                  "event":"READING",
                                  "event_data":{}
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("problem_explanation is required"));

        Integer violationCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from ai_tutor_trace
                where session_id = ?
                  and trace_status = 'schema_violation'
                  and schema_validation->>'schema_pass' = 'false'
                """,
                Integer.class,
                sessionId
        );
        String schemaError = jdbcTemplate.queryForObject(
                """
                select schema_validation->>'error'
                from ai_tutor_trace
                where session_id = ?
                  and trace_status = 'schema_violation'
                order by created_at desc
                limit 1
                """,
                String.class,
                sessionId
        );
        Integer sessionEventCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_workflow_event where session_id = ?",
                Integer.class,
                sessionId
        );

        org.assertj.core.api.Assertions.assertThat(violationCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(schemaError).contains("problem_explanation is required");
        org.assertj.core.api.Assertions.assertThat(sessionEventCount).isEqualTo(0);
    }
}
