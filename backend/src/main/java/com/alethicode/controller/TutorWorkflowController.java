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
 * Public REST facade for the LangGraph tutor workflow. Owns ownership / language /
 * submission validation before delegating runtime concerns to {@link TutorGraphClient}
 * and projection concerns to {@link TutorWorkflowProjectionService}.
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
     * Tutor workflow payloads are small by design (event + event_data). Cap total
     * request body to 256 KiB so a malicious user cannot stream a 200 MB JSON body
     * that Spring Jackson would otherwise deserialize into a giant Map. The global
     * {@code spring.servlet.multipart.max-request-size} is 256 MB and intentionally
     * loose for file uploads; tutor workflow must stay tight.
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

        // 学生做完 A 题 AC 后切到 B 题继续做不应叠加配额：先看是否已有同 (user, problem)
        // 的活跃 session，存在直接复用、不计入配额、不调 tutor-graph 重建 thread。
        // 这条是对 CRIT-3 active session quota 的"业务体验补丁"——quota 只防恶意脚本
        // 短时间批量创建，不应惩罚正常切题学生。
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
            // Stop the virtual thread poller before cancelling the tutor-graph run so
            // the shutdown is deterministic even if tutor-graph is unresponsive.
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

        // CRIT-3: 每用户每天 LLM run 配额硬上限，超过后立刻 429 并阻止下游 LLM 调用。
        // 用 Redis 原子 INCR 计数，TTL=86400s 自然过期；超额计数也会带来 1 次额外 INCR
        // 但不会落到 LLM provider 上。
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

        // Language must still be allowed by the problem even if it came from the projection.
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

    // ---------- helpers ----------

    /**
     * Short-circuit requests whose declared {@code Content-Length} exceeds
     * {@link #MAX_REQUEST_BODY_BYTES}. Returns {@code null} when the size is OK so the
     * caller can proceed with the normal pipeline. Spring has already buffered the
     * body by the time this method runs, so a chunked sender could still get
     * through; this is a cheap tripwire, not the last line of defense.
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
     * Extract the authenticated user id.
     *
     * <p>Contract order (must match {@code SessionAuthenticationFilter}):
     * <ol>
     *   <li>{@code authentication.getDetails()} — the session auth filter stores the
     *       numeric user id there while {@code principal} is just the username.</li>
     *   <li>{@code authentication.getPrincipal()} when it is a {@link Map} with an
     *       {@code "id"} entry — used by API-key / programmatic auth paths and tests
     *       that synthesize a principal.</li>
     * </ol>
     * Anything else is a configuration bug and maps to 401.
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
     * Generic 503 response that DOES NOT leak the downstream exception message.
     * Use this at every point where tutor-graph replies with an error so operators
     * see detail in the log but clients get a stable, redacted payload.
     */
    private ResponseEntity<ApiResponse<Object>> fail503Redacted(String action, Exception e) {
        log.warn("tutor-graph unavailable during {}: {}", action, e.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("tutor-graph service temporarily unavailable"));
    }

    // ---------- exception -> HTTP mapping ----------

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
     * Resilience4j throws {@link io.github.resilience4j.ratelimiter.RequestNotPermitted}
     * when the token bucket for {@code tutorWorkflow} is empty. Map to 429 so the
     * frontend can back off instead of retrying the same endpoint immediately.
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
     * CRIT-3: per-user daily LLM run / active session quota exceeded — return 429
     * with a {@code Retry-After: 60} hint. Frontend must surface a friendly
     * "你今天已使用过多 AI 导学次数，明天再来" rather than retry immediately.
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
