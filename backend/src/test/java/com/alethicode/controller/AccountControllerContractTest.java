package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerContractTest extends AbstractControllerContractTest {

    @Test
    void loginShouldReturnWrappedResponse() throws Exception {
        when(accountAuthDomainService.login(any(), any())).thenReturn(ApiResponse.success("Succeeded"));

        mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"student\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data").value("Succeeded"));
    }

    @Test
    void profileShouldReturnWrappedResponse() throws Exception {
        when(accountProfileDomainService.getProfile(any(), any())).thenReturn(ApiResponse.success(Map.of("username", "student")));

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.username").value("student"));
    }
}
