package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnouncementControllerContractTest extends AbstractControllerContractTest {

    @Test
    void publicAnnouncementsShouldReturnList() throws Exception {
        when(announcementService.listPublic(any())).thenReturn(ApiResponse.success(Map.of("results", List.of(), "total", 0)));

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.results").isArray());
    }

    @Test
    void adminCreateAnnouncementShouldRequireAdminRole() throws Exception {
        when(announcementService.create(any(), any())).thenReturn(ApiResponse.success(Map.of("id", 1, "title", "hello")));

        mockMvc.perform(post("/api/admin/announcements")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"hello\",\"content\":\"world\",\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
