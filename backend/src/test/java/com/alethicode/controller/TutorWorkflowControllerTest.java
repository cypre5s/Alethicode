package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.InternalAITutorToolService;
import com.alethicode.service.aitutor.SessionUsage;
import com.alethicode.service.aitutor.context.CardSummary;
import com.alethicode.service.aitutor.context.ConversationContextService;
import com.alethicode.service.aitutor.context.ConversationMode;
import com.alethicode.service.aitutor.graph.TutorGraphClient;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.ProblemAccess;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.SubmissionRef;
import com.alethicode.service.aitutor.graph.TutorWorkflowProjectionService;
import com.alethicode.service.aitutor.quota.AiTutorQuotaService;
import com.alethicode.websocket.TutorWorkflowWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link TutorWorkflowController} 的 fail-fast 契约单元测试。
 *
 * <p>重点覆盖所有权、题目可访问性、语言白名单、提交所有权和 AC 要求；测试不启动 Spring Boot 上下文，
 * 只用 Mockito 隔离控制器协作者。</p>
 */
class TutorWorkflowControllerTest {

    private TutorGraphClient graphClient;
    private TutorWorkflowProjectionService projectionService;
    private TutorWorkflowAuthorizer authorizer;
    private TutorWorkflowWebSocketHandler webSocketHandler;
    private ConversationContextService conversationContextService;
    private AiTutorQuotaService quotaService;
    private InternalAITutorToolService internalAITutorToolService;
    private TutorWorkflowController controller;

    @BeforeEach
    void setUp() {
        graphClient = mock(TutorGraphClient.class);
        projectionService = mock(TutorWorkflowProjectionService.class);
        authorizer = mock(TutorWorkflowAuthorizer.class);
        webSocketHandler = mock(TutorWorkflowWebSocketHandler.class);
        conversationContextService = mock(ConversationContextService.class);
        quotaService = mock(AiTutorQuotaService.class);
        internalAITutorToolService = mock(InternalAITutorToolService.class);
        controller = new TutorWorkflowController(
                graphClient, projectionService, authorizer, webSocketHandler,
                conversationContextService, quotaService, internalAITutorToolService);
    }

    @Test
    void createSession_missingProblemId_returns422() {
        ResponseEntity<ApiResponse<Object>> response = controller.createSession(
                Map.of("language", "Python3"), null, authenticationFor(1L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(graphClient);
    }

    @Test
    void createSession_missingLanguage_returns422() {
        ResponseEntity<ApiResponse<Object>> response = controller.createSession(
                Map.of("problem_id", 42), null, authenticationFor(1L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(graphClient);
    }

    @Test
    void createSession_accessDenied_returns403() {
        doThrow(new TutorWorkflowAuthorizer.AccessDenied("Problem 42 is not accessible to user 1"))
                .when(authorizer).assertProblemAccessible(anyLong(), anyLong(), anyString());

        ResponseEntity<ApiResponse<Object>> response = controller.handleAccessDenied(
                new TutorWorkflowAuthorizer.AccessDenied("Problem 42 is not accessible to user 1"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createSession_languageNotAllowed_returns422() {
        ResponseEntity<ApiResponse<Object>> response = controller.handleLanguageNotAllowed(
                new TutorWorkflowAuthorizer.LanguageNotAllowed("Language 'Go' not allowed for problem 42"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void createSession_happyPath_returns201WithSessionSnapshot() {
        when(graphClient.createThread(anyString(), anyLong(), anyLong(), eq("Python3"), any()))
                .thenReturn(Mono.just(Map.<String, Object>of("thread_id", "thread_abc")));
        when(projectionService.createSessionWithId(anyString(), eq(1L), eq(42L), eq("thread_abc"), eq("Python3")))
                .thenAnswer(inv -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("session_id", inv.getArgument(0, String.class));
                    snapshot.put("thread_id", inv.getArgument(3, String.class));
                    snapshot.put("problem_id", inv.getArgument(2, Long.class));
                    snapshot.put("language", inv.getArgument(4, String.class));
                    snapshot.put("created", true);
                    return snapshot;
                });

        ResponseEntity<ApiResponse<Object>> response = controller.createSession(
                Map.of("problem_id", 42, "language", "Python3"),
                null,
                authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(authorizer).assertProblemAccessible(42L, 1L, "Python3");
        verify(quotaService).enforceActiveSessionQuota(1L);
        verify(projectionService).createSessionWithId(
                anyString(), eq(1L), eq(42L), eq("thread_abc"), eq("Python3"));
    }

    @Test
    void createSession_reusesExistingActiveSession_doesNotCallTutorGraph() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("session_id", "twf_existing");
        existing.put("thread_id", "thread_existing");
        existing.put("phase", "READING");
        existing.put("runtime_state", "COMPLETED");
        when(projectionService.findActiveSession(1L, 42L)).thenReturn(Optional.of(existing));

        ResponseEntity<ApiResponse<Object>> response = controller.createSession(
                Map.of("problem_id", 42, "language", "Python3"),
                null,
                authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().data();
        assertThat(body).containsEntry("session_id", "twf_existing");
        assertThat(body).containsEntry("thread_id", "thread_existing");
        assertThat(body).containsEntry("reused", true);

        verifyNoInteractions(graphClient);
        verify(quotaService, never()).enforceActiveSessionQuota(anyLong());
        verify(projectionService, never()).createSessionWithId(anyString(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void createSession_activeSessionQuotaExceeded_returns429() {
        ResponseEntity<ApiResponse<Object>> response = controller.handleQuotaExceeded(
                new AiTutorQuotaService.QuotaExceededException(
                        AiTutorQuotaService.QuotaKind.ACTIVE_SESSIONS, 10, "active_sessions cap reached"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody().error()).contains("活跃 AI 导学会话已达上限");
    }

    @Test
    void createRun_dailyLlmRunQuotaExceeded_returns429() {
        ResponseEntity<ApiResponse<Object>> response = controller.handleQuotaExceeded(
                new AiTutorQuotaService.QuotaExceededException(
                        AiTutorQuotaService.QuotaKind.DAILY_LLM_RUNS, 50, "daily_llm_runs cap reached"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody().error()).contains("已超出今日 AI 导学次数上限");
    }

    @Test
    void createRun_nonOwner_returns403() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(false);
        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x", Map.of("event", "READING"), null, authenticationFor(1L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(graphClient);
    }

    @Test
    void createRun_sessionNotFound_returns404() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.empty());
        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x", Map.of("event", "READING"), null, authenticationFor(1L));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createRun_activeRun_returns409OnSecondCall() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));
        when(graphClient.createRun(anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(Map.<String, Object>of("run_id", "run_a")));

        controller.createRun("twf_x", Map.of("event", "READING"), null, authenticationFor(1L));
        ResponseEntity<ApiResponse<Object>> second = controller.createRun(
                "twf_x", Map.of("event", "READING"), null, authenticationFor(1L));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRun_errorFeedbackWithoutSubmission_returns422() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x", Map.of("event", "ERROR_FEEDBACK"), null, authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(graphClient);
    }

    @Test
    void createRun_acReview_assertsSubmissionAccepted() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));
        SubmissionRef ref = new SubmissionRef("sub_1", 1L, 42L, 0);
        when(authorizer.assertSubmissionBelongsTo("sub_1", 1L, 42L)).thenReturn(ref);
        when(graphClient.createRun(anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(Map.<String, Object>of("run_id", "run_a")));

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("event", "AC_REVIEW");
        req.put("event_data", Map.of("submission_id", "sub_1"));
        ResponseEntity<ApiResponse<Object>> response = controller.createRun("twf_x", req, null, authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(authorizer).assertSubmissionAccepted(ref);
    }

    @Test
    void createRun_languageResolvedFromProjection_whenRequestOmits() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));
        when(graphClient.createRun(anyString(), anyString(), anyLong(), anyLong(), eq("Python3"), anyString(), any()))
                .thenReturn(Mono.just(Map.<String, Object>of("run_id", "run_a")));

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x", Map.of("event", "READING"), null, authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(graphClient).createRun(
                eq("twf_x"), eq("thread_abc"), eq(1L), eq(42L), eq("Python3"), eq("READING"), any());
    }

    @Test
    void createRun_marksProjectionQueuedImmediatelyAfterAcceptingRun() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));
        when(graphClient.createRun(anyString(), anyString(), anyLong(), anyLong(), eq("Python3"), eq("READING"), any()))
                .thenReturn(Mono.just(Map.<String, Object>of("run_id", "run_a")));

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x", Map.of("event", "READING"), null, authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(projectionService).markRunQueued("twf_x", "run_a");
    }

    @Test
    void getConversation_returnsActiveModeAndLastCardsForOwner() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(conversationContextService.getActiveMode("twf_x")).thenReturn(ConversationMode.READING);
        when(conversationContextService.listLastCards("twf_x", 8)).thenReturn(List.of(
                new CardSummary("C-G-001", "problem_guide", "reading", "读题重点", Instant.parse("2026-04-25T08:00:00Z"))
        ));

        ResponseEntity<ApiResponse<Object>> response = controller.getConversation("twf_x", authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().data();
        assertThat(body).containsEntry("active_mode", "reading");
        assertThat((List<?>) body.get("last_cards")).hasSize(1);
    }

    @Test
    void getSessionUsage_returnsCounters_forOwner() {
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(internalAITutorToolService.getSessionUsage("twf_x"))
                .thenReturn(new SessionUsage(2048L, 8000L, "deepseek-chat",
                        Instant.parse("2026-05-06T10:00:00Z")));

        ResponseEntity<ApiResponse<Object>> response =
                controller.getSessionUsage("twf_x", authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().data();
        assertThat(body)
                .containsEntry("tokens_used", 2048L)
                .containsEntry("tokens_limit", 8000L)
                .containsEntry("model_name", "deepseek-chat");
    }

    @Test
    void getSessionUsage_returns404_whenSessionMissing() {
        when(projectionService.getSession("twf_missing")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Object>> response =
                controller.getSessionUsage("twf_missing", authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(internalAITutorToolService);
    }

    @Test
    void getSessionUsage_returns403_whenNotOwner() {
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(false);

        ResponseEntity<ApiResponse<Object>> response =
                controller.getSessionUsage("twf_x", authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(internalAITutorToolService);
    }

    @Test
    void switchConversationMode_persistsModeForCurrentPhase() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(conversationContextService.switchMode("twf_x", ConversationMode.CHAT, com.alethicode.service.aitutor.contract.Phase.READING))
                .thenReturn(ConversationMode.CHAT);

        ResponseEntity<ApiResponse<Object>> response = controller.switchConversationMode(
                "twf_x", Map.of("mode", "chat"), authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().data();
        assertThat(body).containsEntry("active_mode", "chat");
        verify(conversationContextService).switchMode("twf_x", ConversationMode.CHAT, com.alethicode.service.aitutor.contract.Phase.READING);
    }

    @Test
    void createRun_planStart_missingFields_returns422() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x",
                Map.of("event", "PLAN_START", "event_data", Map.of("reason", "卡住了")),
                null,
                authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(graphClient);
    }

    @Test
    void createRun_planResponse_missingEvidenceType_returns422() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x",
                Map.of("event", "PLAN_RESPONSE", "event_data", Map.of(
                        "plan_id", "plan_1",
                        "step_id", "step_1"
                )),
                null,
                authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(graphClient);
    }

    @Test
    void createRun_planSteering_redirectRequiresInstruction() {
        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x",
                Map.of("event", "PLAN_STEERING", "event_data", Map.of(
                        "plan_id", "plan_1",
                        "signal_type", "redirect"
                )),
                null,
                authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(graphClient);
    }

    @Test
    void createRun_readsUserId_fromAuthenticationDetails_asSessionFilterDoes() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "alice", null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        auth.setDetails(1L);

        when(projectionService.isSessionOwnedByUser("twf_x", 1L)).thenReturn(true);
        when(projectionService.getSession("twf_x")).thenReturn(Optional.of(sessionSnapshot("Python3")));
        when(authorizer.tryLoadProblem(42L)).thenReturn(Optional.of(problemAccess(Set.of("Python3"))));
        when(graphClient.createRun(anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(Map.<String, Object>of("run_id", "run_a")));

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x", Map.of("event", "READING"), null, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(projectionService).isSessionOwnedByUser("twf_x", 1L);
    }

    @Test
    void createRun_oversizedBody_returns413() {
        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(req.getContentLengthLong()).thenReturn(512L * 1024L);

        ResponseEntity<ApiResponse<Object>> response = controller.createRun(
                "twf_x", Map.of("event", "READING"), req, authenticationFor(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verifyNoInteractions(projectionService);
        verifyNoInteractions(authorizer);
        verifyNoInteractions(graphClient);
    }

    private static Authentication authenticationFor(long userId) {
        Map<String, Object> principal = Map.of("id", userId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    private static Map<String, Object> sessionSnapshot(String language) {
        Map<String, Object> s = new HashMap<>();
        s.put("session_id", "twf_x");
        s.put("thread_id", "thread_abc");
        s.put("problem_id", 42L);
        s.put("language", language);
        s.put("node_outputs", Map.of());
        s.put("available_actions", List.of());
        return s;
    }

    private static ProblemAccess problemAccess(Set<String> langs) {
        return new ProblemAccess(42L, 99L, true, false, langs, false);
    }
}
