package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AITutorWorkflowEvidenceIntegrationTest extends AITutorWorkflowIntegrationTestSupport {

    @Test
    void workflowShouldPersistEvidenceTraceProfileAndCoursewareRetrievalArtifacts() throws Exception {
        long studentId = findUserId("student");
        jdbcTemplate.update(
                """
                insert into ai_courseware_chunk(classroom_id, lesson_id, chapter, kc_id, problem_id, title, content, metadata)
                values (1, 1, '1', 1, 1001, '循环入门', '这道题先读入两个数，再输出它们的和。', cast(? as jsonb))
                """,
                "{\"source\":\"lesson-note\"}"
        );
        jdbcTemplate.update(
                """
                insert into ai_learner_memory(user_id, memory_key, memory_value, confidence, source_problem_id, expires_at, enabled, created_at, updated_at)
                values (?, 'reading_pref', '学生更适合先看输入输出解释', 0.9, 1001, now() + interval '7 day', true, now(), now())
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
                .andExpect(jsonPath("$.data.evidence_pack_summary.problem.problem_id").value(1001))
                .andExpect(jsonPath("$.data.evidence_pack_summary.retrieval.hit_count").value(1))
                .andExpect(jsonPath("$.data.learner_state.calibrated").value(false))
                .andExpect(jsonPath("$.data.learner_state.mastery_by_kc.循环").value(0.3))
                .andExpect(jsonPath("$.data.learner_state.recommended_action_bias.source").value("cold_start"))
                .andExpect(jsonPath("$.data.learner_state.memory_refs[0].memory_key").value("reading_pref"))
                .andExpect(jsonPath("$.data.learner_state.memory_refs[0].memory_type").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("generic"),
                        org.hamcrest.Matchers.is("reading_preference"),
                        org.hamcrest.Matchers.is("error_pattern"),
                        org.hamcrest.Matchers.is("learning_signal")
                )))
                .andExpect(jsonPath("$.data.learner_state.memory_refs[0].memory_value").doesNotExist())
                .andExpect(jsonPath("$.data.tutor_action_decision.recommended_action").value("problem_guide"))
                .andExpect(jsonPath("$.data.tutor_action_decision.confidence").value("high"))
                .andExpect(jsonPath("$.data.trace_grade.schema_pass").value(true))
                .andExpect(jsonPath("$.data.rollout_policy.rollout_mode").value("dark_launch"));

        Integer traceCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_tutor_trace where session_id = ? and event_type = 'READING'",
                Integer.class,
                sessionId
        );
        Integer generationCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_tutor_generation_log where session_id = ? and card_type = 'problem_guide'",
                Integer.class,
                sessionId
        );
        Integer retrievalCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_retrieval_log where session_id = ? and phase = 'READING'",
                Integer.class,
                sessionId
        );
        Integer profileCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_learner_profile_snapshot where session_id = ?",
                Integer.class,
                sessionId
        );
        Integer rolloutCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_rollout_decision where scope_key = ?",
                Integer.class,
                sessionId + ":READING"
        );
        Integer evalDatasetCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_eval_dataset where dataset_name = ?",
                Integer.class,
                "workflow:READING"
        );
        Integer evalRunCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_eval_run where dataset_name = ?",
                Integer.class,
                "workflow:READING"
        );

        assertThat(traceCount).isEqualTo(1);
        assertThat(generationCount).isEqualTo(1);
        assertThat(retrievalCount).isEqualTo(1);
        assertThat(profileCount).isEqualTo(1);
        assertThat(rolloutCount).isEqualTo(1);
        assertThat(evalDatasetCount).isEqualTo(1);
        assertThat(evalRunCount).isEqualTo(1);
    }

    @Test
    void workflowShouldIgnoreDisabledOrExpiredMemoryAndProjectCrossCourseProfile() throws Exception {
        long studentId = findUserId("student");
        jdbcTemplate.update(
                """
                insert into ai_courseware_chunk(classroom_id, lesson_id, chapter, kc_id, problem_id, title, content, metadata)
                values (1, 1, '1', 1, 1001, '跨课画像导读', '先看输入输出再开始。', cast(? as jsonb))
                """,
                "{\"source\":\"cross-course\"}"
        );
        jdbcTemplate.update(
                """
                insert into ai_learner_memory(user_id, memory_key, memory_value, confidence, source_problem_id, expires_at, enabled, created_at, updated_at)
                values
                (?, 'reading_disabled', '不应返回', 0.9, 1001, now() + interval '7 day', false, now(), now()),
                (?, 'reading_expired', '不应返回', 0.8, 1001, now() - interval '1 day', true, now(), now())
                """,
                studentId,
                studentId
        );
        jdbcTemplate.update(
                """
                insert into ai_learner_profile_snapshot(
                    user_id, problem_id, session_id, mastery_by_kc, weak_kcs,
                    misconception_distribution, recent_behavior, frustration_level,
                    confidence_proxy, recommended_action_bias, memory_refs, created_at
                )
                values
                (?, 9001, 'history-a', '{"循环":0.2}'::jsonb, '["循环","边界条件"]'::jsonb, '{}'::jsonb, '{}'::jsonb, 'high', 'low', '{}'::jsonb, '[]'::jsonb, now() - interval '2 day'),
                (?, 9002, 'history-b', '{"循环":0.3}'::jsonb, '["循环"]'::jsonb, '{}'::jsonb, '{}'::jsonb, 'high', 'medium', '{}'::jsonb, '[]'::jsonb, now() - interval '1 day')
                """,
                studentId,
                studentId
        );

        String sessionId = createWorkflowSession();

        MvcResult result = mockMvc.perform(post("/api/ai/workflow/event")
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
                .andExpect(jsonPath("$.data.learner_state.memory_refs").isArray())
                .andExpect(jsonPath("$.data.learner_state.recommended_action_bias.source").value("profile_snapshot"))
                .andExpect(jsonPath("$.data.learner_state.recommended_action_bias.dominant_frustration_level").value("high"))
                .andExpect(jsonPath("$.data.learner_state.recommended_action_bias.prior_snapshot_count").value(2))
                .andExpect(jsonPath("$.data.learner_state.recommended_action_bias.preferred_action").value("ac_review"))
                .andExpect(jsonPath("$.data.learner_state.recommended_action_bias.cross_course_weak_kcs[0]").value("循环"))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                Map.class
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> learnerState = (Map<String, Object>) data.get("learner_state");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> memoryRefs = (java.util.List<Map<String, Object>>) learnerState.get("memory_refs");
        assertThat(memoryRefs).allSatisfy(ref -> {
            Object key = ref.get("memory_key");
            assertThat(key).isNotEqualTo("reading_disabled");
            assertThat(key).isNotEqualTo("reading_expired");
        });
    }

    @Test
    void learningEventsBatchShouldProjectAgentFeedbackIntoFeedbackLabelTable() throws Exception {
        String sessionId = createWorkflowSession();

        mockMvc.perform(post("/api/ai/learning-events/batch")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "events":[
                                    {
                                      "problem_id":1001,
                                      "session_id":"%s",
                                      "event_type":"agent_feedback",
                                      "extra_data":{
                                        "agent_id":1,
                                        "card_type":"problem_guide",
                                        "feedback":"helpful",
                                        "workflow_event_id":"trace-001"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.created").value(1));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                select session_id, card_type, feedback_label, agent_id, workflow_event_id
                from ai_feedback_label
                where session_id = ?
                """,
                sessionId
        );

        assertThat(stored.get("session_id")).isEqualTo(sessionId);
        assertThat(stored.get("card_type")).isEqualTo("problem_guide");
        assertThat(stored.get("feedback_label")).isEqualTo("helpful");
        assertThat(stored.get("agent_id")).isEqualTo(1);
        assertThat(stored.get("workflow_event_id")).isEqualTo("trace-001");
    }

    @Test
    void workflowShouldProjectStructuredMemoryAndExposeOrchestrationContext() throws Exception {
        long studentId = findUserId("student");
        jdbcTemplate.update(
                """
                insert into ai_learner_notebook(
                    id, user_id, problem_id, language, error_taxonomy, root_cause,
                    fix_outcome, student_reflection, tags, evidence_ptr, is_deleted, create_time, update_time
                ) values (
                    'nb-structured-1', ?, 1001, 'Python3', 'runtime_error', 'IndexError: list index out of range',
                    '修复了下标边界', '以后先检查 len(nums) 和索引范围', '["runtime","index"]'::jsonb, '{}'::jsonb, false, now(), now()
                )
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
                .andExpect(jsonPath("$.data.learner_state.memory_refs[0].memory_type").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("error_pattern"),
                        org.hamcrest.Matchers.is("learning_signal"),
                        org.hamcrest.Matchers.is("reading_preference")
                )))
                .andExpect(jsonPath("$.data.learner_state.memory_refs[0].memory_summary").isNotEmpty())
                .andExpect(jsonPath("$.data.orchestration_context.current_event").value("READING"))
                .andExpect(jsonPath("$.data.evidence_pack_summary.orchestration.current_event").value("READING"));
    }

    @Test
    void workflowShouldInjectSimilarErrorEvidenceAndExecutionTraceIntoWorkflowResponse() throws Exception {
        long studentId = findUserId("student");
        jdbcTemplate.update(
                """
                insert into ai_learner_notebook(
                    id, user_id, problem_id, language, error_taxonomy, root_cause,
                    fix_outcome, student_reflection, tags, evidence_ptr, is_deleted, create_time, update_time
                ) values (
                    'nb-runtime-hit', ?, 2001, 'Python3', 'runtime_error', 'IndexError: list index out of range',
                    '增加边界判断', '之前也是列表越界，应该先检查下标范围', '["runtime","boundary"]'::jsonb, '{}'::jsonb, false, now(), now()
                )
                """,
                studentId
        );
        jdbcTemplate.update(
                """
                insert into submission(id, problem_id, create_time, user_id, username, code, result, info, language, shared, statistic_info, ip)
                values (
                    'sub-runtime-1', 1001, now(), ?, 'student',
                    'nums = [1, 2]\\nprint(nums[2])', 4, '{}'::jsonb, 'Python3', false,
                    '{"err_info":"Traceback: IndexError: list index out of range"}'::jsonb, '127.0.0.1'
                )
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
                                  "event":"ERROR_FEEDBACK",
                                  "event_data":{
                                    "submission_id":"sub-runtime-1",
                                    "request_execution_trace":true
                                  }
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.node_outputs.error_diagnosis.root_cause").value(org.hamcrest.Matchers.containsString("错误样例")))
                .andExpect(jsonPath("$.data.node_outputs.error_diagnosis.what_program_is_doing").isNotEmpty())
                .andExpect(jsonPath("$.data.node_outputs.error_diagnosis.expected_behavior").isNotEmpty())
                .andExpect(jsonPath("$.data.node_outputs.error_diagnosis.fix_direction").isNotEmpty())
                .andExpect(jsonPath("$.data.node_outputs.error_diagnosis.repeat_pattern_detected").value(true))
                .andExpect(jsonPath("$.data.node_outputs.error_diagnosis.similar_error_summary").isNotEmpty())
                .andExpect(jsonPath("$.data.node_outputs.error_diagnosis.similar_error_refs[0].source_kind").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("notebook"),
                        org.hamcrest.Matchers.is("memory")
                )))
                .andExpect(jsonPath("$.data.node_outputs.execution_trace_explainer.status").value("ready"))
                .andExpect(jsonPath("$.data.execution_trace[1].message_type").value("execution_trace_explainer"));

        Integer similarRetrievalCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from ai_retrieval_log
                where session_id = ?
                  and phase = 'ERROR_FEEDBACK'
                  and source_type in ('similar_notebook', 'similar_memory')
                """,
                Integer.class,
                sessionId
        );
        assertThat(similarRetrievalCount).isNotNull();
        assertThat(similarRetrievalCount).isGreaterThan(0);
    }
}
