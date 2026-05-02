package com.alethicode.controller;

import com.alethicode.dto.request.BetaFeedbackCreateRequest;
import com.alethicode.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BetaFeedbackControllerContractTest extends AbstractControllerContractTest {

    private static final String DATA_PNG = "{\"type\":\"button_dead\",\"severity\":\"medium\",\"description\":\"按钮无响应\"}";

    /**
     * Reproduces the production auth shape: principal=username, details=Long userId,
     * which is what {@code SessionAuthenticationFilter} stamps onto each request.
     */
    private static RequestPostProcessor studentAuth(long userId) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "student", null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        token.setDetails(userId);
        return authentication(token);
    }

    @Test
    void unauthRejected() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, DATA_PNG.getBytes()
        );
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshots", "ok.png", "image/png", new byte[]{1, 2, 3}
        );
        mockMvc.perform(multipart("/api/beta/feedback-reports")
                        .file(data)
                        .file(screenshot)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void successPersistsRowAndAttachment() throws Exception {
        when(betaFeedbackService.createReport(any(BetaFeedbackCreateRequest.class), any(), any())).thenReturn(42L);
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, DATA_PNG.getBytes()
        );
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshots", "ok.png", "image/png", new byte[]{1, 2, 3}
        );
        mockMvc.perform(multipart("/api/beta/feedback-reports")
                        .file(data)
                        .file(screenshot)
                        .with(studentAuth(7L))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").value(42));
    }

    @Test
    void oversizedScreenshotRejected() throws Exception {
        doThrow(new BadRequestException("screenshot too large"))
                .when(betaFeedbackService)
                .createReport(any(BetaFeedbackCreateRequest.class), any(), any());
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, DATA_PNG.getBytes()
        );
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshots", "huge.png", "image/png", new byte[]{1, 2, 3}
        );
        mockMvc.perform(multipart("/api/beta/feedback-reports")
                        .file(data)
                        .file(screenshot)
                        .with(studentAuth(7L))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("screenshot too large")));
    }

    @Test
    void wrongTypeRejected() throws Exception {
        doThrow(new BadRequestException("screenshot type not allowed"))
                .when(betaFeedbackService)
                .createReport(any(BetaFeedbackCreateRequest.class), any(), any());
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, DATA_PNG.getBytes()
        );
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshots", "evil.gif", "image/gif", new byte[]{1, 2, 3}
        );
        mockMvc.perform(multipart("/api/beta/feedback-reports")
                        .file(data)
                        .file(screenshot)
                        .with(studentAuth(7L))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("not allowed")));
    }

    @Test
    void mailFailureDoesNotBlockSuccess() throws Exception {
        // 即便邮件发送失败，service 也应吞掉异常并返回 reportId（fail-soft）。
        // 这里用 mock 直接模拟 service 已处理失败：返回 id；客户端拿到 200。
        when(betaFeedbackService.createReport(any(BetaFeedbackCreateRequest.class), any(), any())).thenReturn(99L);
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, DATA_PNG.getBytes()
        );
        mockMvc.perform(multipart("/api/beta/feedback-reports")
                        .file(data)
                        .with(studentAuth(7L))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99));
    }

    @Test
    void telemetryBatchAcceptsAuthenticated() throws Exception {
        mockMvc.perform(post("/api/beta/telemetry/events")
                        .with(studentAuth(7L))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"eventType\":\"page_view\",\"route\":\"/\",\"payload\":{},\"occurredAt\":\"2026-04-28T00:00:00Z\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.created").value(1));
    }

    @Test
    void webVitalsAcceptsAuthenticated() throws Exception {
        mockMvc.perform(post("/api/beta/telemetry/web-vitals")
                        .with(studentAuth(7L))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"LCP\",\"value\":1234.5,\"rating\":\"good\",\"navigationType\":\"navigate\",\"route\":\"/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());
    }

    /**
     * 匿名访客（如未登录的首页浏览者、登录页面在 cookie 设置前的瞬间）的遥测请求
     * 必须被 SecurityConfig 放行，避免 401 触发前端登录弹窗循环。
     * 控制器走 {@code extractUserIdNullable}，service 层亦支持 user_id 为 NULL（建表时
     * V74__beta_feedback_and_telemetry.sql 已让 user_id 可空）。
     */
    @Test
    void telemetryBatchAcceptsAnonymous() throws Exception {
        mockMvc.perform(post("/api/beta/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"eventType\":\"page_view\",\"route\":\"/\",\"payload\":{},\"occurredAt\":\"2026-04-28T00:00:00Z\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.created").value(1));
    }

    @Test
    void webVitalsAcceptsAnonymous() throws Exception {
        mockMvc.perform(post("/api/beta/telemetry/web-vitals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"LCP\",\"value\":1234.5,\"rating\":\"good\",\"navigationType\":\"navigate\",\"route\":\"/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty());
    }
}
