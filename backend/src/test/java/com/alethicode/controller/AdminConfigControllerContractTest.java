package com.alethicode.controller;

import com.alethicode.dto.response.AiProviderValidationCaseResult;
import com.alethicode.dto.response.AiProviderValidationRunResponse;
import com.alethicode.service.ai.AiProviderValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminConfigControllerContractTest extends AbstractControllerContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiProviderValidationService validationService;

    private static final String ENDPOINT = "/api/admin/super/ai-config/validation-runs";

    @Test
    void adminCanCreateValidationRun() throws Exception {
        when(validationService.createValidationRun(any())).thenReturn(passedResponse());

        String body = objectMapper.writeValueAsString(Map.of(
                "profile_prefix", "",
                "include_json", true,
                "include_content", false,
                "include_embedding", false,
                "include_tool_loop", false
        ));

        mockMvc.perform(post(ENDPOINT)
                        .with(user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.run_id").isNotEmpty());
    }

    @Test
    void teacherRoleCannotAccessValidationRun() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "profile_prefix", "",
                "include_json", true,
                "include_content", false,
                "include_embedding", false,
                "include_tool_loop", false
        ));

        mockMvc.perform(post(ENDPOINT)
                        .with(user("teacher").roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotAccessValidationRun() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "profile_prefix", "",
                "include_json", true,
                "include_content", false,
                "include_embedding", false,
                "include_tool_loop", false
        ));

        mockMvc.perform(post(ENDPOINT)
                        .with(user("alice").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessValidationRun() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "profile_prefix", "",
                "include_json", true,
                "include_content", false,
                "include_embedding", false,
                "include_tool_loop", false
        ));
        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void responseShouldNotExposePromptOrApiKeyFields() throws Exception {
        when(validationService.createValidationRun(any())).thenReturn(passedResponse());

        String body = objectMapper.writeValueAsString(Map.of(
                "profile_prefix", "",
                "include_json", true,
                "include_content", true,
                "include_embedding", false,
                "include_tool_loop", false
        ));

        mockMvc.perform(post(ENDPOINT)
                        .with(user("root").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[*].prompt").doesNotExist())
                .andExpect(jsonPath("$.data.cases[*].completion").doesNotExist())
                .andExpect(jsonPath("$.data.cases[*].api_key").doesNotExist())
                .andExpect(jsonPath("$.data.cases[*].apiKey").doesNotExist());
    }

    private AiProviderValidationRunResponse passedResponse() {
        List<AiProviderValidationCaseResult> cases = List.of(
                new AiProviderValidationCaseResult("json", true, true, null,
                        Map.of("keyCount", 3)));
        return new AiProviderValidationRunResponse(
                "run-1234", "", true, cases,
                Map.of("totalCases", 1, "passedCases", 1L));
    }
}
