package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionAiTutorLanguageIntegrationTest extends SubmissionIntegrationTestSupport {

    @Test
    void aiTutorProblemShouldAcceptJavaForSubmissionAndDebugEvenWhenStoredLanguagesAreNarrower() throws Exception {
        try (JudgeStubServer judgeStub = JudgeStubServer.start("""
                {"err":null,"data":[{"output":"java-ok\\n","result":0,"cpu_time":7,"memory":20480}]}
                """)) {
            registerJudgeServer("java-ai-tutor-judge", judgeStub.serviceUrl());

            String submissionPayload = """
                    {
                      "problem_id": %d,
                      "language": "Java",
                      "code": "public class Main { public static void main(String[] args) { System.out.println(1); } }"
                    }
                    """.formatted(baseProblemId);

            MvcResult submitResult = mockMvc.perform(post("/api/submission")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(submissionPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();
            String submissionId = String.valueOf(((Map<?, ?>) objectMapper.readValue(
                    submitResult.getResponse().getContentAsString(),
                    Map.class
            ).get("data")).get("submission_id"));
            assertThat(awaitSubmissionResult(submissionId, Duration.ofSeconds(4))).isEqualTo(0);

            mockMvc.perform(post("/api/debug")
                            .with(user("student").roles("USER"))
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "problem_id": %d,
                                      "language": "Java",
                                      "code": "public class Main { public static void main(String[] args) { System.out.println(1); } }",
                                      "input": ""
                                    }
                                    """.formatted(baseProblemId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andExpect(jsonPath("$.data.output").value("java-ok\n"))
                    .andExpect(jsonPath("$.data.error").isEmpty());
        }
    }
}
