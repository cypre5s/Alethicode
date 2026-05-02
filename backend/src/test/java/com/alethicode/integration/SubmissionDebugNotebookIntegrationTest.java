package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionDebugNotebookIntegrationTest extends SubmissionIntegrationTestSupport {

    @Test
    void codingSubmissionShouldRefreshProblemListStatisticsAndMyStatus() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"test_case":1,"result":0,"cpu_time":9,"memory":10240}]}
                """)) {
            registerJudgeServer("problem-list-judge", judgeStub.serviceUrl());

            String codingPayload = """
                    {
                      "problem_id": %d,
                      "language": "Python3",
                      "code": "print(1)"
                    }
                    """.formatted(wrongProblemId);

            MvcResult submitResult = mockMvc.perform(post("/api/submission")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(codingPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();
            String submissionId = String.valueOf(((Map<?, ?>) objectMapper.readValue(
                    submitResult.getResponse().getContentAsString(),
                    Map.class
            ).get("data")).get("submission_id"));
            assertThat(awaitSubmissionResult(submissionId, Duration.ofSeconds(4))).isEqualTo(0);

            Integer submissionNumber = jdbcTemplate.queryForObject(
                    "select submission_number from problem where id = ?",
                    Integer.class,
                    wrongProblemId
            );
            Integer acceptedNumber = jdbcTemplate.queryForObject(
                    "select accepted_number from problem where id = ?",
                    Integer.class,
                    wrongProblemId
            );
            assertThat(submissionNumber).isEqualTo(1);
            assertThat(acceptedNumber).isEqualTo(1);

            Map<String, Object> wrongProblem = problemListRow("student", "P-W1");
            assertThat(wrongProblem.get("my_status")).isEqualTo(0);
            assertThat(wrongProblem.get("submission_number")).isEqualTo(1);
            assertThat(wrongProblem.get("accepted_number")).isEqualTo(1);
        }
    }

    @Test
    void objectiveSubmissionShouldRefreshProblemListStatisticsAndMyStatus() throws Exception {
        String payload = """
                {
                  "problem_id": %d,
                  "language": "Python3",
                  "objective_answer": "A"
                }
                """.formatted(objectiveProblemId);

        mockMvc.perform(post("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        Integer submissionNumber = jdbcTemplate.queryForObject(
                "select submission_number from problem where id = ?",
                Integer.class,
                objectiveProblemId
        );
        Integer acceptedNumber = jdbcTemplate.queryForObject(
                "select accepted_number from problem where id = ?",
                Integer.class,
                objectiveProblemId
        );
        assertThat(submissionNumber).isEqualTo(1);
        assertThat(acceptedNumber).isEqualTo(1);

        Map<String, Object> objectiveProblem = problemListRow("student", "P-OBJ");
        assertThat(objectiveProblem.get("my_status")).isEqualTo(0);
        assertThat(objectiveProblem.get("submission_number")).isEqualTo(1);
        assertThat(objectiveProblem.get("accepted_number")).isEqualTo(1);
    }

    @Test
    void nonAcSubmissionShouldAutoCreateNotebookEntryAndDeduplicate() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"test_case":1,"result":-1,"cpu_time":8,"memory":1024,"error":"wrong answer on case 1"}]}
                """)) {
            registerJudgeServer("notebook-judge", judgeStub.serviceUrl());

            String codingPayload = """
                    {
                      "problem_id": %d,
                      "language": "Python3",
                      "code": "print(1)"
                    }
                    """.formatted(baseProblemId);

            MvcResult first = mockMvc.perform(post("/api/submission")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(codingPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();
            String firstSubmissionId = String.valueOf(((Map<?, ?>) objectMapper.readValue(
                    first.getResponse().getContentAsString(),
                    Map.class
            ).get("data")).get("submission_id"));
            assertThat(awaitSubmissionResult(firstSubmissionId, Duration.ofSeconds(4))).isEqualTo(-1);

            Integer firstNotebookCount = jdbcTemplate.queryForObject(
                    "select count(*) from ai_learner_notebook where user_id = ? and problem_id = ? and error_taxonomy = 'logic_error' and is_deleted = false",
                    Integer.class,
                    studentId,
                    baseProblemId
            );
            assertThat(firstNotebookCount).isEqualTo(1);

            MvcResult second = mockMvc.perform(post("/api/submission")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(codingPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();
            String secondSubmissionId = String.valueOf(((Map<?, ?>) objectMapper.readValue(
                    second.getResponse().getContentAsString(),
                    Map.class
            ).get("data")).get("submission_id"));
            assertThat(awaitSubmissionResult(secondSubmissionId, Duration.ofSeconds(4))).isEqualTo(-1);

            Integer dedupedCount = jdbcTemplate.queryForObject(
                    "select count(*) from ai_learner_notebook where user_id = ? and problem_id = ? and error_taxonomy = 'logic_error' and is_deleted = false",
                    Integer.class,
                    studentId,
                    baseProblemId
            );
            assertThat(dedupedCount).isEqualTo(1);
        }
    }

    @Test
    void debugShouldReturnNoAvailableJudgeServerWhenMissing() throws Exception {
        mockMvc.perform(post("/api/debug")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "language": "Python3",
                                  "code": "print(1)",
                                  "input": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("暂无可用的评测服务器"));
    }

    @Test
    void debugShouldCallJudgeServerAndReturnOutput() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"output":"3\\n","result":0,"cpu_time":7,"memory":20480}]}
                """)) {
            registerJudgeServer("debug-judge", judgeStub.serviceUrl());

            mockMvc.perform(post("/api/debug")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "language": "Python3",
                                      "code": "print(1+2)",
                                      "input": ""
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andExpect(jsonPath("$.data.output").value("3\n"))
                    .andExpect(jsonPath("$.data.error").isEmpty())
                    .andExpect(jsonPath("$.data.time_cost").value(7))
                    .andExpect(jsonPath("$.data.memory_cost").value(20));
        }
    }

    @Test
    void debugShouldApplyUserThrottling() throws Exception {
        upsertThrottling("""
                {
                  "ip": {"capacity": 100, "fill_rate": 0.1, "default_capacity": 100},
                  "user": {"capacity": 1, "fill_rate": 0.01, "default_capacity": 1}
                }
                """);

        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"output":"ok\\n","result":0,"cpu_time":7,"memory":20480}]}
                """)) {
            registerJudgeServer("debug-throttle-judge", judgeStub.serviceUrl());

            String debugPayload = """
                    {
                      "language": "Python3",
                      "code": "print(1)",
                      "input": ""
                    }
                    """;

            mockMvc.perform(post("/api/debug")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(debugPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty());

            mockMvc.perform(post("/api/debug")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(debugPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value("error"))
                    .andExpect(jsonPath("$.data", startsWith("Please wait")));
        }
    }
}
