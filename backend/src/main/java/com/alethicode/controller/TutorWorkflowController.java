package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.InternalAITutorToolService;
import com.alethicode.service.aitutor.SessionUsage;
import com.alethicode.service.aitutor.context.CardSummary;
import com.alethicode.service.aitutor.context.ConversationContextService;
import com.alethicode.service.aitutor.context.ConversationMode;
import com.alethicode.service.aitutor.contract.Phase;
import com.alethicode.service.aitutor.graph.TutorGraphClient;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.ProblemAccess;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.SubmissionRef;
import com.alethicode.service.aitutor.graph.TutorWorkflowProjectionService;
import com.alethicode.service.aitutor.quota.AiTutorQuotaService;
import com.alethicode.websocket.TutorWorkflowWebSocketHandler;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangGraph 导学工作流的学生端 REST 门面。
 *
 * 控制器负责所有权、语言和提交校验；运行时委托给 {@link TutorGraphClient}，投影数据委托给
 * {@link TutorWorkflowProjectionService}。
 */
@RestController
@RequestMapping("/api/ai/tutor-workflow-sessions")
public class TutorWorkflowController {

    private static final Logger log = LoggerFactory.getLogger(TutorWorkflowController.class);
    private static final List<String> ALLOWED_INTERRUPT_ACTIONS = List.of("confirm", "reject", "modify");
    private static final Set<String> SUBMISSION_REQUIRED_EVENTS = Set.of("ERROR_FEEDBACK", "AC_REVIEW");
    private static final Set<String> PLAN_EVENTS = Set.of("PLAN_RECOMMEND", "PLAN_START", "PLAN_RESPONSE", "PLAN_STEERING");
    private static final Set<String> PLAN_EVIDENCE_TYPES = Set.of("text", "sample_prediction", "code_change", "reflection");
    private static final Set<String> PLAN_SIGNAL_TYPES = Set.of("pause", "resume", "skip", "take_over", "redirect");
    private static final Duration GRAPH_CALL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration GRAPH_RESTORE_TIMEOUT = Duration.ofSeconds(30);
    /**
     * 导学工作流请求体只应包含 {@code event} 和 {@code event_data}。
     *
     * 全局上传限制需要支持大文件；本接口单独限制为 256 KiB，避免异常 JSON 被反序列化成大对象。
     */
    private static final long MAX_REQUEST_BODY_BYTES = 256L * 1024L;

    private final TutorGraphClient graphClient;
    private final TutorWorkflowProjectionService projectionService;
    private final TutorWorkflowAuthorizer authorizer;
    private final TutorWorkflowWebSocketHandler webSocketHandler;
    private final ConversationContextService conversationContextService;
    private final AiTutorQuotaService quotaService;
    private final InternalAITutorToolService internalAITutorToolService;
    private final ConcurrentHashMap<String, String> activeRuns = new ConcurrentHashMap<>();

    public TutorWorkflowController(
            TutorGraphClient graphClient,
            TutorWorkflowProjectionService projectionService,
            TutorWorkflowAuthorizer authorizer,
            TutorWorkflowWebSocketHandler webSocketHandler,
            ConversationContextService conversationContextService,
            AiTutorQuotaService quotaService,
            InternalAITutorToolService internalAITutorToolService
    ) {
        this.graphClient = graphClient;
        this.projectionService = projectionService;
        this.authorizer = authorizer;
        this.webSocketHandler = webSocketHandler;
        this.conversationContextService = conversationContextService;
        this.quotaService = quotaService;
        this.internalAITutorToolService = internalAITutorToolService;
    }

    @PostConstruct
    public void registerRunCompletionCallback() {
        webSocketHandler.setRunCompletionCallback((sessionId, runId) ->
                activeRuns.remove(sessionId, runId));
    }

    private Map<String, Object> extractContext(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return null;
    }

    @PostMapping({"", "/"})
    @RateLimiter(name = "tutorWorkflow")
    public ResponseEntity<ApiResponse<Object>> createSession(
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            Authentication authentication
    ) {
        ResponseEntity<ApiResponse<Object>> sizeCheck = enforceRequestBodyLimit(servletRequest);
        if (sizeCheck != null) return sizeCheck;
        Long userId = extractUserId(authentication);
        Long problemId = toLong(request.get("problem_id"));
        String language = (String) request.get("language");

        if (problemId == null) {
            return fail422("problem_id is required");
        }
        if (language == null || language.isBlank()) {
            return fail422("language is required");
        }

        authorizer.assertProblemAccessible(problemId, userId, language);

        Map<String, Object> context = extractContext(request.get("context"));
        if (context != null && "classroom_assignment".equals(context.get("source")) && context.get("anti_cheating") == null) {
            return fail422("classroom_assignment session must declare anti_cheating");
        }

        // 同 (user, problem) 已有活跃 session 时直接复用，不叠加配额
        Optional<Map<String, Object>> existing = projectionService.findActiveSession(userId, problemId);
        if (existing.isPresent()) {
            Map<String, Object> reused = existing.get();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("session_id", reused.get("session_id"));
            snapshot.put("thread_id", reused.get("thread_id"));
            snapshot.put("problem_id", problemId);
            snapshot.put("language", language);
            snapshot.put("phase", reused.get("phase"));
            snapshot.put("runtime_state", reused.get("runtime_state"));
            snapshot.put("reused", true);
            return ResponseEntity.ok(ApiResponse.success(snapshot));
        }

        // CRIT-3: 真正新建时才校验配额（每用户最多 10 个不同题的 active session）。
        quotaService.enforceActiveSessionQuota(userId);

        String sessionId = "twf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Map<String, Object> threadResult;
        try {
            threadResult = graphClient.createThread(sessionId, userId, problemId, language, context)
                    .block(GRAPH_CALL_TIMEOUT);
        } catch (Exception e) {
            return fail503Redacted("createThread", e);
        }
        if (threadResult == null) {
            return fail503Redacted("createThread", new IllegalStateException("tutor-graph returned null thread result"));
        }

        String threadId = (String) threadResult.get("thread_id");
        Map<String, Object> session = projectionService.createSessionWithId(
                sessionId, userId, problemId, threadId, language);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(session));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Object>> getSession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        Optional<Map<String, Object>> sessionOpt = projectionService.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return fail404("Session not found");
        }
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }
        return ResponseEntity.ok(ApiResponse.success(sessionOpt.get()));
    }

    /**
     * Phase 1 chat composer plan 1.7：用户态前端 ContextUsageBar 拉 token / 上下文用量。
     * 鉴权与 getSession 同源——必须是当前登录用户的 session 才可读。
     */
    @GetMapping("/{sessionId}/usage")
    public ResponseEntity<ApiResponse<Object>> getSessionUsage(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        Optional<Map<String, Object>> sessionOpt = projectionService.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return fail404("Session not found");
        }
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }
        SessionUsage usage = internalAITutorToolService.getSessionUsage(sessionId);
        return ResponseEntity.ok(ApiResponse.success(usage.toMap()));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return ResponseEntity.status(403).build();
        }

        String activeRunId = activeRuns.remove(sessionId);
        if (activeRunId != null) {
            // 先停 poller 再 cancel run，确保 tutor-graph 无响应时也能确定性关闭
            webSocketHandler.interruptPoller(activeRunId);
            try {
                graphClient.cancelRun(activeRunId).block(Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("cancelRun failed during deleteSession for {}/{}: {}",
                        sessionId, activeRunId, e.getMessage());
            }
        }

        projectionService.deactivateSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sessionId}/runs")
    @RateLimiter(name = "tutorWorkflow")
    public ResponseEntity<ApiResponse<Object>> createRun(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            Authentication authentication
    ) {
        ResponseEntity<ApiResponse<Object>> sizeCheck = enforceRequestBodyLimit(servletRequest);
        if (sizeCheck != null) return sizeCheck;
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }

        if (activeRuns.containsKey(sessionId)) {
            return fail409("Session already has an active run");
        }

        // 每用户每天 LLM run 配额硬上限，超额 429
        quotaService.enforceDailyLlmRunQuota(userId);

        Optional<Map<String, Object>> sessionOpt = projectionService.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return fail404("Session not found");
        }
        Map<String, Object> session = sessionOpt.get();
        String threadId = (String) session.get("thread_id");
        Long problemId = toLong(session.get("problem_id"));
        String event = (String) request.get("event");

        Object rawEventData = request.getOrDefault("event_data", Map.of());
        if (!(rawEventData instanceof Map)) {
            return fail422("event_data must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> eventData = (Map<String, Object>) rawEventData;

        if (event == null || event.isBlank()) {
            return fail422("event is required");
        }
        String normalizedEvent = event.toUpperCase();
        String planValidationError = validatePlanEvent(normalizedEvent, eventData);
        if (planValidationError != null) {
            return fail422(planValidationError);
        }

        String language = resolveLanguage(sessionId, session, request, eventData);
        if (language == null) {
            return fail422("language is required");
        }

        // 即使语言来自投影，也必须重新按题目配置校验。
        ProblemAccess problemAccess = authorizer.tryLoadProblem(problemId)
                .orElseThrow(() -> new TutorWorkflowAuthorizer.ProblemNotFound(problemId));
        authorizer.assertLanguageAllowed(problemAccess, language);

        if (SUBMISSION_REQUIRED_EVENTS.contains(normalizedEvent)) {
            String submissionId = stringOf(eventData.get("submission_id"));
            if (submissionId == null) {
                return fail422("submission_id is required for event " + normalizedEvent);
            }
            SubmissionRef submission = authorizer.assertSubmissionBelongsTo(submissionId, userId, problemId);
            if ("AC_REVIEW".equals(normalizedEvent)) {
                authorizer.assertSubmissionAccepted(submission);
            }
        }

        String runId = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String previousRun = activeRuns.putIfAbsent(sessionId, runId);
        if (previousRun != null) {
            return fail409("Session already has an active run");
        }

        Map<String, Object> runResult;
        try {
            runResult = graphClient.createRun(
                    sessionId, threadId, userId, problemId, language, normalizedEvent, eventData
            ).block(GRAPH_CALL_TIMEOUT);
        } catch (Exception e) {
            activeRuns.remove(sessionId, runId);
            return fail503Redacted("createRun", e);
        }

        if (runResult == null || runResult.get("run_id") == null) {
            activeRuns.remove(sessionId, runId);
            return fail503Redacted("createRun",
                    new IllegalStateException("tutor-graph returned invalid run response"));
        }

        String actualRunId = (String) runResult.get("run_id");
        if (!actualRunId.equals(runId)) {
            activeRuns.put(sessionId, actualRunId);
        }

        projectionService.markRunQueued(sessionId, actualRunId);
        webSocketHandler.subscribeToRunEvents(sessionId, actualRunId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(runResult));
    }

    @GetMapping("/{sessionId}/checkpoints")
    public ResponseEntity<ApiResponse<Object>> getCheckpoints(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }

        Optional<Map<String, Object>> sessionOpt = projectionService.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return fail404("Session not found");
        }

        String threadId = (String) sessionOpt.get().get("thread_id");
        Map<String, Object> graphResult;
        try {
            graphResult = graphClient.listCheckpoints(threadId, 20).block(GRAPH_CALL_TIMEOUT);
        } catch (Exception e) {
            return fail503Redacted("listCheckpoints", e);
        }

        Object checkpoints = graphResult != null ? graphResult.get("checkpoints") : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("checkpoints", checkpoints != null ? checkpoints : List.of());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{sessionId}/checkpoint-restorations")
    @RateLimiter(name = "tutorWorkflow")
    public ResponseEntity<ApiResponse<Object>> restoreCheckpoint(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            Authentication authentication
    ) {
        ResponseEntity<ApiResponse<Object>> sizeCheck = enforceRequestBodyLimit(servletRequest);
        if (sizeCheck != null) return sizeCheck;
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }

        String checkpointId = (String) request.get("checkpoint_id");
        if (checkpointId == null || checkpointId.isBlank()) {
            return fail422("checkpoint_id is required");
        }
        if (activeRuns.containsKey(sessionId)) {
            return fail409("Cannot restore while a run is active");
        }

        Optional<Map<String, Object>> sessionOpt = projectionService.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return fail404("Session not found");
        }

        String threadId = (String) sessionOpt.get().get("thread_id");
        Map<String, Object> result;
        try {
            result = graphClient.restoreCheckpoint(threadId, checkpointId).block(GRAPH_RESTORE_TIMEOUT);
        } catch (WebClientResponseException.NotFound e) {
            return fail404("Checkpoint not found");
        } catch (Exception e) {
            return fail503Redacted("restoreCheckpoint", e);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }

    @GetMapping("/{sessionId}/conversation")
    public ResponseEntity<ApiResponse<Object>> getConversation(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("active_mode", conversationContextService.getActiveMode(sessionId).key());
        body.put("last_cards", conversationContextService.listLastCards(sessionId, 8).stream()
                .map(CardSummary::toMap)
                .toList());
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/{sessionId}/mode")
    public ResponseEntity<ApiResponse<Object>> switchConversationMode(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }
        Optional<Map<String, Object>> sessionOpt = projectionService.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return fail404("Session not found");
        }
        String rawMode = stringOf(request.get("mode"));
        Optional<ConversationMode> targetMode = ConversationMode.fromKey(rawMode);
        if (targetMode.isEmpty()) {
            return fail422("mode is required or invalid");
        }
        String rawPhase = stringOf(sessionOpt.get().get("phase"));
        Phase currentPhase = Phase.from(rawPhase).orElse(Phase.READING);
        ConversationMode activeMode = conversationContextService.switchMode(sessionId, targetMode.get(), currentPhase);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("active_mode", activeMode.key());
        body.put("last_cards", conversationContextService.listLastCards(sessionId, 8).stream()
                .map(CardSummary::toMap)
                .toList());
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * 压缩会话上下文：转发 COMPACT 事件到 tutor-graph，由 compact 节点执行摘要。
     */
    @PostMapping("/{sessionId}/compact")
    @RateLimiter(name = "tutorWorkflow")
    public ResponseEntity<ApiResponse<Object>> compactSession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }
        if (activeRuns.containsKey(sessionId)) {
            return fail409("Session already has an active run");
        }

        Optional<Map<String, Object>> sessionOpt = projectionService.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return fail404("Session not found");
        }

        Map<String, Object> session = sessionOpt.get();
        String threadId = (String) session.get("thread_id");
        Long problemId = toLong(session.get("problem_id"));
        String language = (String) session.get("language");
        if (language == null || language.isBlank()) {
            language = projectionService.getSessionLanguage(sessionId).orElse(null);
        }
        if (language == null) {
            return fail422("language is required");
        }

        String runId = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String previousRun = activeRuns.putIfAbsent(sessionId, runId);
        if (previousRun != null) {
            return fail409("Session already has an active run");
        }

        Map<String, Object> runResult;
        try {
            runResult = graphClient.createRun(
                    sessionId, threadId, userId, problemId, language, "COMPACT", Map.of()
            ).block(GRAPH_CALL_TIMEOUT);
        } catch (Exception e) {
            activeRuns.remove(sessionId, runId);
            return fail503Redacted("compact", e);
        }

        if (runResult == null || runResult.get("run_id") == null) {
            activeRuns.remove(sessionId, runId);
            return fail503Redacted("compact", new IllegalStateException("tutor-graph returned invalid run response"));
        }

        String actualRunId = (String) runResult.get("run_id");
        if (!actualRunId.equals(runId)) {
            activeRuns.put(sessionId, actualRunId);
        }

        projectionService.markRunQueued(sessionId, actualRunId);
        webSocketHandler.subscribeToRunEvents(sessionId, actualRunId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("compacted", true);
        body.put("run_id", actualRunId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(body));
    }

    /**
     * 分叉会话：复制源 session 的 events 到新 session，返回新 session 信息。
     */
    @PostMapping("/{sessionId}/fork")
    @RateLimiter(name = "tutorWorkflow")
    public ResponseEntity<ApiResponse<Object>> forkSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }
        Long fromMessageId = toLong(request.get("fromMessageId"));
        Map<String, Object> result = internalAITutorToolService.forkSession(sessionId, fromMessageId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PostMapping("/{sessionId}/interrupt-responses")
    @RateLimiter(name = "tutorWorkflow")
    public ResponseEntity<ApiResponse<Object>> respondInterrupt(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            Authentication authentication
    ) {
        ResponseEntity<ApiResponse<Object>> sizeCheck = enforceRequestBodyLimit(servletRequest);
        if (sizeCheck != null) return sizeCheck;
        Long userId = extractUserId(authentication);
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            return fail403("Session not owned by current user");
        }

        String action = (String) request.get("action");
        if (action == null || !ALLOWED_INTERRUPT_ACTIONS.contains(action)) {
            return fail422("action must be confirm, reject, or modify");
        }

        Object rawData = request.getOrDefault("data", Map.of());
        if (!(rawData instanceof Map)) {
            return fail422("data must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) rawData;

        String activeRunId = activeRuns.get(sessionId);
        if (activeRunId == null) {
            return fail409("No active run for this session");
        }

        Map<String, Object> result;
        try {
            result = graphClient.resumeRun(activeRunId, action, data).block(GRAPH_RESTORE_TIMEOUT);
        } catch (Exception e) {
            return fail503Redacted("resumeRun", e);
        }

        webSocketHandler.subscribeToRunEvents(sessionId, activeRunId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }

    /**
     * 根据声明的 {@code Content-Length} 拦截超大请求体。
     *
     * 该检查是低成本哨兵；chunked 请求仍需依赖上游网关和容器限制。
     */
    private ResponseEntity<ApiResponse<Object>> enforceRequestBodyLimit(HttpServletRequest servletRequest) {
        if (servletRequest == null) return null;
        long contentLength = servletRequest.getContentLengthLong();
        if (contentLength > MAX_REQUEST_BODY_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error("request body exceeds tutor workflow limit"));
        }
        return null;
    }

    private String resolveLanguage(String sessionId, Map<String, Object> session,
                                   Map<String, Object> request, Map<String, Object> eventData) {
        Object fromProjection = session.get("language");
        if (fromProjection instanceof String s && !s.isBlank()) {
            return s;
        }
        Object fromEventData = eventData.get("language");
        if (fromEventData instanceof String s && !s.isBlank()) {
            return s;
        }
        Object fromRequest = request.get("language");
        if (fromRequest instanceof String s && !s.isBlank()) {
            return s;
        }
        return projectionService.getSessionLanguage(sessionId).orElse(null);
    }

    /**
     * 从 Spring Security 上下文中提取已认证用户 ID。
     *
     * <p>读取顺序必须与 {@code SessionAuthenticationFilter} 保持一致：
     * <ol>
     *   <li>{@code authentication.getDetails()} 存放 session 认证写入的数字用户 ID。</li>
     *   <li>{@code authentication.getPrincipal()} 为 {@link Map} 且包含 {@code "id"} 时，
     *       支持 API key、程序化认证和测试合成 principal。</li>
     * </ol>
     * 其他情况视为认证配置错误并映射为 401。
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Not authenticated");
        }
        Object details = authentication.getDetails();
        if (details instanceof Number n) {
            return n.longValue();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Map<?, ?> p) {
            Object id = p.get("id");
            if (id instanceof Number n) return n.longValue();
            if (id != null) {
                try {
                    return Long.parseLong(String.valueOf(id));
                } catch (NumberFormatException e) {
                    throw new SecurityException("Malformed user id in authentication principal");
                }
            }
        }
        throw new SecurityException("User id missing from authentication");
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringOf(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String validatePlanEvent(String normalizedEvent, Map<String, Object> eventData) {
        if (!PLAN_EVENTS.contains(normalizedEvent)) {
            return null;
        }
        if ("PLAN_START".equals(normalizedEvent)) {
            if (stringOf(eventData.get("reason")) == null) {
                return "reason is required for event PLAN_START";
            }
            if (stringOf(eventData.get("trigger_source")) == null) {
                return "trigger_source is required for event PLAN_START";
            }
            if (stringOf(eventData.get("current_phase")) == null) {
                return "current_phase is required for event PLAN_START";
            }
            return null;
        }
        if ("PLAN_RESPONSE".equals(normalizedEvent)) {
            if (stringOf(eventData.get("plan_id")) == null) {
                return "plan_id is required for event PLAN_RESPONSE";
            }
            if (stringOf(eventData.get("step_id")) == null) {
                return "step_id is required for event PLAN_RESPONSE";
            }
            String evidenceType = stringOf(eventData.get("evidence_type"));
            if (evidenceType == null) {
                return "evidence_type is required for event PLAN_RESPONSE";
            }
            String normalizedEvidenceType = evidenceType.toLowerCase();
            if (!PLAN_EVIDENCE_TYPES.contains(normalizedEvidenceType)) {
                return "evidence_type must be one of text, sample_prediction, code_change, reflection";
            }
            if (Set.of("text", "reflection").contains(normalizedEvidenceType)
                    && stringOf(eventData.get("response_text")) == null) {
                return "response_text is required for evidence_type " + normalizedEvidenceType;
            }
            if ("sample_prediction".equals(normalizedEvidenceType)
                    && stringOf(eventData.get("sample_prediction")) == null
                    && stringOf(eventData.get("response_text")) == null) {
                return "sample_prediction is required for evidence_type sample_prediction";
            }
            if ("code_change".equals(normalizedEvidenceType)
                    && stringOf(eventData.get("code_snapshot_id")) == null
                    && stringOf(eventData.get("response_text")) == null) {
                return "code_snapshot_id is required for evidence_type code_change";
            }
            return null;
        }
        if ("PLAN_STEERING".equals(normalizedEvent)) {
            if (stringOf(eventData.get("plan_id")) == null) {
                return "plan_id is required for event PLAN_STEERING";
            }
            String signalType = stringOf(eventData.get("signal_type"));
            if (signalType == null) {
                return "signal_type is required for event PLAN_STEERING";
            }
            String normalizedSignalType = signalType.toLowerCase();
            if (!PLAN_SIGNAL_TYPES.contains(normalizedSignalType)) {
                return "signal_type must be one of pause, resume, skip, take_over, redirect";
            }
            if ("redirect".equals(normalizedSignalType)
                    && stringOf(eventData.get("redirect_instruction")) == null) {
                return "redirect_instruction is required for signal_type redirect";
            }
        }
        return null;
    }

    private ResponseEntity<ApiResponse<Object>> fail422(String message) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(message));
    }

    private ResponseEntity<ApiResponse<Object>> fail403(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(message));
    }

    private ResponseEntity<ApiResponse<Object>> fail404(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(message));
    }

    private ResponseEntity<ApiResponse<Object>> fail409(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(message));
    }

    private ResponseEntity<ApiResponse<Object>> fail503(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(message));
    }

    /**
     * 返回脱敏的通用 503，避免向客户端泄露 tutor-graph 异常细节。
     */
    private ResponseEntity<ApiResponse<Object>> fail503Redacted(String action, Exception e) {
        log.warn("tutor-graph unavailable during {}: {}", action, e.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("tutor-graph service temporarily unavailable"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(TutorWorkflowAuthorizer.ProblemNotFound.class)
    public ResponseEntity<ApiResponse<Object>> handleProblemNotFound(TutorWorkflowAuthorizer.ProblemNotFound e) {
        return fail404(e.getMessage());
    }

    @ExceptionHandler(TutorWorkflowAuthorizer.AccessDenied.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(TutorWorkflowAuthorizer.AccessDenied e) {
        return fail403(e.getMessage());
    }

    @ExceptionHandler(TutorWorkflowAuthorizer.LanguageNotAllowed.class)
    public ResponseEntity<ApiResponse<Object>> handleLanguageNotAllowed(TutorWorkflowAuthorizer.LanguageNotAllowed e) {
        return fail422(e.getMessage());
    }

    @ExceptionHandler(TutorWorkflowAuthorizer.SubmissionNotFound.class)
    public ResponseEntity<ApiResponse<Object>> handleSubmissionNotFound(TutorWorkflowAuthorizer.SubmissionNotFound e) {
        return fail404(e.getMessage());
    }

    @ExceptionHandler(TutorWorkflowAuthorizer.SubmissionMismatch.class)
    public ResponseEntity<ApiResponse<Object>> handleSubmissionMismatch(TutorWorkflowAuthorizer.SubmissionMismatch e) {
        return fail403(e.getMessage());
    }

    @ExceptionHandler(TutorWorkflowAuthorizer.SubmissionNotAccepted.class)
    public ResponseEntity<ApiResponse<Object>> handleSubmissionNotAccepted(TutorWorkflowAuthorizer.SubmissionNotAccepted e) {
        return fail422(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException e) {
        return fail422(e.getMessage());
    }

    /**
     * 将 {@code tutorWorkflow} 限流桶耗尽映射为 429。
     */
    @ExceptionHandler(io.github.resilience4j.ratelimiter.RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimitExceeded(
            io.github.resilience4j.ratelimiter.RequestNotPermitted e) {
        log.warn("Tutor workflow rate limit exceeded: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(ApiResponse.error("Rate limit exceeded — please slow down"));
    }

    /**
     * 将每日 LLM run 或活跃会话配额超限映射为 429。
     */
    @ExceptionHandler(AiTutorQuotaService.QuotaExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleQuotaExceeded(
            AiTutorQuotaService.QuotaExceededException e) {
        log.warn("Tutor workflow quota exceeded: kind={}, limit={}, message={}",
                e.kind(), e.limit(), e.getMessage());
        String userMessage = switch (e.kind()) {
            case DAILY_LLM_RUNS -> "已超出今日 AI 导学次数上限（" + e.limit() + " 次/天），请明天再试";
            case ACTIVE_SESSIONS -> "活跃 AI 导学会话已达上限（" + e.limit() + " 个），请先关闭旧会话";
        };
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(ApiResponse.error(userMessage));
    }
}
