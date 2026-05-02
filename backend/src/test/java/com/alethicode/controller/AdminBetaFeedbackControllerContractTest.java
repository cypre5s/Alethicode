package com.alethicode.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBetaFeedbackControllerContractTest extends AbstractControllerContractTest {

    @Test
    @WithMockUser(username = "student", roles = {"USER"})
    void nonAdminGets403() throws Exception {
        mockMvc.perform(get("/api/admin/beta/feedback-reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminListsWithFilter() throws Exception {
        when(adminBetaFeedbackService.listReports(eq(0), eq(20), eq("pending"), eq(null), eq(null)))
                .thenReturn(Map.of(
                        "items", List.of(Map.of("id", 1, "type", "button_dead")),
                        "total", 1,
                        "offset", 0,
                        "limit", 20
                ));

        mockMvc.perform(get("/api/admin/beta/feedback-reports")
                        .param("status", "pending")
                        .param("offset", "0")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminGetsDetail() throws Exception {
        when(adminBetaFeedbackService.getReport(7L)).thenReturn(Map.of(
                "id", 7,
                "description", "测试详情",
                "attachments", List.of()
        ));

        mockMvc.perform(get("/api/admin/beta/feedback-reports/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.attachments").isArray());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminUpdateStatusSetsResolvedAt() throws Exception {
        mockMvc.perform(patch("/api/admin/beta/feedback-reports/7")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());

        verify(adminBetaFeedbackService).updateStatus(eq(7L), eq("resolved"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminScreenshotReturnsImage() throws Exception {
        byte[] body = new byte[]{1, 2, 3, 4};
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline().filename("a.png").build());
        headers.setContentLength(body.length);
        when(adminBetaFeedbackService.streamScreenshot(eq(3L), eq(11L)))
                .thenReturn(new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK));

        mockMvc.perform(get("/api/admin/beta/feedback-reports/3/screenshots/11"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }
}
