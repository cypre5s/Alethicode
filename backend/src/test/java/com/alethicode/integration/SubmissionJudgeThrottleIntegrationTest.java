package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionJudgeThrottleIntegrationTest extends SubmissionIntegrationTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    void recentWrongStatusFilterAndRejudgeShouldWork() throws Exception {
        mockMvc.perform(get("/api/submissions/recent-wrong")
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.items[0].problem_key").value("P-W1"));

        mockMvc.perform(get("/api/submissions/recent-wrong")
                        .param("user_id", String.valueOf(teacherId))
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Permission denied"));

        mockMvc.perform(get("/api/judge/review-queue")
                        .param("page", "1")
                        .param("page_size", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/judge/review-queue/sub-review")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"verdict\":\"accepted\"}"))
                .andExpect(status().isNotFound());

        MvcResult listResult = mockMvc.perform(get("/api/submissions")
                        .param("limit", "20")
                        .param("offset", "0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn();
        Map<String, Object> listPayload = objectMapper.readValue(listResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> listData = (Map<String, Object>) listPayload.get("data");
        List<Map<String, Object>> listItems = (List<Map<String, Object>>) listData.get("results");
        assertThat(listItems).extracting(item -> String.valueOf(item.get("id"))).doesNotContain("sub-review");

        mockMvc.perform(get("/api/submission")
                        .param("id", "sub-review")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Submission doesn't exist"));

        mockMvc.perform(get("/api/submission/rejudge")
                        .param("id", "sub-own")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());
        assertThat(awaitSubmissionResult("sub-own", Duration.ofSeconds(4))).isEqualTo(5);

        mockMvc.perform(get("/api/submission/rejudge")
                        .param("id", "sub-own")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("permission-denied"))
                .andExpect(jsonPath("$.data").value("请先登录"));
    }

    @Test
    void createSubmissionShouldSupportObjectiveAndJudgeAvailabilityCheck() throws Exception {
        String objectivePayload = """
                {
                  "problem_id": %d,
                  "language": "Python3",
                  "objective_answer": "a"
                }
                """.formatted(objectiveProblemId);

        mockMvc.perform(post("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectivePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.submission_id", not(emptyString())));

        String codingPayload = """
                {
                  "problem_id": %d,
                  "language": "Python3",
                  "code": "print(1)"
                }
                """.formatted(baseProblemId);

        mockMvc.perform(post("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(codingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("No available judge server. Please start oj-judge service or wait for heartbeat recovery."));

        registerJudgeServer("judge-1", "http://127.0.0.1:12345");

        MvcResult unavailableJudgeResult = mockMvc.perform(post("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(codingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.submission_id", not(emptyString())))
                .andReturn();

        String unavailableJudgeSubmissionId = objectMapper.readTree(
                unavailableJudgeResult.getResponse().getContentAsString()
        ).path("data").path("submission_id").asText();
        assertThat(awaitSubmissionResult(unavailableJudgeSubmissionId, Duration.ofSeconds(4))).isEqualTo(5);
    }

    @Test
    void createSubmissionShouldFailFastWhenLanguageIsMissing() throws Exception {
        String objectivePayloadWithoutLanguage = """
                {
                  "problem_id": %d,
                  "objective_answer": "a"
                }
                """.formatted(objectiveProblemId);

        mockMvc.perform(post("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectivePayloadWithoutLanguage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("请选择编程语言"));
    }

    @Test
    void createSubmissionShouldApplyUserThrottling() throws Exception {
        upsertThrottling("""
                {
                  "ip": {"capacity": 100, "fill_rate": 0.1, "default_capacity": 100},
                  "user": {"capacity": 1, "fill_rate": 0.01, "default_capacity": 1}
                }
                """);

        String objectivePayload = """
                {
                  "problem_id": %d,
                  "language": "Python3",
                  "objective_answer": "a"
                }
                """.formatted(objectiveProblemId);

        mockMvc.perform(post("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectivePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        mockMvc.perform(post("/api/submission")
                        .with(user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectivePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data", startsWith("Please wait")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void createSubmissionShouldDispatchJudgeAndPersistResult() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"test_case":1,"result":0,"cpu_time":9,"memory":10240}]}
                """)) {
            registerJudgeServer("dispatch-judge", judgeStub.serviceUrl());

            String codingPayload = """
                    {
                      "problem_id": %d,
                      "language": "Python3",
                      "code": "print(1)"
                    }
                    """.formatted(baseProblemId);

            MvcResult result = mockMvc.perform(post("/api/submission")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(codingPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();

            Map<String, Object> payload = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String submissionId = String.valueOf(data.get("submission_id"));
            int finalResult = awaitSubmissionResult(submissionId, Duration.ofSeconds(4));
            assertThat(finalResult).isEqualTo(0);

            String statisticInfo = jdbcTemplate.queryForObject(
                    "select statistic_info::text from submission where id = ?",
                    String.class,
                    submissionId
            );
            Map<String, Object> stat = objectMapper.readValue(statisticInfo, Map.class);
            assertThat(stat.get("time_cost")).isEqualTo(9);
            assertThat(stat.get("memory_cost")).isEqualTo(10240);
            Map<String, Object> quality = waitForCodeQuality(submissionId, Duration.ofSeconds(4));
            assertThat(quality.get("readability")).isEqualTo(4);
            assertThat(quality.get("efficiency")).isEqualTo(4);
            assertThat(quality.get("style")).isEqualTo(3);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void createSubmissionShouldDispatchCodeQualityForCppSubmission() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"test_case":1,"result":0,"cpu_time":12,"memory":12288}]}
                """)) {
            registerJudgeServer("dispatch-cpp-judge", judgeStub.serviceUrl());

            String cppPayload = """
                    {
                      "problem_id": %d,
                      "language": "C++",
                      "code": "#include <iostream>\\nint main(){std::cout<<1;return 0;}"
                    }
                    """.formatted(baseProblemId);

            MvcResult result = mockMvc.perform(post("/api/submission")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(cppPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();

            Map<String, Object> payload = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String submissionId = String.valueOf(data.get("submission_id"));
            int finalResult = awaitSubmissionResult(submissionId, Duration.ofSeconds(4));
            assertThat(finalResult).isEqualTo(0);

            Map<String, Object> quality = waitForCodeQuality(submissionId, Duration.ofSeconds(4));
            Map<String, Object> statisticInfo = awaitSubmissionStatisticInfo(submissionId, Duration.ofSeconds(1));
            assertThat(statisticInfo.get("code_quality_status")).isEqualTo("ready");
            assertThat(quality.get("readability")).isEqualTo(4);
            assertThat(quality.get("efficiency")).isEqualTo(4);
            assertThat(quality.get("style")).isEqualTo(3);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void createSubmissionShouldPersistPartialScoreForNonAcResult() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[
                  {"test_case":1,"result":0,"cpu_time":9,"memory":10240},
                  {"test_case":2,"result":-1,"cpu_time":10,"memory":10240,"error":"wrong answer"}
                ]}
                """)) {
            registerJudgeServer("partial-judge", judgeStub.serviceUrl());

            String codingPayload = """
                    {
                      "problem_id": %d,
                      "language": "Python3",
                      "code": "print(1)"
                    }
                    """.formatted(baseProblemId);

            MvcResult result = mockMvc.perform(post("/api/submission")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(codingPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();

            Map<String, Object> payload = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String submissionId = String.valueOf(data.get("submission_id"));
            int finalResult = awaitSubmissionResult(submissionId, Duration.ofSeconds(4));
            assertThat(finalResult).isEqualTo(-1);

            Map<String, Object> statisticInfo = awaitSubmissionStatisticInfo(submissionId, Duration.ofSeconds(4));
            assertThat(statisticInfo.get("partial_score")).isEqualTo(50);
            assertThat(statisticInfo.get("passed_test_case_count")).isEqualTo(1);
            assertThat(statisticInfo.get("total_test_case_count")).isEqualTo(2);
        }
    }

    @Test
    void rejudgeShouldDispatchJudgeAndRefreshResult() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"test_case":1,"result":0,"cpu_time":13,"memory":4096}]}
                """)) {
            registerJudgeServer("rejudge-judge", judgeStub.serviceUrl());

            mockMvc.perform(get("/api/submission/rejudge")
                            .param("id", "sub-own")
                            .with(user("root").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty());

            int refreshed = awaitSubmissionResult("sub-own", Duration.ofSeconds(4));
            assertThat(refreshed).isEqualTo(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> waitForCodeQuality(String submissionId, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> statisticInfo = awaitSubmissionStatisticInfo(submissionId, Duration.ofSeconds(1));
            Object quality = statisticInfo.get("code_quality");
            if (quality instanceof Map<?, ?> qualityMap && !qualityMap.isEmpty()) {
                return (Map<String, Object>) qualityMap;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("code_quality not ready for submission " + submissionId);
    }
}
