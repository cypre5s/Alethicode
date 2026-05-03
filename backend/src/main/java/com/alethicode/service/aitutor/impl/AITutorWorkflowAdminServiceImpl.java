package com.alethicode.service.aitutor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminPreflightService;
import com.alethicode.service.aitutor.WorkflowCheckpointService;
import com.alethicode.service.aitutor.admin.AdminKcManagementService;
import com.alethicode.service.aitutor.admin.AdminMisconceptionMiningService;
import com.alethicode.service.aitutor.admin.AdminVariantReviewService;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.contract.Phase;
import com.alethicode.service.aitutor.evidence.EvidencePack;
import com.alethicode.service.aitutor.evidence.EvidencePackAssembler;
import com.alethicode.service.aitutor.eval.AITutorEvalService;
import com.alethicode.service.aitutor.eval.OffPolicyEvalResult;
import com.alethicode.service.aitutor.eval.OffPolicyEvalService;
import com.alethicode.service.aitutor.eval.TraceGradeService;
import com.alethicode.service.aitutor.policy.BanditDecision;
import com.alethicode.service.aitutor.policy.ContextualBanditReranker;
import com.alethicode.service.aitutor.policy.TransitionPolicy;
import com.alethicode.service.aitutor.policy.TutorActionDecision;
import com.alethicode.service.aitutor.policy.TutorActionPolicy;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.execution.ExecutionTraceService;
import com.alethicode.service.aitutor.execution.JudgeBackedExecutionTraceService;
import com.alethicode.service.aitutor.execution.LanguageRoutedExecutionTraceService;
import com.alethicode.service.aitutor.execution.PythonExecutionTraceService;
import com.alethicode.service.aitutor.language.AiTutorProblemLanguageNormalizer;
import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import com.alethicode.service.aitutor.language.TutorLanguageSupport;
import com.alethicode.service.aitutor.profile.CrossCourseProfileService;
import com.alethicode.service.aitutor.profile.LearnerMemoryService;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.profile.LearnerState;
import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import com.alethicode.service.aitutor.retrieval.SimilarErrorRetrievalService;
import com.alethicode.service.aitutor.rollout.RolloutDecision;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.alethicode.service.aitutor.schema.CardSchemaRegistry;
import com.alethicode.service.aitutor.schema.CardSchemaValidator;
import com.alethicode.config.BetaFeatureRegistry;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.ai.AiCircuitBreaker;
import com.alethicode.websocket.WorkflowRealtimeSupport;
import static com.alethicode.util.ServiceParseUtils.castMap;
import static com.alethicode.util.ServiceParseUtils.firstNonBlank;
import static com.alethicode.util.ServiceParseUtils.randomId;
import static com.alethicode.util.ServiceParseUtils.shortenForPrompt;
import static com.alethicode.util.ServiceParseUtils.stringValue;
import static com.alethicode.util.ServiceParseUtils.trimToEmpty;
import static com.alethicode.util.ServiceParseUtils.trimToNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AITutorWorkflowAdminServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(AITutorWorkflowAdminServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final List<String> WORKFLOW_EVENTS = List.of("CALIBRATING", "READING", "IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW", "TRANSFER", "CHAT", "AGENT_FEEDBACK", "KNOWLEDGE_REVIEW");
    private static final Set<String> ALLOWED_CONFIDENCE_LEVELS = Set.of("low", "medium", "high");
    private static final List<String> BEHAVIOR_METRIC_KEYS = List.of("consecutiveErrors", "submissionCount", "editFrequency", "dwellTime", "deleteRatio");
    private static final Pattern INVALID_HANDLING_PATTERN = Pattern.compile(
            "(非法|不合法|无效|异常输入|错误输入|invalid|illegal|malformed|out\\s*of\\s*range|越界|超出范围)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INVALID_CASE_PATTERN = Pattern.compile(
            "(invalid|illegal|malformed|非法|不合法|无效|异常)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRANSFER_VALIDATION_FIELD_PATTERN = Pattern.compile("^transfer\\.([a-z_]+).*$");
    private static final Pattern SOURCE_DISPLAY_CHAPTER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern SOURCE_TITLE_CHAPTER_PREFIX_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\s+.*)?$");
    private static final Pattern SOURCE_DISPLAY_GENERIC_PREFIX_PATTERN = Pattern.compile("^([A-Za-z]+\\d+)-(\\d+)$");
    /**
     * Rejects outputs like "千位数是-，" where minus is not followed by a digit (LLM often omits -1).
     * Lookahead is evaluated after the '-' in "是-".
     */
    private static final Pattern TRANSFER_DIGIT_PLACE_LONE_MINUS = Pattern.compile(
            "(?:千|百|十|个)位数是-(?![0-9])"
    );
    /** "百位位数" style duplicated classifier */
    private static final Pattern TRANSFER_DIGIT_PLACE_DUP_WEI = Pattern.compile("位位");
    private static final int TRANSFER_GENERATION_MAX_ATTEMPTS = 3;
    private static final int MAX_CHAT_HISTORY = 50;
    private static final int MAX_CHAT_CONTEXT_WINDOW = 6;
    private static final int MAX_CHAT_CODE_CONTEXT_CHARS = 1200;
    private static final Pattern DIRECT_ANSWER_RISK_PATTERN = Pattern.compile(
            "(```|完整代码|标准答案|直接答案|final\\s+answer|here\\s+is\\s+the\\s+answer)",
            Pattern.CASE_INSENSITIVE
    );
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiTutorProblemLanguageNormalizer aiTutorProblemLanguageNormalizer;
    private final AiModelGateway aiModelGateway;
    private final AlethicodeProperties properties;
    private final TransitionPolicy transitionPolicy;
    private final CardSchemaValidator cardSchemaValidator;
    private final CoursewareRetrievalService coursewareRetrievalService;
    private final MasteryService masteryService;
    private final LearnerMemoryService learnerMemoryService;
    private final CrossCourseProfileService crossCourseProfileService;
    private final LearnerProfileProjector learnerProfileProjector;
    private final EvidencePackAssembler evidencePackAssembler;
    private final SimilarErrorRetrievalService similarErrorRetrievalService;
    private final ExecutionTraceService executionTraceService;
    private final TutorActionPolicy tutorActionPolicy;
    private final ContextualBanditReranker contextualBanditReranker;
    private final TraceGradeService traceGradeService;
    private final AITutorEvalService aiTutorEvalService;
    private final OffPolicyEvalService offPolicyEvalService;
    private final RolloutPolicyService rolloutPolicyService;
    private final ReflectionService reflectionService;
    private final AdminKcManagementService adminKcManagementService;
    private final AdminPreflightService adminPreflightService;
    private final AdminMisconceptionMiningService adminMisconceptionMiningService;
    private final AdminVariantReviewService adminVariantReviewService;
    private final WorkflowCheckpointService workflowCheckpointService;
    private final BetaFeatureRegistry betaFeatureRegistry;
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;
    @Autowired(required = false)
    private com.alethicode.service.aitutor.observability.AgentTraceRecorder agentTraceRecorder;
    @Autowired(required = false)
    private com.alethicode.service.aitutor.AiTraceService aiTraceService;
    private WorkflowRealtimeSupport workflowRealtimeSupport;

    @Autowired
    public AITutorWorkflowAdminServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiModelGateway aiModelGateway,
            com.alethicode.service.rag.RagServiceClient ragServiceClient,
            AlethicodeProperties properties,
            ReflectionService reflectionService,
            AdminKcManagementService adminKcManagementService,
            AdminPreflightService adminPreflightService,
            AdminMisconceptionMiningService adminMisconceptionMiningService,
            AdminVariantReviewService adminVariantReviewService,
            WorkflowCheckpointService workflowCheckpointService,
            RolloutPolicyService rolloutPolicyService,
            BetaFeatureRegistry betaFeatureRegistry,
            AiCircuitBreaker aiCircuitBreaker
    ) {
        this(
                jdbcTemplate,
                objectMapper,
                aiModelGateway,
                ragServiceClient,
                properties,
                new TransitionPolicy(),
                new CardSchemaValidator(new CardSchemaRegistry()),
                new CoursewareRetrievalService(jdbcTemplate),
                new MasteryService(jdbcTemplate),
                new LearnerMemoryService(jdbcTemplate, objectMapper),
                new CrossCourseProfileService(jdbcTemplate),
                null,
                new SimilarErrorRetrievalService(jdbcTemplate, ragServiceClient),
                new LanguageRoutedExecutionTraceService(
                        new PythonExecutionTraceService(aiModelGateway),
                        new JudgeBackedExecutionTraceService(jdbcTemplate, objectMapper, properties, aiCircuitBreaker)
                ),
                new TutorActionPolicy(),
                new ContextualBanditReranker(),
                new TraceGradeService(),
                new AITutorEvalService(),
                new OffPolicyEvalService(),
                rolloutPolicyService,
                reflectionService,
                adminKcManagementService,
                adminPreflightService,
                adminMisconceptionMiningService,
                adminVariantReviewService,
                workflowCheckpointService,
                betaFeatureRegistry
        );
    }

    AITutorWorkflowAdminServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiModelGateway aiModelGateway,
            com.alethicode.service.rag.RagServiceClient ragServiceClient,
            AlethicodeProperties properties,
            TransitionPolicy transitionPolicy,
            CardSchemaValidator cardSchemaValidator,
            CoursewareRetrievalService coursewareRetrievalService,
            MasteryService masteryService,
            LearnerMemoryService learnerMemoryService,
            CrossCourseProfileService crossCourseProfileService,
            LearnerProfileProjector learnerProfileProjector,
            SimilarErrorRetrievalService similarErrorRetrievalService,
            ExecutionTraceService executionTraceService,
            TutorActionPolicy tutorActionPolicy,
            ContextualBanditReranker contextualBanditReranker,
            TraceGradeService traceGradeService,
            AITutorEvalService aiTutorEvalService,
            OffPolicyEvalService offPolicyEvalService,
            RolloutPolicyService rolloutPolicyService,
            ReflectionService reflectionService,
            AdminKcManagementService adminKcManagementService,
            AdminPreflightService adminPreflightService,
            AdminMisconceptionMiningService adminMisconceptionMiningService,
            AdminVariantReviewService adminVariantReviewService,
            WorkflowCheckpointService workflowCheckpointService,
            BetaFeatureRegistry betaFeatureRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiTutorProblemLanguageNormalizer = new AiTutorProblemLanguageNormalizer(objectMapper);
        this.aiModelGateway = aiModelGateway;
        this.properties = properties;
        this.transitionPolicy = transitionPolicy;
        this.cardSchemaValidator = cardSchemaValidator;
        this.coursewareRetrievalService = coursewareRetrievalService;
        this.masteryService = masteryService;
        this.learnerMemoryService = learnerMemoryService;
        this.crossCourseProfileService = crossCourseProfileService;
        this.learnerProfileProjector = learnerProfileProjector == null
                ? new LearnerProfileProjector(
                        jdbcTemplate,
                        objectMapper,
                        masteryService,
                        learnerMemoryService,
                        new com.alethicode.service.aitutor.profile.LearnerMemorySemanticRetrievalService(
                                jdbcTemplate, ragServiceClient, objectMapper),
                        // ragServiceClient injected at line above replaces 16-dim aiModelGateway path
                        new com.alethicode.service.aitutor.profile.LearnerNarrativeSummaryService(
                                jdbcTemplate, aiModelGateway, learnerMemoryService, objectMapper),
                        crossCourseProfileService)
                : learnerProfileProjector;
        this.similarErrorRetrievalService = similarErrorRetrievalService;
        this.executionTraceService = executionTraceService;
        this.evidencePackAssembler = new EvidencePackAssembler(jdbcTemplate, objectMapper, coursewareRetrievalService, similarErrorRetrievalService);
        this.tutorActionPolicy = tutorActionPolicy;
        this.contextualBanditReranker = contextualBanditReranker;
        this.traceGradeService = traceGradeService;
        this.aiTutorEvalService = aiTutorEvalService;
        this.offPolicyEvalService = offPolicyEvalService;
        this.rolloutPolicyService = rolloutPolicyService;
        this.reflectionService = reflectionService;
        this.adminKcManagementService = adminKcManagementService;
        this.adminPreflightService = adminPreflightService;
        this.adminMisconceptionMiningService = adminMisconceptionMiningService;
        this.adminVariantReviewService = adminVariantReviewService;
        this.workflowCheckpointService = workflowCheckpointService;
        this.betaFeatureRegistry = betaFeatureRegistry;
    }

    @Autowired(required = false)
    void setWorkflowRealtimeSupport(WorkflowRealtimeSupport workflowRealtimeSupport) {
        this.workflowRealtimeSupport = workflowRealtimeSupport;
    }

    public ApiResponse<Object> ideateSkeleton(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }

        Map<String, Object> problemRecord = loadProblemRecord(problemId);
        Map<String, Object> result = new LinkedHashMap<>(generateSkeletonByLlm(
                buildProblemContext(problemRecord),
                LanguageAwareTutorContext.from(request, Map.of(), problemRecord)
        ));
        result.put("session_id", trimToEmpty(stringValue(request.get("session_id"))));
        return ApiResponse.success(result);
    }

    public ApiResponse<Object> workflowSessionGet(Map<String, String> params, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String sessionId = trimToNull(params.get("session_id"));
        Long problemId = parseLong(params.get("problem_id"));
        if (sessionId == null && problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "session_id or problem_id is required");
        }

        Map<String, Object> session = findWorkflowSession(user.userId(), sessionId, problemId, false);
        if (session == null) {
            return ApiResponse.success(null);
        }
        String sid = stringValue(session.get("session_id"));
        List<Map<String, Object>> events = jdbcTemplate.query(
                """
                select event_type, event_data::text as event_data, created_at
                from ai_workflow_event
                where session_id = ?
                order by created_at asc
                limit 100
                """,
                (rs, rowNum) -> {
                    Map<String, Object> e = parseJsonMap(rs.getString("event_data"));
                    e.put("_event_type", rs.getString("event_type"));
                    e.put("_created_at", formatTime(rs.getTimestamp("created_at")));
                    return e;
                },
                sid
        );
        Map<String, Object> payload = workflowSessionPayload(session);
        payload.put("execution_trace", events);
        payload.put("available_actions", availableActions(trimToEmpty(stringValue(session.get("phase"))), trimToEmpty(stringValue(session.get("pending_human_action")))));
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> workflowSessionCreate(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }

        Map<String, Object> existed = findWorkflowSession(user.userId(), null, problemId, true);
        boolean created = false;
        if (existed == null) {
            created = true;
            String sessionId = randomId(32);
            String threadId = randomId(32);
            jdbcTemplate.update(
                    """
                    insert into ai_workflow_session(session_id, thread_id, user_id, problem_id, phase,
                                                    node_outputs, behavior_metrics, pending_human_action,
                                                    last_safe_response, submission_id, is_active, created_at, updated_at)
                    values (?, ?, ?, ?, 'READING', cast(? as jsonb), cast(? as jsonb), '', '', '', true, now(), now())
                    """,
                    sessionId,
                    threadId,
                    user.userId(),
                    problemId,
                    "{}",
                    "{}"
            );
            saveCheckpoint(sessionId, "审题引导", Map.of("phase", "READING", "node_outputs", Map.of()));
            existed = findWorkflowSession(user.userId(), sessionId, null, false);
        }
        Map<String, Object> payload = workflowSessionPayload(existed);
        payload.put("created", created);
        payload.put("available_actions", availableActions(trimToEmpty(stringValue(existed.get("phase"))), trimToEmpty(stringValue(existed.get("pending_human_action")))));
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> workflowSessionDelete(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }
        Map<String, Object> active = findWorkflowSession(user.userId(), null, problemId, true);
        if (active == null) {
            return ApiResponse.success(Map.of("cleared", false, "reason", "no_active_session"));
        }
        String sid = stringValue(active.get("session_id"));
        jdbcTemplate.update("delete from ai_workflow_event where session_id = ?", sid);
        jdbcTemplate.update("delete from ai_workflow_checkpoint where session_id = ?", sid);
        jdbcTemplate.update("update ai_workflow_session set is_active = false, updated_at = now() where session_id = ?", sid);
        return ApiResponse.success(Map.of("cleared", true));
    }

    public ApiResponse<Object> workflowEvent(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }
        String event = normalizeWorkflowEvent(stringValue(request.get("event")));
        if (event == null || !WORKFLOW_EVENTS.contains(event)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "event must be one of: " + String.join(", ", WORKFLOW_EVENTS));
        }
        String sessionId = trimToNull(stringValue(request.get("session_id")));
        Map<String, Object> eventData = castMap(request.get("event_data"));
        boolean asyncMode = parseBoolean(request.get("async"));
        Map<String, Object> session = ensureWorkflowSession(user, sessionId, problemId, authentication);
        sessionId = stringValue(session.get("session_id"));

        if (asyncMode) {
            if (workflowRealtimeSupport == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "workflow realtime support is unavailable");
            }
            if (workflowRealtimeSupport.hasRunningTask(sessionId)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "workflow task is already running");
            }
            String finalSessionId = sessionId;
            final Future<?>[] taskHolder = new Future<?>[1];
            Future<?> task = workflowRealtimeSupport.submitTrackedTask(finalSessionId, () -> {
                workflowRealtimeSupport.broadcastEvent(finalSessionId,
                        com.alethicode.service.aitutor.contract.ServerEvent.TASK_STARTED,
                        com.alethicode.service.aitutor.contract.RuntimeContract.builder()
                                .sessionId(finalSessionId)
                                .runtimeState(com.alethicode.service.aitutor.contract.RuntimeState.RUNNING)
                                .clientEvent(event)
                                .build(),
                        Map.of("node", event.toLowerCase(Locale.ROOT), "ts", nowIso()));
                try {
                    ApiResponse<Object> response = processWorkflowEvent(user, problemId, finalSessionId, event, eventData);
                    if ((taskHolder[0] != null && !workflowRealtimeSupport.isTaskActive(finalSessionId, taskHolder[0]))
                            || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    workflowRealtimeSupport.broadcastEvent(finalSessionId,
                            com.alethicode.service.aitutor.contract.ServerEvent.TASK_COMPLETED,
                            com.alethicode.service.aitutor.contract.RuntimeContract.builder()
                                    .sessionId(finalSessionId)
                                    .runtimeState(com.alethicode.service.aitutor.contract.RuntimeState.COMPLETED)
                                    .build(),
                            Map.of("data", response.data()));
                } catch (Exception exception) {
                    log.error("Workflow event failed: session={}, event={}, error={}", finalSessionId, event, exception.getMessage(), exception);
                    if ((taskHolder[0] != null && !workflowRealtimeSupport.isTaskActive(finalSessionId, taskHolder[0]))
                            || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    workflowRealtimeSupport.broadcastEvent(finalSessionId,
                            com.alethicode.service.aitutor.contract.ServerEvent.TASK_FAILED,
                            com.alethicode.service.aitutor.contract.RuntimeContract.builder()
                                    .sessionId(finalSessionId)
                                    .runtimeState(com.alethicode.service.aitutor.contract.RuntimeState.FAILED)
                                    .failureBucket(com.alethicode.service.aitutor.contract.FailureBucket.SYSTEM_ERROR)
                                    .build(),
                            Map.of("data", Map.of(
                                    "session_id", finalSessionId,
                                    "phase", trimToEmpty(stringValue(session.get("phase"))),
                                    "error", trimToEmpty(exception.getMessage())
                            )));
                }
            });
            taskHolder[0] = task;
            return ApiResponse.success(Map.of("task_id", randomId(24), "session_id", sessionId, "status", "dispatched"));
        }

        return processWorkflowEvent(user, problemId, sessionId, event, eventData);
    }

    private Map<String, Object> ensureWorkflowSession(UserAuth user, String sessionId, Long problemId, Authentication authentication) {
        Map<String, Object> session = findWorkflowSession(user.userId(), sessionId, problemId, true);
        if (session == null) {
            ApiResponse<Object> created = workflowSessionCreate(Map.of("problem_id", problemId), authentication);
            if (created.error() != null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", trimToEmpty(created.error()));
            }
            Map<String, Object> data = castMap(created.data());
            String createdSessionId = stringValue(data.get("session_id"));
            return findWorkflowSession(user.userId(), createdSessionId, null, true);
        }
        return session;
    }

    private ApiResponse<Object> processWorkflowEvent(
            UserAuth user,
            Long problemId,
            String sessionId,
            String event,
            Map<String, Object> eventData
    ) {
        return processWorkflowEvent(user, problemId, sessionId, event, eventData, false);
    }

    private ApiResponse<Object> processWorkflowEvent(
            UserAuth user,
            Long problemId,
            String sessionId,
            String event,
            Map<String, Object> eventData,
            boolean skipTransitionPolicy
    ) {
        long startedAtMs = System.currentTimeMillis();
        Map<String, Object> session = findWorkflowSession(user.userId(), sessionId, problemId, true);
        String currentPhase = normalizeWorkflowPhase(stringValue(session.get("phase")));
        String currentPending = trimToEmpty(stringValue(session.get("pending_human_action")));
        if (!skipTransitionPolicy) {
            transitionPolicy.validateOrThrow(currentPhase, event, currentPending, eventData);
        }

        boolean auxiliaryEvent = "CHAT".equals(event) || "AGENT_FEEDBACK".equals(event) || "KNOWLEDGE_REVIEW".equals(event);
        String nextPhase = auxiliaryEvent ? currentPhase : event;
        int latencyMs = 1;
        Map<String, Object> behaviorMetrics = mergeBehaviorMetrics(castMap(session.get("behavior_metrics")), event, eventData, latencyMs);
        java.util.concurrent.CompletableFuture<Void> memoryRefreshFuture =
                java.util.concurrent.CompletableFuture.runAsync(() ->
                        learnerMemoryService.refreshFromSources(user.userId(), problemId));
        java.util.concurrent.CompletableFuture<LearnerState> profileFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        learnerProfileProjector.project(user.userId(), problemId, behaviorMetrics, currentPhase));

        LearnerState learnerState;
        try {
            memoryRefreshFuture.join();
            learnerState = profileFuture.join();
        } catch (java.util.concurrent.CompletionException ce) {
            Throwable cause = ce.getCause();
            throw cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
        }
        EvidencePack evidencePack = evidencePackAssembler.assemble(user.userId(), problemId, sessionId, currentPhase, event, session, eventData, learnerState);
        Map<String, Object> nodeOutputs = castMap(session.get("node_outputs"));
        nodeOutputs.put("last_event", Map.of("event", event, "event_data", eventData, "ts", nowIso()));

        String pending = "";

        Map<String, Object> guardrail = new LinkedHashMap<>();
        guardrail.put("passed", true);
        guardrail.put("reason", "ok");
        String safeResponse = null;
        String contentProbe = trimToEmpty(stringValue(eventData.get("message"))).toLowerCase(Locale.ROOT);
        if (contentProbe.contains("答案") || contentProbe.contains("answer")) {
            guardrail.put("passed", false);
            guardrail.put("reason", "leakage_risk");
            safeResponse = "我先不直接给答案，我们先定位关键步骤。";
        }

        String submissionId = trimToEmpty(stringValue(eventData.get("submission_id")));
        if (submissionId.isBlank()) {
            submissionId = trimToEmpty(stringValue(session.get("submission_id")));
        }

        CardType cardType = cardTypeByEvent(event);
        LearnerState decisionLearnerState = learnerState;
        boolean schemaPass = true;
        String schemaError = "";

        com.alethicode.service.aitutor.observability.AgentTraceContext traceContext = null;
        com.alethicode.service.aitutor.observability.AgentTraceRecorder.SpanHandle dispatchSpan = null;
        String traceId = null;
        if (agentTraceRecorder != null && aiTraceService != null) {
            traceId = aiTraceService.generateTraceId();
            Map<String, Object> dispatchMeta = new LinkedHashMap<>();
            dispatchMeta.put("event", event);
            dispatchMeta.put("phase", currentPhase);
            dispatchMeta.put("problem_id", problemId);
            dispatchMeta.put("user_id", user.userId());
            dispatchMeta.put("scope", "phase_output_schema_validation");
            dispatchSpan = agentTraceRecorder.startSpan(
                    traceId,
                    com.alethicode.service.aitutor.observability.AgentTraceRecorder.SpanType.DISPATCH,
                    sessionId,
                    null,
                    dispatchMeta
            );
            traceContext = new com.alethicode.service.aitutor.observability.AgentTraceContext(
                    agentTraceRecorder, traceId, sessionId, dispatchSpan.spanId());
        }

        try {
            if (!"AGENT_FEEDBACK".equals(event)) {
                applyPhaseOutput(nodeOutputs, event, currentPhase, eventData, problemId, user,
                        evidencePack, learnerState, traceContext);
            }
            cardType = resolveCardTypeForEvent(event, nodeOutputs);
            if (cardType != null) {
                cardSchemaValidator.validateOrThrow(cardType, castMap(nodeOutputs.get(cardType.outputKey())));
            }
            if (dispatchSpan != null) {
                agentTraceRecorder.endSpanOk(dispatchSpan,
                        "phase output ok: event=" + event + ", phase=" + currentPhase);
            }
        } catch (Exception exception) {
            schemaPass = false;
            schemaError = trimToEmpty(exception.getMessage());
            if (dispatchSpan != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("error", schemaError);
                payload.put("card_type", cardType == null ? "" : cardType.messageType());
                agentTraceRecorder.endSpanFailed(dispatchSpan,
                        "phase output failed: " + schemaError, payload);
            }
            persistSchemaViolationTraceInNewTransaction(
                    sessionId,
                    nextPhase,
                    event,
                    evidencePack,
                    learnerState,
                    guardrail,
                    cardType,
                    schemaError
            );
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", schemaError.isBlank() ? "schema violation" : schemaError);
        }

        TutorActionDecision ruleDecision = tutorActionPolicy.decide(nextPhase, pending, decisionLearnerState);
        BanditDecision banditDecision = contextualBanditReranker.rerank(nextPhase, ruleDecision, decisionLearnerState);
        TutorActionDecision banditCandidateDecision = promoteDecision(ruleDecision, banditDecision);
        Map<String, Object> rolloutMetrics = new LinkedHashMap<>(aiTutorEvalService.summarize(
                traceGradeService.grade(evidencePack, guardrail, schemaPass, banditCandidateDecision)
        ));
        boolean banditEnabled = isBanditEnabled();
        OffPolicyEvalResult opeResult = banditEnabled
                ? offPolicyEvalService.evaluate(nextPhase, banditDecision.chosenAction(), loadHistoricalBanditSamples(nextPhase))
                : new OffPolicyEvalResult(false, 0, 0.0, 0.0, "bandit disabled");
        rolloutMetrics.put("bandit_enabled", banditEnabled);
        rolloutMetrics.put("ope_eligible", opeResult.eligible());
        rolloutMetrics.put("ope_score", opeResult.estimatedReward());
        rolloutMetrics.put("ope_sample_size", opeResult.sampleSize());
        rolloutMetrics.put("bandit_action", banditDecision.chosenAction());
        rolloutMetrics.put("bandit_propensity", banditDecision.chosenPropensity());
        RolloutDecision rolloutDecision = rolloutPolicyService.evaluate(
                "workflow",
                sessionId + ":" + event,
                rolloutMetrics
        );
        TutorActionDecision actionDecision = "gray".equals(rolloutDecision.rolloutMode()) ? banditCandidateDecision : ruleDecision;
        Map<String, Object> traceGrade = traceGradeService.grade(evidencePack, guardrail, schemaPass, actionDecision);
        Map<String, Object> evalSummary = new LinkedHashMap<>(aiTutorEvalService.summarize(traceGrade));
        evalSummary.put("bandit_enabled", banditEnabled);
        evalSummary.put("ope_eligible", opeResult.eligible());
        evalSummary.put("ope_score", opeResult.estimatedReward());
        evalSummary.put("ope_sample_size", opeResult.sampleSize());
        evalSummary.put("bandit_action", banditDecision.chosenAction());
        evalSummary.put("bandit_propensity", banditDecision.chosenPropensity());
        evalSummary.put("rollout_mode", rolloutDecision.rolloutMode());
        latencyMs = Math.max(1, (int) (System.currentTimeMillis() - startedAtMs));
        behaviorMetrics.put("latency_ms", latencyMs);
        if ("ERROR_FEEDBACK".equals(event)) {
            compressPhaseSummary(nodeOutputs, learnerState);
        }
        if ("ERROR_FEEDBACK".equals(currentPhase) && "CODING".equals(event)) {
            generatePhaseTransitionSummary(nodeOutputs, learnerState);
        }

        jdbcTemplate.update(
                """
                update ai_workflow_session
                set phase = ?,
                    node_outputs = cast(? as jsonb),
                    behavior_metrics = cast(? as jsonb),
                    pending_human_action = ?,
                    last_safe_response = ?,
                    submission_id = ?,
                    updated_at = now()
                where session_id = ?
                """,
                nextPhase,
                toJson(nodeOutputs),
                toJson(behaviorMetrics),
                pending,
                safeResponse,
                submissionId,
                sessionId
        );

        jdbcTemplate.update(
                """
                insert into ai_workflow_event(session_id, event_type, event_data, created_at)
                values (?, ?, cast(? as jsonb), now())
                """,
                sessionId,
                event,
                toJson(eventData)
        );

        learnerProfileProjector.persistSnapshot(user.userId(), problemId, sessionId, learnerState);
        evidencePackAssembler.persistRetrievalLogs(sessionId, problemId, nextPhase, evidencePack);
        if ("ERROR_FEEDBACK".equals(event) || "AC_REVIEW".equals(event)) {
            String memorySummary = "phase=" + nextPhase + "; event=" + event
                    + "; action=" + actionDecision.recommendedAction()
                    + "; schema_pass=" + schemaPass;
            learnerMemoryService.onEventCompleted(user.userId(), problemId, event, memorySummary);
        }
        persistGenerationLog(sessionId, nextPhase, cardType, evidencePack, nodeOutputs, schemaPass);
        persistRolloutDecision(sessionId + ":" + event, rolloutDecision);
        Map<String, Object> traceDecision = new LinkedHashMap<>(actionDecision.toMap());
        traceDecision.put("rule_action", ruleDecision.recommendedAction());
        traceDecision.put("shadow_action", banditDecision.chosenAction());
        traceDecision.put("logged_action", actionDecision.recommendedAction());
        traceDecision.put("propensity", "gray".equals(rolloutDecision.rolloutMode()) ? banditDecision.chosenPropensity() : 1.0);
        traceDecision.put("reward", evalSummary.get("reward"));
        traceDecision.put("bandit_reason", banditDecision.reason());
        traceDecision.put("bandit_probabilities", banditDecision.probabilities());
        traceDecision.put("ope", opeResult.toMap());
        traceDecision.putAll(buildOrchestrationDecision(event, evidencePack, cardType));
        persistTrace(
                sessionId,
                nextPhase,
                event,
                "ok",
                evidencePack,
                learnerState,
                traceDecision,
                guardrail,
                Map.of(
                        "schema_pass", schemaPass,
                        "card_type", cardType == null ? "" : cardType.messageType()
                ),
                ""
        );
        persistEvalArtifacts(nextPhase, cardType, evidencePack, traceDecision, guardrail, traceGrade, evalSummary, rolloutDecision);

        Map<String, Object> checkpointChannel = new LinkedHashMap<>();
        checkpointChannel.put("phase", nextPhase);
        checkpointChannel.put("node_outputs", nodeOutputs);
        checkpointChannel.put("behavior_metrics", behaviorMetrics);
        checkpointChannel.put("pending_human_action", pending);
        checkpointChannel.put("last_safe_response", safeResponse);
        checkpointChannel.put("submission_id", submissionId);
        saveCheckpoint(sessionId, labelByEvent(event, nextPhase, nodeOutputs), checkpointChannel);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", sessionId);
        response.put("phase", nextPhase);
        response.put("node_outputs", nodeOutputs);
        response.put("guardrail_result", guardrail);
        response.put("safe_response", safeResponse);
        response.put("pending_human_action", pending);
        response.put("error", "");
        response.put("latency_ms", latencyMs);
        response.put("execution_trace", buildAgentExecutionTrace(nodeOutputs, event, nextPhase, eventData));
        response.put("available_actions", actionDecision.availableActions());
        response.put("structured_trace", List.of(Map.of("node", nextPhase.toLowerCase(Locale.ROOT), "ok", true)));
        response.put("cost_summary", Map.of("tokens", 0, "usd", 0.0));
        response.put("behavior_metrics", behaviorMetrics);
        response.put("submission_id", submissionId);
        response.put("evidence_pack_summary", evidencePack.toSummary());
        response.put("context_snapshot", evidencePack.contextSnapshot());
        response.put("learner_state", learnerState.toMap());
        response.put("tutor_action_decision", actionDecision.toMap());
        response.put("trace_grade", traceGrade);
        response.put("rollout_policy", rolloutDecision.toMap());
        response.put("orchestration_context", evidencePack.orchestration());
        return ApiResponse.success(response);
    }

    public ApiResponse<Object> workflowCheckpointList(Map<String, String> params, Authentication authentication) {
        return workflowCheckpointService.workflowCheckpointList(params, authentication);
    }

    public ApiResponse<Object> workflowCheckpointRestore(Map<String, Object> request, Authentication authentication) {
        return workflowCheckpointService.workflowCheckpointRestore(request, authentication);
    }

    public ApiResponse<Object> workflowInterrupt(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String sessionId = trimToNull(stringValue(request.get("session_id")));
        String action = trimToNull(stringValue(request.get("action")));
        if (sessionId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "session_id is required");
        }
        if (action == null || !("confirm".equals(action) || "reject".equals(action) || "modify".equals(action))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "action must be one of: confirm, reject, modify");
        }
        Map<String, Object> session = findWorkflowSession(user.userId(), sessionId, null, true);
        if (session == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Active session not found");
        }
        String pending = trimToEmpty(stringValue(session.get("pending_human_action")));
        if (pending.isBlank()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "No pending human action");
        }
        Map<String, Object> userData = castMap(request.get("data"));
        jdbcTemplate.update(
                """
                insert into ai_workflow_event(session_id, event_type, event_data, created_at)
                values (?, 'human_interrupt', cast(? as jsonb), now())
                """,
                sessionId,
                toJson(Map.of("action", action, "pending_was", pending, "user_data", userData))
        );
        jdbcTemplate.update("update ai_workflow_session set pending_human_action = '', updated_at = now() where session_id = ?", sessionId);

        String phase = normalizeWorkflowPhase(stringValue(session.get("phase")));
        if ("confirm".equals(action)) {
            Map<String, Object> nodeOutputs = castMap(session.get("node_outputs"));
            nodeOutputs.put("human_interrupt", Map.of("action", action, "data", userData));
            jdbcTemplate.update(
                    "update ai_workflow_session set node_outputs = cast(? as jsonb), updated_at = now() where session_id = ?",
                    toJson(nodeOutputs),
                    sessionId
            );
            return ApiResponse.success(Map.of(
                    "session_id", sessionId,
                    "phase", phase,
                    "node_outputs", nodeOutputs,
                    "execution_trace", List.of(Map.of("action", action, "ts", nowIso())),
                    "available_actions", availableActions(phase, "")
            ));
        }
        return ApiResponse.success(Map.of(
                "session_id", sessionId,
                "phase", phase,
                "action_taken", action,
                "available_actions", availableActions(phase, "")
        ));
    }

    public ApiResponse<Object> adminVariantReview(Map<String, String> params, Authentication authentication) {
        return adminVariantReviewService.adminVariantReview(params, authentication);
    }

    public ApiResponse<Object> adminVariantApprove(String problemId, Map<String, Object> request, Authentication authentication) {
        return adminVariantReviewService.adminVariantApprove(problemId, request, authentication);
    }

    public ApiResponse<Object> adminVariantReject(String problemId, Authentication authentication) {
        return adminVariantReviewService.adminVariantReject(problemId, authentication);
    }

    public ApiResponse<Object> adminKcList(Map<String, String> params, Authentication authentication) {
        return adminKcManagementService.adminKcList(params, authentication);
    }

    public ApiResponse<Object> adminKcDetailUpdate(String kcId, Map<String, Object> request, Authentication authentication) {
        return adminKcManagementService.adminKcDetailUpdate(kcId, request, authentication);
    }

    public ApiResponse<Object> adminKcProblems(String kcId, Authentication authentication) {
        return adminKcManagementService.adminKcProblems(kcId, authentication);
    }

    public ApiResponse<Object> adminClassroomChapters(Authentication authentication) {
        return adminKcManagementService.adminClassroomChapters(authentication);
    }

    public ApiResponse<Object> adminPreflightStats(Authentication authentication) {
        return adminPreflightService.adminPreflightStats(authentication);
    }

    public ApiResponse<Object> adminPreflightDiagnose(Map<String, Object> request, Authentication authentication) {
        return adminPreflightService.adminPreflightDiagnose(request, authentication);
    }

    public ApiResponse<Object> adminMcMiningPending(Authentication authentication) {
        return adminMisconceptionMiningService.adminMcMiningPending(authentication);
    }

    public ApiResponse<Object> adminMcMiningApprove(Map<String, Object> request, Authentication authentication) {
        return adminMisconceptionMiningService.adminMcMiningApprove(request, authentication);
    }

    public ApiResponse<Object> adminMcMiningReject(Map<String, Object> request, Authentication authentication) {
        return adminMisconceptionMiningService.adminMcMiningReject(request, authentication);
    }

    public ApiResponse<Object> adminMcMiningMerge(Map<String, Object> request, Authentication authentication) {
        return adminMisconceptionMiningService.adminMcMiningMerge(request, authentication);
    }

    public ApiResponse<Object> adminMcMiningDiscover(Authentication authentication) {
        return adminMisconceptionMiningService.adminMcMiningDiscover(authentication);
    }

    private Map<String, Object> findWorkflowSession(Long userId, String sessionId, Long problemId, boolean onlyActive) {
        String where;
        List<Object> args = new ArrayList<>();
        if (sessionId != null) {
            where = "session_id = ? and user_id = ?";
            args.add(sessionId);
            args.add(userId);
        } else {
            where = "problem_id = ? and user_id = ?";
            args.add(problemId);
            args.add(userId);
        }
        if (onlyActive) {
            where += " and is_active = true";
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select session_id, thread_id, phase, node_outputs::text as node_outputs,
                           behavior_metrics::text as behavior_metrics,
                           pending_human_action, last_safe_response, submission_id,
                           is_active, problem_id
                    from ai_workflow_session
                    where """ + " " + where + " order by updated_at desc limit 1",
                    (rs, rowNum) -> {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("session_id", rs.getString("session_id"));
                        s.put("thread_id", rs.getString("thread_id"));
                        s.put("phase", normalizeWorkflowPhase(rs.getString("phase")));
                        s.put("node_outputs", parseJsonMap(rs.getString("node_outputs")));
                        s.put("behavior_metrics", parseJsonMap(rs.getString("behavior_metrics")));
                        s.put("pending_human_action", rs.getString("pending_human_action"));
                        s.put("last_safe_response", rs.getString("last_safe_response"));
                        s.put("submission_id", rs.getString("submission_id"));
                        s.put("is_active", rs.getBoolean("is_active"));
                        s.put("problem_id", rs.getLong("problem_id"));
                        return s;
                    },
                    args.toArray()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Map<String, Object> workflowSessionPayload(Map<String, Object> session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", stringValue(session.get("session_id")));
        payload.put("thread_id", stringValue(session.get("thread_id")));
        payload.put("phase", normalizeWorkflowPhase(stringValue(session.get("phase"))));
        payload.put("node_outputs", castMap(session.get("node_outputs")));
        payload.put("behavior_metrics", castMap(session.get("behavior_metrics")));
        payload.put("pending_human_action", stringValue(session.get("pending_human_action")));
        payload.put("last_safe_response", stringValue(session.get("last_safe_response")));
        payload.put("submission_id", stringValue(session.get("submission_id")) == null ? "" : stringValue(session.get("submission_id")));
        payload.put("is_active", parseBoolean(session.get("is_active")));
        return payload;
    }

    private List<Map<String, Object>> availableActions(String phase, String pending) {
        return tutorActionPolicy.decide(
                trimToEmpty(phase),
                trimToEmpty(pending),
                new LearnerState(false, Map.of(), List.of(), Map.of(), Map.of(), "low", "low", Map.of(), List.of(), "", true)
        ).availableActions();
    }

    private Map<String, Object> action(String key, String label, Integer agentId, String event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        if (agentId != null) {
            item.put("agent_id", agentId);
        }
        item.put("event", event);
        return item;
    }

    private void applyPhaseOutput(
            Map<String, Object> nodeOutputs,
            String event,
            String currentPhase,
            Map<String, Object> eventData,
            Long problemId,
            UserAuth user,
            EvidencePack evidencePack,
            LearnerState learnerState
    ) {
        applyPhaseOutput(nodeOutputs, event, currentPhase, eventData, problemId, user,
                evidencePack, learnerState, null);
    }

    private void applyPhaseOutput(
            Map<String, Object> nodeOutputs,
            String event,
            String currentPhase,
            Map<String, Object> eventData,
            Long problemId,
            UserAuth user,
            EvidencePack evidencePack,
            LearnerState learnerState,
            com.alethicode.service.aitutor.observability.AgentTraceContext traceContext
    ) {
        Map<String, Object> problemRecord = evidencePack.problem();
        String baseProblemContext = evidencePackAssembler.buildProblemContext(evidencePack);
        String problemContext = enrichProblemContextWithSharedMemory(baseProblemContext, nodeOutputs);
        LanguageAwareTutorContext tutorContext = resolveTutorContext(eventData, evidencePack);
        switch (event) {
            case "READING" -> nodeOutputs.put("problem_guide", buildProblemGuidePayload(problemContext, tutorContext, normalizeMapList(evidencePack.courseware().get("hits"))));
            case "IDEATING" -> {
                String thoughtText = trimToEmpty(stringValue(eventData.get("thought_text")));
                if ("__generate_skeleton__".equals(thoughtText)) {
                    nodeOutputs.put("ideate", generateSkeletonByLlm(problemContext, tutorContext));
                } else {
                    nodeOutputs.put("ideate", generateIdeateByLlm(problemContext, tutorContext, thoughtText));
                }
            }
            case "ERROR_FEEDBACK" -> {
                Map<String, Object> behaviorMetrics = castMap(eventData.get("behavior_metrics"));
                nodeOutputs.put("error_diagnosis", buildErrorDiagnosisPayload(
                        tutorContext,
                        evidencePack.submission(),
                        problemContext,
                        behaviorMetrics,
                        learnerState,
                        normalizeMapList(evidencePack.courseware().get("hits")),
                        normalizeMapList(evidencePack.similarErrors().get("similar_notebook_hits")),
                        normalizeMapList(evidencePack.similarErrors().get("similar_memory_hits")),
                        user.userId(),
                        problemId
                ));
            }
            case "AC_REVIEW" -> nodeOutputs.put("post_ac", buildPostAcPayload(
                    tutorContext,
                    problemContext,
                    trimToEmpty(stringValue(evidencePack.code().get("current_code"))),
                    tutorContext.currentLanguage(),
                    parseInt(stringValue(eventData.get("guidance_level")), 1),
                    normalizeMapList(evidencePack.courseware().get("hits"))
            ));
            case "TRANSFER" -> {
                String nextPracticeDirection = trimToEmpty(stringValue(castMap(nodeOutputs.get("post_ac")).get("next_practice_direction")));
                nodeOutputs.put("transfer", generateTransferProblem(problemId, user.userId(), tutorContext.currentLanguage(), nextPracticeDirection));
            }
            case "CHAT" -> nodeOutputs.put("chat", buildChatPayload(
                    tutorContext,
                    currentPhase,
                    problemContext,
                    trimToEmpty(stringValue(eventData.get("message"))),
                    trimToEmpty(stringValue(evidencePack.code().get("current_code"))),
                    castMap(eventData.get("behavior_metrics")),
                    nodeOutputs,
                    castMap(nodeOutputs.get("chat")),
                    learnerState
            ));
            case "KNOWLEDGE_REVIEW" -> nodeOutputs.put("knowledge_review", buildKnowledgeReviewPayload(
                    tutorContext,
                    problemContext,
                    learnerState,
                    normalizeMapList(evidencePack.courseware().get("hits"))
            ));
            default -> {
            }
        }
        if (("CODING".equals(event) || "ERROR_FEEDBACK".equals(event)) && parseBoolean(eventData.get("request_execution_trace"))) {
            nodeOutputs.put("execution_trace_explainer", buildExecutionTraceExplainerPayload(event, evidencePack, problemRecord));
        }
    }

    private Map<String, Object> generateTransferProblem(Long sourceProblemId, Long userId, String currentLanguage, String nextPracticeDirection) {
        if (sourceProblemId == null) {
            throw new IllegalStateException("problem_id is required for transfer");
        }
        if (userId == null) {
            throw new IllegalStateException("user_id is required for transfer");
        }
        String normalizedCurrentLanguage = trimToNull(TutorLanguageSupport.normalizeLanguage(currentLanguage));
        if (normalizedCurrentLanguage == null) {
            throw new IllegalStateException("transfer.current_language is required");
        }
        Map<String, Object> source = loadSourceProblemForTransfer(sourceProblemId);

        String description = trimToEmpty(stringValue(source.get("description")));
        String inputDescription = trimToEmpty(stringValue(source.get("input_description")));
        String outputDescription = trimToEmpty(stringValue(source.get("output_description")));
        StatementSplit split = splitStatementFields(description);
        if (inputDescription.isBlank()) {
            inputDescription = split.inputDescription();
        }
        if (outputDescription.isBlank()) {
            outputDescription = split.outputDescription();
        }
        if (!split.description().isBlank()) {
            description = split.description();
        }
        String hint = trimToEmpty(stringValue(source.get("hint")));
        String displayId = nextTransferDisplayId(stringValue(source.get("_id")), stringValue(source.get("title")));

        String samplesJson = trimToEmpty(stringValue(source.get("samples_json")));
        if (samplesJson.isBlank()) {
            samplesJson = "[]";
        }
        List<Map<String, Object>> sourceSamples = parseTransferSamples(samplesJson, true);
        Map<String, Object> generated = generateTransferByLlmWithRetry(
                source,
                description,
                inputDescription,
                outputDescription,
                hint,
                sourceSamples,
                normalizedCurrentLanguage,
                trimToEmpty(nextPracticeDirection)
        );
        String transferTitle = displayId + " " + requireTransferText(generated, "title");
        String transferDescription = requireTransferText(generated, "description");
        String transferInputDescription = requireTransferText(generated, "input_description");
        String transferOutputDescription = requireTransferText(generated, "output_description");
        String transferHint = requireTransferText(generated, "hint");
        String transferReferenceSolutionCode = requireTransferText(generated, "reference_solution_code");
        List<Map<String, Object>> sampleList = normalizeGeneratedTransferPairs(generated.get("samples"), "samples");
        if (sampleList.isEmpty()) {
            throw new IllegalStateException("transfer.samples must not be empty");
        }
        List<Map<String, Object>> transferTestCases = normalizeGeneratedTransferPairs(generated.get("test_cases"), "test_cases");
        if (transferTestCases.isEmpty()) {
            throw new IllegalStateException("transfer.test_cases must not be empty");
        }
        List<String> generatedTargetKcs = normalizeGeneratedTargetKcs(generated.get("target_kcs"));
        if (generatedTargetKcs.isEmpty()) {
            throw new IllegalStateException("transfer.target_kcs must not be empty");
        }
        String normalizedSamplesJson = toJson(sampleList);
        String languagesJson = trimToEmpty(stringValue(source.get("languages_json")));
        if (languagesJson.isBlank()) {
            throw new IllegalStateException("source.languages_json is required for transfer");
        }
        String templateJson = trimToEmpty(stringValue(source.get("template_json")));
        if (templateJson.isBlank()) {
            templateJson = "{}";
        }
        String statisticInfoJson = trimToEmpty(stringValue(source.get("statistic_info_json")));
        if (statisticInfoJson.isBlank()) {
            statisticInfoJson = "{}";
        }

        Integer sourceTimeLimit = parseInt(stringValue(source.get("time_limit")), 1000);
        Integer sourceMemoryLimit = parseInt(stringValue(source.get("memory_limit")), 256);
        String sourceDifficulty = trimToEmpty(stringValue(source.get("difficulty")));
        if (sourceDifficulty.isBlank()) {
            sourceDifficulty = "Mid";
        }
        List<String> allowedLanguages = parseTransferAllowedLanguages(languagesJson);
        String transferReferenceSolutionLanguage = resolveTransferReferenceSolutionLanguage(allowedLanguages, normalizedCurrentLanguage);
        String transferTestCaseId = "transfer_" + randomId(12).toLowerCase(Locale.ROOT);
        String transferTestCaseScoreJson = buildTransferTestCaseScoreJson(transferTestCases.size());

        Long newProblemId = jdbcTemplate.queryForObject(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    reference_solution_language, reference_solution_code,
                    visible, is_public, difficulty, source, statistic_info,
                    is_ai_generated, ai_source_problem_id, visibility_status, create_time, last_update_time
                ) values (
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, cast(? as jsonb), ?,
                    cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                    ?, ?, false, false, ?, ?, cast(? as jsonb),
                    true, ?, 'student_private', now(), now()
                ) returning id
                """,
                Long.class,
                displayId,
                transferTitle,
                transferDescription,
                transferInputDescription,
                transferOutputDescription,
                normalizedSamplesJson,
                transferTestCaseId,
                transferTestCaseScoreJson,
                transferHint,
                languagesJson,
                templateJson,
                userId,
                Math.max(sourceTimeLimit, 1),
                Math.max(sourceMemoryLimit, 1),
                transferReferenceSolutionLanguage,
                transferReferenceSolutionCode,
                sourceDifficulty,
                "AI-Transfer Temp",
                statisticInfoJson,
                sourceProblemId
        );
        if (newProblemId == null) {
            throw new IllegalStateException("transfer problem insert failed");
        }

        jdbcTemplate.update(
                """
                insert into problem_problem_tags(problem_id, problemtag_id)
                select ?, pt.problemtag_id
                from problem_problem_tags pt
                where pt.problem_id = ?
                on conflict do nothing
                """,
                newProblemId,
                sourceProblemId
        );
        jdbcTemplate.update(
                """
                insert into ai_problem_kc_mapping(problem_id, kc_id, weight, language_pack_id)
                select ?, m.kc_id, m.weight, m.language_pack_id
                from ai_problem_kc_mapping m
                where m.problem_id = ?
                on conflict do nothing
                """,
                newProblemId,
                sourceProblemId
        );
        jdbcTemplate.update(
                """
                insert into language_pack_problem_mapping(language_pack_id, problem_id, generation_log_id, create_time)
                select distinct lpm.language_pack_id, ?, lpm.generation_log_id, now()
                from language_pack_problem_mapping lpm
                where lpm.problem_id = ?
                on conflict do nothing
                """,
                newProblemId,
                sourceProblemId
        );
        writeTransferTestCases(transferTestCaseId, transferTestCases);

        List<String> targetKcs = generatedTargetKcs;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", transferTitle);
        payload.put("description", transferDescription);
        payload.put("input_description", transferInputDescription);
        payload.put("output_description", transferOutputDescription);
        payload.put("samples", sampleList);
        payload.put("test_cases", transferTestCases);
        payload.put("hint", transferHint);
        payload.put("reference_solution_code", transferReferenceSolutionCode);
        payload.put("target_kcs", targetKcs);
        payload.put("problem_display_id", displayId);
        payload.put("temporary_problem", true);
        payload.put("ai_tutor_enabled", false);
        return payload;
    }

    private Map<String, Object> loadSourceProblemForTransfer(Long sourceProblemId) {
        try {
            Map<String, Object> source = jdbcTemplate.queryForObject(
                    """
                    select id, _id, title, description, input_description, output_description,
                           samples::text as samples_json, hint, difficulty, time_limit, memory_limit,
                           languages::text as languages_json, template::text as template_json,
                           test_case_id, test_case_score::text as test_case_score_json,
                           reference_solution_language, reference_solution_code,
                           statistic_info::text as statistic_info_json,
                           visibility_status
                    from problem
                    where id = ?
                    limit 1
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("_id", rs.getString("_id"));
                        row.put("title", rs.getString("title"));
                        row.put("description", rs.getString("description"));
                        row.put("input_description", rs.getString("input_description"));
                        row.put("output_description", rs.getString("output_description"));
                        row.put("samples_json", rs.getString("samples_json"));
                        row.put("hint", rs.getString("hint"));
                        row.put("difficulty", rs.getString("difficulty"));
                        row.put("time_limit", rs.getInt("time_limit"));
                        row.put("memory_limit", rs.getInt("memory_limit"));
                        row.put("languages_json", rs.getString("languages_json"));
                        row.put("template_json", rs.getString("template_json"));
                        row.put("test_case_id", rs.getString("test_case_id"));
                        row.put("test_case_score_json", rs.getString("test_case_score_json"));
                        row.put("reference_solution_language", rs.getString("reference_solution_language"));
                        row.put("reference_solution_code", rs.getString("reference_solution_code"));
                        row.put("statistic_info_json", rs.getString("statistic_info_json"));
                        row.put("visibility_status", rs.getString("visibility_status"));
                        return row;
                    },
                    sourceProblemId
            );
            if (source == null) {
                throw new IllegalStateException("Problem not found: " + sourceProblemId);
            }
            AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalizedLanguage =
                    aiTutorProblemLanguageNormalizer.normalize(
                            trimToEmpty(stringValue(source.get("visibility_status"))),
                            trimToEmpty(stringValue(source.get("statistic_info_json"))),
                            trimToEmpty(stringValue(source.get("languages_json"))),
                            trimToEmpty(stringValue(source.get("template_json")))
                    );
            source.put("languages_json", toJson(normalizedLanguage.languages()));
            source.put("template_json", toJson(normalizedLanguage.fullTemplates()));
            return source;
        } catch (EmptyResultDataAccessException ignored) {
            throw new IllegalStateException("Problem not found: " + sourceProblemId);
        }
    }

    private Map<String, Object> generateTransferByLlm(
            Map<String, Object> source,
            String description,
            String inputDescription,
            String outputDescription,
            String hint,
            List<Map<String, Object>> sourceSamples,
            String preferredReferenceSolutionLanguage,
            int attempt,
            String previousError,
            String nextPracticeDirection
    ) {
        Map<String, Object> sourcePayload = buildTransferSourcePayload(
                source,
                description,
                inputDescription,
                outputDescription,
                hint,
                sourceSamples,
                preferredReferenceSolutionLanguage
        );

        return aiModelGateway.callForJson(
                buildTransferSystemPrompt(),
                buildTransferUserPrompt(sourcePayload, attempt, previousError, nextPracticeDirection)
        );
    }

    private Map<String, Object> buildTransferSourcePayload(
            Map<String, Object> source,
            String description,
            String inputDescription,
            String outputDescription,
            String hint,
            List<Map<String, Object>> sourceSamples,
            String preferredReferenceSolutionLanguage
    ) {
        Map<String, Object> sourcePayload = new LinkedHashMap<>();
        sourcePayload.put("source_problem_id", source.get("id"));
        sourcePayload.put("source_display_id", source.get("_id"));
        sourcePayload.put("source_title", source.get("title"));
        sourcePayload.put("description", description);
        sourcePayload.put("input_description", inputDescription);
        sourcePayload.put("output_description", outputDescription);
        String normalizedHint = trimToNull(hint);
        sourcePayload.put("source_hint", normalizedHint == null ? "" : normalizedHint);
        sourcePayload.put("source_hint_available", normalizedHint != null);
        sourcePayload.put("samples", sourceSamples);
        List<String> allowedLanguages = parseTransferAllowedLanguages(trimToEmpty(stringValue(source.get("languages_json"))));
        sourcePayload.put("allowed_languages", allowedLanguages);
        sourcePayload.put("preferred_reference_solution_language", resolveTransferReferenceSolutionLanguage(allowedLanguages, preferredReferenceSolutionLanguage));
        return sourcePayload;
    }

    private String buildTransferSystemPrompt() {
        return """
                你是 OJ 出题助手。目标用户是非计算机专业的编程初学者。
                请基于源题生成一题“举一反三”迁移题，要求：
                1) 不得复用原题原文，必须是可区分的新题；
                2) 难度比源题提升一个层级，但仍严格限定在源题所属考纲知识范围内，保留核心知识点；
                3) 如果用户提供了"下一步练习方向"，必须以该方向作为迁移题的核心设计依据，确保新题重点考察该方向涉及的知识点或技能变体；
                4) 题目设计必须至少包含 1 个需要学生主动思考的关键点（如边界分析、条件构造、状态转移或反例辨析），不能变成纯模板套用；
                5) description / input_description / output_description 三段都必须非空，且 description 仅写任务背景与目标，不要混入“输入/输出”小节标题；
                6) 题面字段完整，输出严格 JSON 对象，不要输出额外文本；
                7) samples 必须至少 1 组，input/output 均为字符串；
                8) target_kcs 必须提供 1~5 个知识点名称；
                9) hint 必须为非空字符串，且至少给出 1 条具体、可执行、适合初学者的解题提示；
                10) 即使源题 hint 为空，也必须自行生成新的 hint，不能返回空字符串、空白字符串或省略该字段；
                11) 若你在题目中设置了输出精度要求（例如“保留两位小数”），必须在 output_description 中明确写出，且样例输出与该精度要求严格一致。
                12) reference_solution_code 必须为完整、可直接运行的参考解代码，语言使用 preferred_reference_solution_language；
                13) test_cases 必须为用于判题的测试点数组，至少 1 组，input/output 均为字符串，并且必须与题面、samples、reference_solution_code 完全一致，不得复用源题测试点。
                14) 若题面要求按「千位数是X…百位数是Y…」等形式输出各位数字，则每位「是」后必须是单个数字 0-9，或带负号的合法形式（如千位数为 -1 时写作「千位数是-1」）；禁止「千位数是-」后仅接逗号、分号或换行（孤立的负号）；samples 与 test_cases 的 output 必须与 reference_solution_code 在同一组输入下算出的结果一致。
                15) 禁止「百位位数」「个位位数」等重复「位」字的量词错误，应写作「百位数」「个位数」。
                返回 JSON 结构：
                {
                  "title": "不含显示ID前缀",
                  "description": "题目描述（可含HTML）",
                  "input_description": "输入说明",
                  "output_description": "输出说明",
                  "hint": "提示",
                  "samples": [{"input":"...","output":"..."}],
                  "test_cases": [{"input":"...","output":"..."}],
                  "reference_solution_code": "完整参考解代码",
                  "target_kcs": ["KC1","KC2"]
                }
                """;
    }

    private String buildTransferUserPrompt(Map<String, Object> sourcePayload, int attempt, String previousError, String nextPracticeDirection) {
        StringBuilder prompt = new StringBuilder("请基于以下源题信息生成迁移题 JSON。\n");
        String normalizedDirection = trimToNull(nextPracticeDirection);
        if (normalizedDirection != null) {
            prompt.append("学生在 AC 复盘时收到的下一步练习方向为：")
                    .append(normalizedDirection)
                    .append("。请基于此方向设计迁移题，使新题重点考察该方向对应的知识点或技能。\n");
        }
        if (Boolean.FALSE.equals(sourcePayload.get("source_hint_available"))) {
            prompt.append("源题没有可复用的 hint，你必须自行补出新的非空 hint，且要具体、可执行、适合初学者。\n");
        }
        String normalizedError = trimToNull(previousError);
        if (attempt > 1 && normalizedError != null) {
            String missingField = extractTransferValidationField(normalizedError);
            if (missingField != null) {
                prompt.append("上一次返回缺少字段：")
                        .append(missingField)
                        .append("。本次必须补齐非空 ")
                        .append(missingField)
                        .append("，只返回完整 JSON。\n");
            } else {
                prompt.append("上一次返回不符合 JSON 契约：")
                        .append(normalizedError)
                        .append("。本次必须严格补齐并只返回完整 JSON。\n");
            }
        }
        prompt.append("源题信息如下：\n").append(toJson(sourcePayload));
        return prompt.toString();
    }

    private Map<String, Object> generateTransferByLlmWithRetry(
            Map<String, Object> source,
            String description,
            String inputDescription,
            String outputDescription,
            String hint,
            List<Map<String, Object>> sourceSamples,
            String preferredReferenceSolutionLanguage,
            String nextPracticeDirection
    ) {
        IllegalStateException lastException = null;
        String previousError = null;
        for (int attempt = 1; attempt <= TRANSFER_GENERATION_MAX_ATTEMPTS; attempt++) {
            Map<String, Object> generated = Map.of();
            try {
                generated = generateTransferByLlm(
                        source,
                        description,
                        inputDescription,
                        outputDescription,
                        hint,
                        sourceSamples,
                        preferredReferenceSolutionLanguage,
                        attempt,
                        previousError,
                        nextPracticeDirection
                );
                validateGeneratedTransferPayload(generated);
                return generated;
            } catch (IllegalStateException exception) {
                lastException = exception;
                previousError = trimToEmpty(exception.getMessage());
                logTransferGenerationFailure(attempt, previousError, generated);
            }
        }
        String detail = lastException == null ? "unknown" : trimToEmpty(lastException.getMessage());
        throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "类似题生成失败，请重试（" + detail + "）");
    }

    private void logTransferGenerationFailure(int attempt, String errorMessage, Map<String, Object> generated) {
        String missingField = extractTransferValidationField(errorMessage);
        String payloadPreview = generated == null || generated.isEmpty()
                ? ""
                : truncateForLog(toJson(generated), 600);
        if (!payloadPreview.isEmpty()) {
            log.warn(
                    "transfer generation payload invalid, attempt {}/{}, field={}, error={}, llm_json={}",
                    attempt,
                    TRANSFER_GENERATION_MAX_ATTEMPTS,
                    missingField == null ? "unknown" : missingField,
                    trimToEmpty(errorMessage),
                    payloadPreview
            );
            return;
        }
        log.warn(
                "transfer generation payload invalid, attempt {}/{}, field={}, error={}",
                attempt,
                TRANSFER_GENERATION_MAX_ATTEMPTS,
                missingField == null ? "unknown" : missingField,
                trimToEmpty(errorMessage)
        );
    }

    private String extractTransferValidationField(String errorMessage) {
        String normalized = trimToNull(errorMessage);
        if (normalized == null) {
            return null;
        }
        Matcher matcher = TRANSFER_VALIDATION_FIELD_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private String truncateForLog(String raw, int maxLength) {
        String normalized = trimToEmpty(raw).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private void validateGeneratedTransferPayload(Map<String, Object> generated) {
        requireTransferText(generated, "title");
        requireTransferText(generated, "description");
        requireTransferText(generated, "input_description");
        requireTransferText(generated, "output_description");
        requireTransferText(generated, "hint");
        requireTransferText(generated, "reference_solution_code");
        List<Map<String, Object>> samples = normalizeGeneratedTransferPairs(generated.get("samples"), "samples");
        if (samples.isEmpty()) {
            throw new IllegalStateException("transfer.samples must not be empty");
        }
        List<Map<String, Object>> testCases = normalizeGeneratedTransferPairs(generated.get("test_cases"), "test_cases");
        if (testCases.isEmpty()) {
            throw new IllegalStateException("transfer.test_cases must not be empty");
        }
        List<String> targetKcs = normalizeGeneratedTargetKcs(generated.get("target_kcs"));
        if (targetKcs.isEmpty()) {
            throw new IllegalStateException("transfer.target_kcs must not be empty");
        }
        validateTransferSamplesAndTestCasesSemantic(samples, testCases);
    }

    /**
     * Fail-fast checks on LLM-generated I/O text: catch common digit-place formatting bugs before persisting.
     */
    private void validateTransferSamplesAndTestCasesSemantic(
            List<Map<String, Object>> samples,
            List<Map<String, Object>> testCases
    ) {
        validateTransferPairOutputsSemantic(samples, "samples");
        validateTransferPairOutputsSemantic(testCases, "test_cases");
    }

    private void validateTransferPairOutputsSemantic(List<Map<String, Object>> pairs, String fieldName) {
        for (int i = 0; i < pairs.size(); i++) {
            String output = trimToNull(stringValue(pairs.get(i).get("output")));
            if (output == null) {
                continue;
            }
            assertTransferOutputSemantic(fieldName, i, output);
        }
    }

    private void assertTransferOutputSemantic(String fieldName, int index, String output) {
        if (TRANSFER_DIGIT_PLACE_LONE_MINUS.matcher(output).find()) {
            throw new IllegalStateException(
                    "transfer." + fieldName + "[" + index + "].output invalid: "
                            + "各位数字说明中「是-」后必须紧跟数字（如千位数是-1），禁止单独「千位数是-」后接逗号、换行或结束"
            );
        }
        if (TRANSFER_DIGIT_PLACE_DUP_WEI.matcher(output).find()) {
            throw new IllegalStateException(
                    "transfer." + fieldName + "[" + index + "].output invalid: "
                            + "禁止「位位」等重复量词（如「百位位数」应改为「百位数」）"
            );
        }
    }

    private String requireTransferText(Map<String, Object> payload, String key) {
        String value = trimToNull(stringValue(payload.get(key)));
        if (value == null) {
            throw new IllegalStateException("transfer." + key + " is required");
        }
        return value;
    }

    private List<Map<String, Object>> normalizeGeneratedTransferPairs(Object rawPairs, String fieldName) {
        if (!(rawPairs instanceof List<?> list)) {
            throw new IllegalStateException("transfer." + fieldName + " must be an array");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String input = trimToNull(stringValue(map.get("input")));
            String output = trimToNull(stringValue(map.get("output")));
            if (input == null || output == null) {
                continue;
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("input", input);
            one.put("output", output);
            result.add(one);
        }
        return result;
    }

    private List<String> normalizeGeneratedTargetKcs(Object rawTargetKcs) {
        if (!(rawTargetKcs instanceof List<?> list)) {
            throw new IllegalStateException("transfer.target_kcs must be an array");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String value = trimToNull(stringValue(item));
            if (value == null) {
                continue;
            }
            if (!result.contains(value)) {
                result.add(value);
            }
            if (result.size() >= 5) {
                break;
            }
        }
        return result;
    }

    private String nextTransferDisplayId(String sourceDisplayId, String sourceTitle) {
        TransferDisplayIdRule rule = resolveTransferDisplayIdRule(sourceDisplayId, sourceTitle);
        int nextSuffix = nextTransferDisplaySuffix(rule);
        while (nextSuffix <= rule.maxSuffix()) {
            String candidate = formatTransferDisplayId(rule, nextSuffix);
            if (!displayIdExists(candidate)) {
                return candidate;
            }
            if (nextSuffix == Integer.MAX_VALUE) {
                break;
            }
            nextSuffix++;
        }
        throw new IllegalStateException("transfer display_id exhausted for prefix: " + rule.prefix());
    }

    private TransferDisplayIdRule resolveTransferDisplayIdRule(String sourceDisplayId, String sourceTitle) {
        String normalizedDisplayId = trimToEmpty(sourceDisplayId);
        Matcher chapterMatcher = SOURCE_DISPLAY_CHAPTER_PATTERN.matcher(normalizedDisplayId);
        if (chapterMatcher.matches()) {
            return new TransferDisplayIdRule(
                    chapterMatcher.group(1) + "." + chapterMatcher.group(2),
                    ".",
                    3,
                    999
            );
        }
        String normalizedTitle = trimToEmpty(sourceTitle);
        Matcher titleMatcher = SOURCE_TITLE_CHAPTER_PREFIX_PATTERN.matcher(normalizedTitle);
        if (titleMatcher.matches()) {
            return new TransferDisplayIdRule(
                    titleMatcher.group(1) + "." + titleMatcher.group(2),
                    ".",
                    3,
                    999
            );
        }
        Matcher genericMatcher = SOURCE_DISPLAY_GENERIC_PREFIX_PATTERN.matcher(normalizedDisplayId);
        if (genericMatcher.matches()) {
            return new TransferDisplayIdRule(
                    genericMatcher.group(1),
                    "-T",
                    0,
                    999
            );
        }
        if (!normalizedDisplayId.isBlank()) {
            return new TransferDisplayIdRule(
                    normalizedDisplayId,
                    "-T",
                    0,
                    999
            );
        }
        throw new IllegalStateException(
                "source display_id must map to chapter prefix (from display_id or title), got display_id: "
                        + normalizedDisplayId + ", title: " + normalizedTitle
        );
    }

    private String formatTransferDisplayId(TransferDisplayIdRule rule, int suffix) {
        if (rule.suffixWidth() > 0) {
            return rule.prefix() + rule.separator() + String.format(Locale.ROOT, "%0" + rule.suffixWidth() + "d", suffix);
        }
        return rule.prefix() + rule.separator() + suffix;
    }

    private int nextTransferDisplaySuffix(TransferDisplayIdRule rule) {
        Pattern suffixPattern = Pattern.compile("^" + Pattern.quote(rule.prefix()) + Pattern.quote(rule.separator()) + "(\\d+)$");
        List<String> existingDisplayIds = jdbcTemplate.queryForList(
                "select _id from problem where _id like ?",
                String.class,
                rule.prefix() + rule.separator() + "%"
        );
        int maxSuffix = 0;
        for (String existingDisplayId : existingDisplayIds) {
            if (existingDisplayId == null) {
                continue;
            }
            Matcher matcher = suffixPattern.matcher(existingDisplayId);
            if (!matcher.matches()) {
                continue;
            }
            int suffix = parseInt(matcher.group(1), 0);
            if (suffix > maxSuffix) {
                maxSuffix = suffix;
            }
        }
        int next = Math.max(maxSuffix + 1, 1);
        if (next > rule.maxSuffix()) {
            throw new IllegalStateException("transfer display_id exhausted for prefix: " + rule.prefix());
        }
        return next;
    }

    private boolean displayIdExists(String displayId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from problem where _id = ?",
                Integer.class,
                displayId
        );
        return count != null && count > 0;
    }

    private record TransferDisplayIdRule(String prefix, String separator, int suffixWidth, int maxSuffix) {
    }

    private List<Map<String, Object>> parseTransferSamples(String samplesJson, boolean allowInvalidCases) {
        List<Object> raw = parseJsonList(samplesJson);
        List<Map<String, Object>> samples = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            if (!allowInvalidCases && isInvalidTransferCase(map)) {
                continue;
            }
            String input = "";
            String output = "";
            Object inObj = map.get("input");
            Object outObj = map.get("output");
            if (inObj != null) {
                input = String.valueOf(inObj);
            }
            if (outObj != null) {
                output = String.valueOf(outObj);
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("input", input);
            one.put("output", output);
            samples.add(one);
        }
        return samples;
    }

    private boolean statementMentionsInvalidHandling(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(part);
            }
        }
        return INVALID_HANDLING_PATTERN.matcher(text.toString()).find();
    }

    private boolean isInvalidTransferCase(Map<?, ?> caseMap) {
        String caseType = trimToEmpty(stringValue(caseMap.get("case_type"))).toLowerCase(Locale.ROOT);
        if ("invalid".equals(caseType) || "illegal".equals(caseType) || "malformed".equals(caseType)) {
            return true;
        }
        String markerText = (trimToEmpty(stringValue(caseMap.get("note"))) + "\n"
                + trimToEmpty(stringValue(caseMap.get("desc"))) + "\n"
                + trimToEmpty(stringValue(caseMap.get("description"))) + "\n"
                + trimToEmpty(stringValue(caseMap.get("explanation")))).trim();
        String outputText = trimToEmpty(stringValue(caseMap.get("output")));
        return INVALID_CASE_PATTERN.matcher(markerText).find() || INVALID_CASE_PATTERN.matcher(outputText).find();
    }

    private List<String> loadTargetKcsForTransfer(Long problemId) {
        List<String> names = jdbcTemplate.query(
                """
                select kc.name
                from ai_problem_kc_mapping m
                join ai_knowledge_component kc on kc.id = m.kc_id
                where m.problem_id = ?
                order by kc.id
                """,
                (rs, rowNum) -> rs.getString("name"),
                problemId
        );
        if (!names.isEmpty()) {
            return names;
        }
        return jdbcTemplate.query(
                """
                select t.name
                from problem_problem_tags pt
                join problem_tag t on t.id = pt.problemtag_id
                where pt.problem_id = ?
                order by t.id
                limit 5
                """,
                (rs, rowNum) -> rs.getString("name"),
                problemId
        );
    }

    private StatementSplit splitStatementFields(String rawText) {
        String text = trimToEmpty(rawText).replace("\r\n", "\n");
        if (text.isBlank()) {
            return new StatementSplit("", "", "");
        }
        int inPos = findLastMarker(text, List.of("输入格式：", "输入格式:", "输入说明：", "输入说明:"));
        int outPos = findLastMarker(text, List.of("输出格式：", "输出格式:", "输出说明：", "输出说明:"));
        if (inPos < 0 && outPos < 0) {
            return new StatementSplit(text, "", "");
        }

        int first = inPos >= 0 && outPos >= 0 ? Math.min(inPos, outPos) : Math.max(inPos, outPos);
        String description = text.substring(0, Math.max(first, 0)).trim();
        String input = "";
        String output = "";

        if (inPos >= 0 && outPos >= 0) {
            if (inPos < outPos) {
                input = extractAfterMarkerUntil(text, inPos, List.of("输入格式：", "输入格式:", "输入说明：", "输入说明:"), outPos);
                output = extractAfterMarkerUntil(text, outPos, List.of("输出格式：", "输出格式:", "输出说明：", "输出说明:"), text.length());
            } else {
                output = extractAfterMarkerUntil(text, outPos, List.of("输出格式：", "输出格式:", "输出说明：", "输出说明:"), inPos);
                input = extractAfterMarkerUntil(text, inPos, List.of("输入格式：", "输入格式:", "输入说明：", "输入说明:"), text.length());
            }
        } else if (inPos >= 0) {
            input = extractAfterMarkerUntil(text, inPos, List.of("输入格式：", "输入格式:", "输入说明：", "输入说明:"), text.length());
        } else {
            output = extractAfterMarkerUntil(text, outPos, List.of("输出格式：", "输出格式:", "输出说明：", "输出说明:"), text.length());
        }
        return new StatementSplit(description.isBlank() ? text : description, input.trim(), output.trim());
    }

    private int findLastMarker(String text, List<String> markers) {
        int best = -1;
        for (String marker : markers) {
            int pos = text.lastIndexOf(marker);
            if (pos > best) {
                best = pos;
            }
        }
        return best;
    }

    private String extractAfterMarkerUntil(String text, int markerPos, List<String> markers, int endExclusive) {
        int markerLength = 0;
        for (String marker : markers) {
            if (text.startsWith(marker, markerPos)) {
                markerLength = marker.length();
                break;
            }
        }
        int start = markerPos + markerLength;
        if (start >= endExclusive || start >= text.length()) {
            return "";
        }
        return text.substring(start, Math.min(endExclusive, text.length())).trim();
    }

    private Map<String, Object> loadProblemRecord(Long problemId) {
        if (problemId == null) {
            throw new IllegalStateException("题目 ID 不能为空");
        }
        Map<String, Object> row = jdbcTemplate.query(
                """
                select p.id, p.title, p.description, p.input_description, p.output_description,
                       p.samples, p.hint, p.source, p.reference_solution_code, p.reference_solution_language,
                       p.languages::text as languages_json, p.template::text as template_json,
                       lpm.language_pack_id, lp.primary_language as language_pack_primary_language
                from problem p
                left join language_pack_problem_mapping lpm on lpm.problem_id = p.id
                left join language_pack lp on lp.id = lpm.language_pack_id
                where p.id = ?
                limit 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("title", trimToEmpty(rs.getString("title")));
                    item.put("description", trimToEmpty(rs.getString("description")));
                    item.put("input_description", trimToEmpty(rs.getString("input_description")));
                    item.put("output_description", trimToEmpty(rs.getString("output_description")));
                    item.put("samples", trimToEmpty(rs.getString("samples")));
                    item.put("hint", trimToEmpty(rs.getString("hint")));
                    item.put("source", trimToEmpty(rs.getString("source")));
                    item.put("reference_solution_code", trimToEmpty(rs.getString("reference_solution_code")));
                    item.put("reference_solution_language", trimToEmpty(rs.getString("reference_solution_language")));
                    item.put("languages", TutorLanguageSupport.parseLanguageList(rs.getString("languages_json")));
                    item.put("template", trimToEmpty(rs.getString("template_json")));
                    item.put("language_pack_id", rs.getObject("language_pack_id"));
                    item.put("language_pack_primary_language", trimToEmpty(rs.getString("language_pack_primary_language")));
                    return item;
                },
                problemId
        );
        if (row == null) {
            throw new IllegalStateException("Problem not found: " + problemId);
        }
        return row;
    }

    private String loadProblemContext(Long problemId) {
        return buildProblemContext(loadProblemRecord(problemId));
    }

    private LanguageAwareTutorContext resolveTutorContext(Map<String, Object> eventData, EvidencePack evidencePack) {
        return LanguageAwareTutorContext.from(
                eventData == null ? Map.of() : eventData,
                mergeLanguageSources(evidencePack.code(), evidencePack.submission()),
                evidencePack.problem()
        );
    }

    private Map<String, Object> mergeLanguageSources(Map<String, Object> code, Map<String, Object> submission) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(submission == null ? Map.of() : submission);
        merged.putAll(code == null ? Map.of() : code);
        return merged;
    }

    private String buildProblemContext(Map<String, Object> row) {
        return """
                题目ID: %s
                标题: %s
                题目描述: %s
                输入描述: %s
                输出描述: %s
                样例: %s
                提示: %s
                来源: %s
                """.formatted(
                stringValue(row.get("id")),
                trimToEmpty(stringValue(row.get("title"))),
                trimToEmpty(stringValue(row.get("description"))),
                trimToEmpty(stringValue(row.get("input_description"))),
                trimToEmpty(stringValue(row.get("output_description"))),
                trimToEmpty(stringValue(row.get("samples"))),
                trimToEmpty(stringValue(row.get("hint"))),
                trimToEmpty(stringValue(row.get("source")))
        );
    }

    private Map<String, Object> buildProblemGuidePayload(String problemContext,
                                                        LanguageAwareTutorContext tutorContext,
                                                        List<Map<String, Object>> coursewareRefs) {
        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                %s
                你必须严格返回 JSON 对象，不要输出额外文本，不要直接给答案。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 审题导学助手", tutorContext)),
                """
                【题目上下文】
                %s

                请输出 JSON：
                {
                  "plain_task": "用一句话说明题目要做什么",
                  "problem_explanation": "把题意翻译成人话",
                  "input_translation": "输入怎么理解",
                  "output_translation": "输出怎么理解",
                  "approach_direction": "先做什么更稳",
                  "warmup_question": "一个帮助学生进入思考的问题",
                  "courseware_refs": []
                }
                """.formatted(problemContext)
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plain_task", requireNonBlank(raw, "plain_task"));
        result.put("problem_explanation", requireNonBlank(raw, "problem_explanation"));
        result.put("input_translation", requireNonBlank(raw, "input_translation"));
        result.put("output_translation", requireNonBlank(raw, "output_translation"));
        result.put("approach_direction", requireNonBlank(raw, "approach_direction"));
        result.put("warmup_question", requireNonBlank(raw, "warmup_question"));
        result.put("courseware_refs", coursewareRefs.isEmpty() ? normalizeMapList(raw.get("courseware_refs")) : coursewareRefs);
        return result;
    }

    /**
     * 生成「帮我回顾相关知识点」卡片 payload。
     *
     * 语义：围绕当前题目触达到的薄弱 KC（{@link LearnerState#weakKcs()}）做一次 KC 级回顾，
     * 不是审题导读，也不直接给代码答案。失败时直接抛出异常，由上层 Plan 兜底，不做静默降级。
     */
    private Map<String, Object> buildKnowledgeReviewPayload(LanguageAwareTutorContext tutorContext,
                                                           String problemContext,
                                                           LearnerState learnerState,
                                                           List<Map<String, Object>> coursewareRefs) {
        List<String> weakKcs = learnerState == null || learnerState.weakKcs() == null
                ? List.of()
                : learnerState.weakKcs();
        Map<String, Double> masteryByKc = learnerState == null || learnerState.masteryByKc() == null
                ? Map.of()
                : learnerState.masteryByKc();

        List<Map<String, Object>> focusKcs = new ArrayList<>();
        for (String kc : weakKcs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", kc);
            Double mastery = masteryByKc.get(kc);
            item.put("mastery", mastery == null ? 0.0 : mastery);
            focusKcs.add(item);
        }
        if (focusKcs.isEmpty()) {
            throw new IllegalStateException(
                    "KNOWLEDGE_REVIEW requires at least one weak knowledge concept; learnerState has none");
        }

        String focusBullets = focusKcs.stream()
                .map(k -> "- " + k.get("name") + "（当前掌握度 " + Math.round(((Number) k.get("mastery")).doubleValue() * 100) + "%）")
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                %s
                围绕学生的薄弱知识点做一次简明回顾。只讲知识点本身（定义 + 典型用法 + 初学常见坑），
                不要直接给当前题目的完整代码答案，不要把知识点讲成与题目毫无关联的通识。
                你必须严格返回 JSON 对象，不要输出额外文本。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 知识点回顾助手", tutorContext)),
                """
                【题目上下文】
                %s

                【学生当前薄弱知识点（按掌握度从低到高）】
                %s

                请生成一段 Markdown 回顾，帮学生把这些知识点串起来，侧重：
                1. 每个知识点用初学者能听懂的一句话解释；
                2. 列 1-2 个最容易踩的坑（结合题目类型，但不直接解题）；
                3. 结尾给一句鼓励，提醒学生带着这些知识点回去审题。

                请输出 JSON：
                {
                  "reply": "完整的 Markdown 回顾正文（含标题、要点、示例伪代码可选）",
                  "kc_focus": ["按掌握度从低到高列出的薄弱 KC 名称，来自输入"],
                  "courseware_refs": []
                }
                """.formatted(problemContext, focusBullets)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", requireNonBlank(raw, "reply"));
        Object kcFocusRaw = raw.get("kc_focus");
        List<String> kcFocus = new ArrayList<>();
        if (kcFocusRaw instanceof List<?> list) {
            for (Object item : list) {
                String text = trimToNull(stringValue(item));
                if (text != null) {
                    kcFocus.add(text);
                }
            }
        }
        if (kcFocus.isEmpty()) {
            for (Map<String, Object> item : focusKcs) {
                kcFocus.add(String.valueOf(item.get("name")));
            }
        }
        result.put("kc_focus", kcFocus);
        result.put("mastery_snapshot", focusKcs);
        result.put("courseware_refs", coursewareRefs.isEmpty() ? normalizeMapList(raw.get("courseware_refs")) : coursewareRefs);
        return result;
    }

    private Map<String, Object> generateIdeateByLlm(String problemContext,
                                                   LanguageAwareTutorContext tutorContext,
                                                   String thoughtText) {
        String normalizedThought = trimToEmpty(thoughtText);
        if (normalizedThought.isBlank()) {
            throw new IllegalStateException("thought_text is required for ideating");
        }
        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                %s
                只允许基于题目信息和学生思路做引导，不要直接给答案。
                你必须严格返回JSON对象，不要输出额外文本。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 导学助手", tutorContext)),
                """
                【题目上下文】
                %s
                
                【学生思路】
                %s
                
                请输出JSON，字段必须完整：
                {
                  "understood_as": "string，复述学生思路并指出关键目标",
                  "step_plan": ["string", "string", "string"],
                  "has_logic_gap": true/false,
                  "logic_gap_hint": "string，当has_logic_gap为false时给空串",
                  "confidence_level": "low|medium|high"
                }
                """.formatted(problemContext, normalizedThought)
        );

        String understoodAs = trimToNull(stringValue(raw.get("understood_as")));
        if (understoodAs == null) {
            throw new IllegalStateException("LLM response missing understood_as");
        }
        Object planObj = raw.get("step_plan");
        if (!(planObj instanceof List<?> planList) || planList.isEmpty()) {
            throw new IllegalStateException("LLM response missing step_plan");
        }
        List<String> stepPlan = new ArrayList<>();
        for (Object item : planList) {
            String step = trimToNull(stringValue(item));
            if (step != null) {
                stepPlan.add(step);
            }
        }
        if (stepPlan.isEmpty()) {
            throw new IllegalStateException("LLM response has empty step_plan");
        }
        boolean hasLogicGap = Boolean.TRUE.equals(raw.get("has_logic_gap"));
        String logicGapHint = trimToEmpty(stringValue(raw.get("logic_gap_hint")));
        String confidenceLevel = trimToEmpty(stringValue(raw.get("confidence_level"))).toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONFIDENCE_LEVELS.contains(confidenceLevel)) {
            throw new IllegalStateException("LLM response has invalid confidence_level: " + confidenceLevel);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("understood_as", understoodAs);
        result.put("step_plan", stepPlan);
        result.put("has_logic_gap", hasLogicGap);
        result.put("logic_gap_hint", hasLogicGap ? logicGapHint : "");
        result.put("confidence_level", confidenceLevel);
        return result;
    }

    private Map<String, Object> generateSkeletonByLlm(String problemContext, LanguageAwareTutorContext tutorContext) {
        String currentLanguage = TutorLanguageSupport.templateLanguageLabel(tutorContext.currentLanguage());
        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                %s
                请给出可直接用于练习的 %s 骨架代码，不要直接给完整答案。
                你必须严格返回JSON对象，不要输出额外文本。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 导学助手", tutorContext), currentLanguage),
                """
                【题目上下文】
                %s

                生成骨架时必须遵守：
                1. 保持 %s 在当前 OJ 里的最小自然骨架，只留学生真正要补的 TODO。
                2. 若题目没有明确要求，也不是该语言本身必须的入口结构，不要为了凑模板额外包一层 main、def main()、主函数、启动类。
                3. 不要添加不会实际用到的 import、include、using、package 等库或头文件声明。
                4. **绝对禁止给出完整答案或可直接运行通过的代码**。核心逻辑必须替换为 `# TODO: ...` 注释，让学生自己填写。至少保留 2 个 TODO 占位。如果代码中没有 TODO 注释，视为违规。
                5. description、TODO 注释、行内注释、解释文案默认使用简体中文；只有题目明确要求英文术语、英文输出或语言语法关键字必须英文时，才保留必要英文。
                6. 变量名、函数名、类名等标识符不要翻译成拼音或中文，优先使用符合该语言习惯的清晰英文命名；除非题目明确要求特定标识符，否则不要用 qian、bai、shi、ge 这类拼音命名。
                7. 只提供代码框架结构（输入、变量声明、输出格式），核心算法/逻辑部分用 TODO 替代。

                请输出JSON，字段必须完整：
                {
                  "description": "一句中文简短说明如何使用骨架",
                  "skeleton": "%s 骨架代码，包含中文 TODO 注释和符合语言习惯的英文标识符"
                }
                """.formatted(problemContext, currentLanguage, currentLanguage)
        );
        Map<String, Object> nestedRaw = castMap(raw.get("data"));
        String description = firstNonBlankField(nestedRaw, "description", "summary", "usage", "hint", "guide");
        if (description == null) {
            description = firstNonBlankField(raw, "description", "summary", "usage", "hint", "guide");
        }
        String skeleton = firstNonBlankField(nestedRaw, "skeleton", "skeleton_code", "code");
        if (skeleton == null) {
            skeleton = firstNonBlankField(raw, "skeleton", "skeleton_code", "code");
        }
        if (skeleton == null) {
            log.warn("skeleton generation missing code field, top_level_keys={}, nested_keys={}, raw={}",
                    raw.keySet(),
                    nestedRaw.keySet(),
                    shortenForPrompt(toJson(raw), 400));
            throw new IllegalStateException("LLM response missing skeleton");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", description == null ? "" : description);
        result.put("skeleton", skeleton);
        return result;
    }

    private Map<String, Object> buildPostAcPayload(
            LanguageAwareTutorContext tutorContext,
            String problemContext,
            String code,
            String language,
            int guidanceLevel,
            List<Map<String, Object>> coursewareRefs
    ) {
        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                %s
                请严格返回 JSON，不要输出额外文本。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 学习复盘助手", tutorContext)),
                """
                【题目上下文】
                %s

                【学生代码】
                %s

                【语言】
                %s

                【复盘层级】
                %d

                请输出 JSON：
                {
                  "celebration": "鼓励",
                  "what_you_learned": ["学会了什么"],
                  "key_success_point": "关键成功点",
                  "transfer_tip": "迁移提示",
                  "one_improvement": "一个改进点",
                  "recommended_review": "建议复习点",
                  "next_practice_direction": "下一步方向",
                  "peer_comparison": {
                    "algorithm_diff": "优秀解法差异",
                    "structure_diff": "结构差异",
                    "organization_diff": "组织差异"
                  },
                  "progressive_hints": [
                    {"title":"步骤标题","question":"追问","code_snippet":"示例代码"}
                  ]
                }
                """.formatted(problemContext, code, language, guidanceLevel)
        );
        Map<String, Object> payload = normalizePostAcPayload(raw, guidanceLevel);
        payload.put("courseware_refs", coursewareRefs);
        return payload;
    }

    private Map<String, Object> normalizePostAcPayload(Map<String, Object> raw, int guidanceLevel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("celebration", requireNonBlank(raw, "celebration"));
        payload.put("what_you_learned", normalizeStringList(raw.get("what_you_learned")));
        payload.put("key_success_point", requireNonBlank(raw, "key_success_point"));
        payload.put("transfer_tip", requireNonBlank(raw, "transfer_tip"));
        payload.put("one_improvement", requireNonBlank(raw, "one_improvement"));
        payload.put("recommended_review", requireNonBlank(raw, "recommended_review"));
        payload.put("next_practice_direction", requireNonBlank(raw, "next_practice_direction"));
        if (guidanceLevel >= 3) {
            payload.put("level_2", castMap(raw.get("peer_comparison")));
            payload.put("level_3", Map.of("steps", normalizeMapList(raw.get("progressive_hints"))));
        }
        return payload;
    }

    private Map<String, Object> buildChatPayload(
            LanguageAwareTutorContext tutorContext,
            String currentPhase,
            String problemContext,
            String message,
            String code,
            Map<String, Object> behaviorMetrics,
            Map<String, Object> nodeOutputs,
            Map<String, Object> existingChatPayload,
            LearnerState learnerState
    ) {
        String normalizedMessage = trimToNull(message);
        if (normalizedMessage == null) {
            throw new IllegalStateException("message is required for chat");
        }
        String phaseLabel = switch (trimToEmpty(currentPhase)) {
            case "READING" -> "先把题意和输入输出说清楚";
            case "IDEATING" -> "把步骤拆成 2 到 3 步";
            case "CODING" -> "先盯住当前代码里最可能出错的一处";
            case "ERROR_FEEDBACK" -> "先根据提交结果定位错误类型";
            case "AC_REVIEW" -> "先总结这次通过的关键动作";
            case "TRANSFER" -> "先比较原题和迁移题的共同点";
            default -> "先把当前卡点说具体";
        };
        int consecutiveErrors = parseInt(stringValue(behaviorMetrics.get("consecutiveErrors")), 0);
        String frustration = consecutiveErrors >= 3 ? "你已经连续错了几次，这次只改一个点再验证。" : "我们一步一步来。";
        String codeHint = trimToNull(code) == null ? "先别急着写整段代码。" : "结合你当前代码，优先检查最近改动的那几行。";
        String confidenceHint = learnerState == null ? "" : "当前系统判断你的信心大约是 " + learnerState.confidenceProxy() + "。";

        List<Map<String, Object>> previousHistory = normalizeMapList(existingChatPayload.get("history"));
        List<Map<String, Object>> history = new ArrayList<>(previousHistory);
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", normalizedMessage);
        userMessage.put("phase", trimToEmpty(currentPhase));
        userMessage.put("ts", nowIso());
        history.add(userMessage);
        List<Map<String, Object>> recentHistory = recentChatHistoryForPrompt(history);
        String sharedAgentSummary = buildRecentAgentContextSummary(nodeOutputs);
        String normalizedProblemContext = shortenForPrompt(trimToEmpty(problemContext), 1800);
        String normalizedCode = shortenForPrompt(trimToEmpty(code), MAX_CHAT_CODE_CONTEXT_CHARS);

        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                %s
                你必须结合题目上下文、学生提问、代码快照进行回答。
                严禁直接给出题目最终答案、完整代码、可直接提交的关键表达式。
                如果学生索要答案，必须拒绝并改为分步提示与追问。
                你必须严格返回 JSON 对象，不要输出额外文本。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 导学助手（CHAT 模式）", tutorContext)),
                """
                【当前阶段】
                %s

                【题目上下文】
                %s

                【学生提问】
                %s

                【当前代码快照】
                %s

                【最近对话历史（学生+助手）】
                %s

                【同会话 Agent 上下文摘要】
                %s

                【学习状态提示】
                %s
                %s
                %s

                请输出 JSON：
                {
                  "reply": "给学生的引导回复（2-4句，不能直接给答案）",
                  "focus_point": "本轮聚焦点（不超过20字）",
                  "next_question": "一个推动思考的问题"
                }
                """.formatted(
                        trimToEmpty(currentPhase),
                        normalizedProblemContext,
                        normalizedMessage,
                        normalizedCode.isBlank() ? "（暂无代码）" : normalizedCode,
                        toJson(recentHistory),
                        sharedAgentSummary.isBlank() ? "（暂无）" : sharedAgentSummary,
                        "阶段建议：" + phaseLabel,
                        frustration,
                        codeHint + confidenceHint
                )
        );
        String reply = enforceNoDirectAnswer(requireNonBlank(raw, "reply"), phaseLabel);
        String focusPoint = trimToEmpty(stringValue(raw.get("focus_point")));
        String nextQuestion = trimToEmpty(stringValue(raw.get("next_question")));
        StringBuilder finalReply = new StringBuilder(reply);
        if (!focusPoint.isBlank()) {
            finalReply.append("\n聚焦点：").append(focusPoint);
        }
        if (!nextQuestion.isBlank()) {
            finalReply.append("\n思考题：").append(nextQuestion);
        }

        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", finalReply.toString());
        assistantMessage.put("phase", trimToEmpty(currentPhase));
        assistantMessage.put("ts", nowIso());
        history.add(assistantMessage);
        return Map.of("history", trimChatHistory(history));
    }

    private String enforceNoDirectAnswer(String reply, String phaseLabel) {
        String normalizedReply = trimToNull(reply);
        if (normalizedReply == null) {
            throw new IllegalStateException("chat reply is required");
        }
        if (DIRECT_ANSWER_RISK_PATTERN.matcher(normalizedReply).find()) {
            return "我先不直接给最终答案。当前先做这一步：" + phaseLabel + "。你先尝试一个最小样例，再把你的中间结果发我，我继续给你下一步提示。";
        }
        return normalizedReply;
    }

    private List<Map<String, Object>> recentChatHistoryForPrompt(List<Map<String, Object>> history) {
        if (history.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, history.size() - MAX_CHAT_CONTEXT_WINDOW);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = fromIndex; i < history.size(); i++) {
            Map<String, Object> item = history.get(i);
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("role", trimToEmpty(stringValue(item.get("role"))));
            normalized.put("phase", trimToEmpty(stringValue(item.get("phase"))));
            normalized.put("content", shortenForPrompt(trimToEmpty(stringValue(item.get("content"))), 220));
            result.add(normalized);
        }
        return result;
    }

    private Map<String, Object> buildErrorDiagnosisPayload(
            LanguageAwareTutorContext tutorContext,
            Map<String, Object> submission,
            String problemContext,
            Map<String, Object> behaviorMetrics,
            LearnerState learnerState,
            List<Map<String, Object>> coursewareRefs,
            List<Map<String, Object>> similarNotebookHits,
            List<Map<String, Object>> similarMemoryHits,
            Long userId,
            Long problemId
    ) {
        int result = parseInt(stringValue(submission.get("result")), 0);
        String errorInfo = trimToEmpty(stringValue(castMap(submission.get("statistic_info")).get("err_info")));
        if (errorInfo.isBlank()) {
            errorInfo = trimToEmpty(stringValue(castMap(submission.get("info")).get("err_info")));
        }
        String category = classifyErrorTaxonomy(result, errorInfo);

        Map<String, Object> llmDiagnosis = generateErrorDiagnosisByLlm(
                tutorContext, problemContext, submission, category, result, errorInfo);

        Map<String, Object> evidenceContext = new LinkedHashMap<>();
        evidenceContext.put("problem_context", shortenForPrompt(problemContext, 1500));
        evidenceContext.put("error_info", errorInfo);
        evidenceContext.put("error_taxonomy", category);
        evidenceContext.put("result_code", result);
        ReflectionResult reflectionResult = reflectionService.reflectAndRefine(
                CardType.ERROR_DIAGNOSIS, evidenceContext, llmDiagnosis, 1);
        llmDiagnosis = reflectionResult.output();
        log.debug("ERROR_FEEDBACK reflection: passed={}, rounds={}, verdict={}",
                reflectionResult.passed(), reflectionResult.roundsUsed(),
                reflectionResult.criticVerdict());

        int consecutiveErrors = learnerState == null ? parseInt(stringValue(behaviorMetrics.get("consecutiveErrors")), 0)
                : parseInt(stringValue(learnerState.recentBehavior().get("consecutiveErrors")), 0);
        String frustrationLevel = "low";
        String frustrationEncouragement = "";
        if (consecutiveErrors >= 5) {
            frustrationLevel = "severe";
            frustrationEncouragement = "先停 30 秒，只看最小样例，再继续。";
        } else if (consecutiveErrors >= 3) {
            frustrationLevel = "moderate";
            frustrationEncouragement = "这次只改一个点，再重新提交验证。";
        }

        Map<String, Object> diagnosis = new LinkedHashMap<>();
        Map<String, Object> firstFailedTestCase = buildFirstFailedTestCasePayload(submission);
        diagnosis.put("error_taxonomy", category);
        diagnosis.put("root_cause", requireNonBlank(llmDiagnosis, "root_cause"));
        diagnosis.put("what_program_is_doing", requireNonBlank(llmDiagnosis, "what_program_is_doing"));
        diagnosis.put("expected_behavior", requireNonBlank(llmDiagnosis, "expected_behavior"));
        diagnosis.put("fix_direction", requireNonBlank(llmDiagnosis, "fix_direction"));
        diagnosis.put("related_kcs", requireNonEmptyStringList(llmDiagnosis, "related_kcs"));
        diagnosis.put("encouragement", requireNonBlank(llmDiagnosis, "encouragement"));
        diagnosis.put("hint_level", 1);
        diagnosis.put("misconception_hits", List.of());
        diagnosis.put("misconception_info", List.of());
        diagnosis.put("courseware_refs", coursewareRefs);
        diagnosis.put("frustration_level", frustrationLevel);
        diagnosis.put("frustration_encouragement", frustrationEncouragement);
        diagnosis.put("similar_error_summary", buildSimilarErrorSummary(similarNotebookHits, similarMemoryHits));
        diagnosis.put("similar_error_refs", buildSimilarErrorRefs(similarNotebookHits, similarMemoryHits));
        diagnosis.put("repeat_pattern_detected", !similarNotebookHits.isEmpty() || !similarMemoryHits.isEmpty());
        diagnosis.put("first_failed_test_case", firstFailedTestCase);
        return diagnosis;
    }

    private Map<String, Object> generateErrorDiagnosisByLlm(
            LanguageAwareTutorContext tutorContext,
            String problemContext,
            Map<String, Object> submission,
            String category,
            int resultCode,
            String errorInfo
    ) {
        String code = trimToEmpty(stringValue(submission.get("code")));
        String language = trimToEmpty(stringValue(submission.get("language")));
        String conciseProblemContext = shortenForPrompt(trimToEmpty(problemContext), 2200);
        String conciseCode = shortenForPrompt(code, 2600);
        String failureCaseJson = toJson(extractFailedCaseEvidence(submission));
        List<String> relatedKcCandidates = inferRelatedKcs(problemContext, category);

        return aiModelGateway.callForJson(
                """
                %s
                你必须根据题干、学生错误代码、判题返回的失败样例证据来定位问题。
                禁止给出完整可提交代码或最终答案。
                禁止只给泛化模板句，必须体现本次证据。
                若判题未返回失败样例详情，必须明确写出“判题未返回失败样例详情”。
                你必须严格返回 JSON 对象，不要输出额外文本。
                """.formatted(TutorLanguageSupport.beginnerSystemRole("OJ 错误诊断助手", tutorContext)),
                """
                【题目上下文】
                %s

                【学生错误代码】
                %s

                【语言】
                %s

                【判题结果】
                result_code=%d, result_label=%s, error_taxonomy=%s

                【判题错误信息】
                %s

                【失败样例证据（来自判题返回）】
                %s

                【候选知识点】
                %s

                请输出 JSON：
                {
                  "root_cause": "结合题干+错误代码+失败样例的根因定位",
                  "what_program_is_doing": "程序当前实际行为",
                  "expected_behavior": "题目要求的正确行为",
                  "fix_direction": "只给定位与修复方向，不给完整代码",
                  "related_kcs": ["相关知识点1", "相关知识点2"],
                  "encouragement": "一句鼓励"
                }
                """.formatted(
                        conciseProblemContext,
                        conciseCode.isBlank() ? "（暂无代码）" : conciseCode,
                        language.isBlank() ? tutorContext.currentLanguage() : language,
                        resultCode,
                        judgeResultLabel(resultCode),
                        category,
                        errorInfo.isBlank() ? "（无）" : summarizeErrorInfo(errorInfo),
                        failureCaseJson,
                        relatedKcCandidates
                )
        );
    }

    private Map<String, Object> extractFailedCaseEvidence(Map<String, Object> submission) {
        Map<String, Object> info = castMap(submission.get("info"));
        List<Map<String, Object>> judgeCases = normalizeMapList(info.get("data"));
        for (Map<String, Object> oneCase : judgeCases) {
            int oneResult = parseInt(stringValue(oneCase.get("result")), 0);
            if (oneResult != 0) {
                return compactCaseEvidence(oneCase);
            }
        }
        if (!judgeCases.isEmpty()) {
            return compactCaseEvidence(judgeCases.get(0));
        }
        Map<String, Object> objectiveEvidence = castMap(castMap(submission.get("statistic_info")).get("objective"));
        if (!objectiveEvidence.isEmpty()) {
            return objectiveEvidence;
        }
        return Map.of();
    }

    private Map<String, Object> compactCaseEvidence(Map<String, Object> oneCase) {
        List<String> preferredKeys = List.of(
                "test_case", "result", "error", "input", "output",
                "expected_output", "expected", "actual_output", "actual",
                "stdout", "stderr"
        );
        Map<String, Object> compact = new LinkedHashMap<>();
        for (String key : preferredKeys) {
            Object value = oneCase.get(key);
            if (value == null) {
                continue;
            }
            String textValue = trimToEmpty(stringValue(value));
            if (!textValue.isBlank()) {
                compact.put(key, textValue);
            }
        }
        if (!compact.isEmpty()) {
            return compact;
        }
        for (Map.Entry<String, Object> entry : oneCase.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String value = trimToEmpty(stringValue(entry.getValue()));
            if (!value.isBlank()) {
                compact.put(entry.getKey(), value);
            }
        }
        return compact;
    }

    private Map<String, Object> buildFirstFailedTestCasePayload(Map<String, Object> submission) {
        Map<String, Object> evidence = extractFailedCaseEvidence(submission);
        if (evidence.isEmpty()) {
            return Map.of();
        }
        String input = trimToEmpty(stringValue(evidence.get("input")));
        String expectedOutput = firstNonBlank(
                trimToNull(stringValue(evidence.get("expected_output"))),
                trimToNull(stringValue(evidence.get("output"))),
                trimToNull(stringValue(evidence.get("expected")))
        );
        String actualOutput = firstNonBlank(
                trimToNull(stringValue(evidence.get("actual_output"))),
                trimToNull(stringValue(evidence.get("actual"))),
                trimToNull(stringValue(evidence.get("stdout")))
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        if (!input.isBlank()) {
            payload.put("input", input);
        }
        if (expectedOutput != null) {
            payload.put("expected_output", expectedOutput);
        }
        if (actualOutput != null) {
            payload.put("actual_output", actualOutput);
        }
        return payload;
    }

    private List<String> requireNonEmptyStringList(Map<String, Object> payload, String fieldName) {
        List<String> values = normalizeStringList(payload.get(fieldName));
        if (values.isEmpty()) {
            throw new IllegalStateException("LLM response missing " + fieldName);
        }
        return values;
    }

    private Map<String, Object> buildExecutionTraceExplainerPayload(String event, EvidencePack evidencePack, Map<String, Object> problemRecord) {
        LanguageAwareTutorContext tutorContext = resolveTutorContext(evidencePack.workflow(), evidencePack);
        String inputSample = extractFirstSampleInput(trimToEmpty(stringValue(problemRecord == null ? "" : problemRecord.get("samples"))));
        String failureReason = "";
        if ("ERROR_FEEDBACK".equals(event)) {
            failureReason = trimToEmpty(stringValue(castMap(evidencePack.submission().get("statistic_info")).get("err_info")));
            if (failureReason.isBlank()) {
                failureReason = trimToEmpty(stringValue(castMap(evidencePack.submission().get("info")).get("err_info")));
            }
        }
        return executionTraceService.explain(
                tutorContext,
                trimToEmpty(stringValue(evidencePack.code().get("current_code"))),
                inputSample,
                failureReason,
                evidencePack.submission()
        );
    }

    private String buildSimilarErrorSummary(List<Map<String, Object>> similarNotebookHits, List<Map<String, Object>> similarMemoryHits) {
        List<Map<String, Object>> allHits = new ArrayList<>();
        allHits.addAll(similarNotebookHits);
        allHits.addAll(similarMemoryHits);
        if (allHits.isEmpty()) {
            return "";
        }
        Map<String, Object> topHit = allHits.stream()
                .sorted((left, right) -> Double.compare(toDouble(right.get("score")), toDouble(left.get("score"))))
                .findFirst()
                .orElse(Map.of());
        return "这次问题和你之前的“%s”很相似，建议先回看相同的边界或状态更新位置。"
                .formatted(trimToEmpty(stringValue(topHit.get("summary"))));
    }

    private List<Map<String, Object>> buildSimilarErrorRefs(List<Map<String, Object>> similarNotebookHits, List<Map<String, Object>> similarMemoryHits) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Map<String, Object> hit : similarNotebookHits) {
            refs.add(buildSimilarRef(hit, "notebook"));
        }
        for (Map<String, Object> hit : similarMemoryHits) {
            refs.add(buildSimilarRef(hit, "memory"));
        }
        return refs;
    }

    private Map<String, Object> buildSimilarRef(Map<String, Object> hit, String sourceKind) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("source_kind", sourceKind);
        ref.put("source_id", trimToEmpty(stringValue(hit.get("source_id"))));
        ref.put("summary", trimToEmpty(stringValue(hit.get("summary"))));
        ref.put("score", toDouble(hit.get("score")));
        ref.put("problem_id", hit.get("problem_id"));
        return ref;
    }

    private List<String> parseTransferAllowedLanguages(String languagesJson) {
        List<String> result = new ArrayList<>();
        for (Object item : parseJsonList(languagesJson)) {
            String language = trimToNull(stringValue(item));
            if (language == null) {
                continue;
            }
            if (!result.contains(language)) {
                result.add(language);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("transfer.allowed_languages must not be empty");
        }
        return result;
    }

    private String resolveTransferReferenceSolutionLanguage(List<String> allowedLanguages, String preferredLanguage) {
        if (allowedLanguages.isEmpty()) {
            throw new IllegalStateException("transfer.allowed_languages must not be empty");
        }
        String normalizedPreferredLanguage = trimToNull(TutorLanguageSupport.normalizeLanguage(preferredLanguage));
        if (normalizedPreferredLanguage == null) {
            throw new IllegalStateException("transfer.current_language is required");
        }
        if (!allowedLanguages.contains(normalizedPreferredLanguage)) {
            throw new IllegalStateException("transfer.current_language is not allowed: " + normalizedPreferredLanguage);
        }
        return normalizedPreferredLanguage;
    }

    private String buildTransferTestCaseScoreJson(int count) {
        List<Map<String, Object>> scores = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> score = new LinkedHashMap<>();
            score.put("score", 0);
            score.put("input_name", i + ".in");
            score.put("output_name", i + ".out");
            scores.add(score);
        }
        return toJson(scores);
    }

    private void writeTransferTestCases(String testCaseId, List<Map<String, Object>> testCases) {
        Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
        try {
            Files.createDirectories(testCaseDir);

            Map<String, Object> testCaseInfo = new LinkedHashMap<>();
            testCaseInfo.put("spj", false);
            Map<String, Object> infoCases = new LinkedHashMap<>();
            testCaseInfo.put("test_cases", infoCases);

            for (int index = 0; index < testCases.size(); index++) {
                Map<String, Object> one = testCases.get(index);
                String inputContent = ensureTrailingNewline(normalizeLineEnding(trimToEmpty(stringValue(one.get("input")))));
                String outputContent = ensureTrailingNewline(normalizeLineEnding(trimToEmpty(stringValue(one.get("output")))));
                String inputName = (index + 1) + ".in";
                String outputName = (index + 1) + ".out";
                Files.writeString(testCaseDir.resolve(inputName), inputContent, StandardCharsets.UTF_8);
                Files.writeString(testCaseDir.resolve(outputName), outputContent, StandardCharsets.UTF_8);

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("stripped_output_md5", md5Hex(rstripWhitespace(outputContent.getBytes(StandardCharsets.UTF_8))));
                info.put("input_size", inputContent.getBytes(StandardCharsets.UTF_8).length);
                info.put("output_size", outputContent.getBytes(StandardCharsets.UTF_8).length);
                info.put("input_name", inputName);
                info.put("output_name", outputName);
                infoCases.put(String.valueOf(index + 1), info);
            }

            Files.writeString(
                    testCaseDir.resolve("info"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(testCaseInfo),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            deleteTransferTestCaseDirQuietly(testCaseDir);
            throw new IllegalStateException("transfer test cases write failed: " + exception.getMessage(), exception);
        }
    }

    private void deleteTransferTestCaseDirQuietly(Path testCaseDir) {
        try {
            if (!Files.isDirectory(testCaseDir)) {
                return;
            }
            try (var walk = Files.walk(testCaseDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private String normalizeLineEnding(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String ensureTrailingNewline(String content) {
        if (content.isEmpty() || content.endsWith("\n")) {
            return content;
        }
        return content + "\n";
    }

    private byte[] rstripWhitespace(byte[] content) {
        int end = content.length;
        while (end > 0) {
            byte one = content[end - 1];
            if (one != '\n' && one != '\r' && one != ' ' && one != '\t') {
                break;
            }
            end--;
        }
        byte[] trimmed = new byte[end];
        System.arraycopy(content, 0, trimmed, 0, end);
        return trimmed;
    }

    private String md5Hex(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(content);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte one : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", one));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 unavailable", exception);
        }
    }

    private Map<String, Object> mergeBehaviorMetrics(Map<String, Object> existing, String event, Map<String, Object> eventData, int latencyMs) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> input = castMap(eventData.get("behavior_metrics"));
        for (String key : BEHAVIOR_METRIC_KEYS) {
            Object value = input.containsKey(key) ? input.get(key) : existing.get(key);
            if ("deleteRatio".equals(key)) {
                merged.put(key, parseDoubleObj(value) == null ? 0.0 : parseDoubleObj(value));
            } else {
                merged.put(key, parseInt(stringValue(value), 0));
            }
        }
        merged.put("last_event", event);
        merged.put("latency_ms", latencyMs);
        return merged;
    }

    private void persistTrace(
            String sessionId,
            String phase,
            String event,
            String traceStatus,
            EvidencePack evidencePack,
            LearnerState learnerState,
            Map<String, Object> decision,
            Map<String, Object> guardrail,
            Map<String, Object> schemaValidation,
            String scaffoldLevel
    ) {
        jdbcTemplate.update(
                """
                insert into ai_tutor_trace(
                    session_id, phase, event_type, trace_status, evidence_summary,
                    learner_state, decision, guardrail, schema_validation, scaffold_level, created_at
                )
                values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, now())
                """,
                sessionId,
                phase,
                event,
                traceStatus,
                toJson(evidencePack.toSummary()),
                toJson(learnerState.toMap()),
                toJson(decision),
                toJson(guardrail),
                toJson(schemaValidation),
                scaffoldLevel == null ? "" : scaffoldLevel
        );
    }

    private void persistSchemaViolationTraceInNewTransaction(
            String sessionId,
            String phase,
            String event,
            EvidencePack evidencePack,
            LearnerState learnerState,
            Map<String, Object> guardrail,
            CardType cardType,
            String schemaError
    ) {
        if (transactionManager == null) {
            persistTrace(
                    sessionId,
                    phase,
                    event,
                    "schema_violation",
                    evidencePack,
                    learnerState,
                    Map.of(),
                    guardrail,
                    Map.of(
                            "schema_pass", false,
                            "card_type", cardType == null ? "" : cardType.messageType(),
                            "error", schemaError
                    ),
                    ""
            );
            return;
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> persistTrace(
                sessionId,
                phase,
                event,
                "schema_violation",
                evidencePack,
                learnerState,
                Map.of(),
                guardrail,
                Map.of(
                        "schema_pass", false,
                        "card_type", cardType == null ? "" : cardType.messageType(),
                        "error", schemaError
                ),
                ""
        ));
    }

    private void persistGenerationLog(
            String sessionId,
            String phase,
            CardType cardType,
            EvidencePack evidencePack,
            Map<String, Object> nodeOutputs,
            boolean schemaPass
    ) {
        if (cardType == null) {
            return;
        }
        Map<String, Object> payload = castMap(nodeOutputs.get(cardType.outputKey()));
        String promptHash = md5Hex((phase + ":" + cardType.messageType() + ":" + evidencePack.toSummary()).getBytes(StandardCharsets.UTF_8));
        String evidenceHash = md5Hex(toJson(evidencePack.toSummary()).getBytes(StandardCharsets.UTF_8));
        String modelName = trimToNull(aiModelGateway.readConfigOrDefault("LLM_MODEL", "MiniMax-M2.7"));
        if (modelName == null) {
            modelName = "MiniMax-M2.7";
        }
        jdbcTemplate.update(
                """
                insert into ai_tutor_generation_log(
                    session_id, phase, card_type, model_name, prompt_hash, evidence_hash,
                    request_summary, response_summary, schema_pass, created_at
                )
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, now())
                """,
                sessionId,
                phase,
                cardType.messageType(),
                modelName,
                promptHash,
                evidenceHash,
                toJson(evidencePack.toSummary()),
                toJson(payload),
                schemaPass
        );
    }

    private void persistRolloutDecision(String scopeKey, RolloutDecision rolloutDecision) {
        jdbcTemplate.update(
                """
                insert into ai_rollout_decision(scope_type, scope_key, decision, reason, metrics, created_at)
                values ('workflow', ?, ?, ?, cast(? as jsonb), now())
                """,
                scopeKey,
                rolloutDecision.rolloutMode(),
                rolloutDecision.reason(),
                toJson(rolloutDecision.metrics())
        );
    }

    private void persistEvalArtifacts(
            String phase,
            CardType cardType,
            EvidencePack evidencePack,
            Map<String, Object> decision,
            Map<String, Object> guardrail,
            Map<String, Object> traceGrade,
            Map<String, Object> evalSummary,
            RolloutDecision rolloutDecision
    ) {
        String datasetName = "workflow:" + phase;
        String modelName = trimToNull(aiModelGateway.readConfigOrDefault("LLM_MODEL", "MiniMax-M2.7"));
        if (modelName == null) {
            modelName = "MiniMax-M2.7";
        }
        jdbcTemplate.update(
                """
                insert into ai_eval_dataset(dataset_name, phase, card_type, input_payload, expectation, tags, created_at)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), now())
                """,
                datasetName,
                phase,
                cardType == null ? "" : cardType.messageType(),
                toJson(aiTutorEvalService.buildDatasetInput(evidencePack.toSummary(), decision, guardrail)),
                toJson(aiTutorEvalService.buildExpectation(traceGrade)),
                toJson(List.of(cardType == null ? "" : cardType.messageType(), rolloutDecision.rolloutMode()))
        );
        jdbcTemplate.update(
                """
                insert into ai_eval_run(dataset_name, model_name, variant_name, metrics, created_at)
                values (?, ?, ?, cast(? as jsonb), now())
                """,
                datasetName,
                modelName,
                rolloutDecision.rolloutMode(),
                toJson(evalSummary)
        );
    }

    private boolean isBanditEnabled() {
        String raw = aiModelGateway.readConfigOrDefault("AI_BANDIT_ENABLED", "true");
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(raw);
    }

    private List<Map<String, Object>> loadHistoricalBanditSamples(String phase) {
        return jdbcTemplate.query(
                """
                select phase,
                       coalesce(decision->>'logged_action', '') as logged_action,
                       coalesce(decision->>'propensity', '0') as propensity,
                       coalesce(decision->>'reward', '0') as reward
                from ai_tutor_trace
                where trace_status = 'ok'
                  and phase = ?
                  and jsonb_exists(decision, 'logged_action')
                order by created_at desc
                limit 100
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("phase", rs.getString("phase"));
                    row.put("logged_action", rs.getString("logged_action"));
                    row.put("propensity", rs.getString("propensity"));
                    row.put("reward", rs.getString("reward"));
                    return row;
                },
                phase
        );
    }

    private TutorActionDecision promoteDecision(TutorActionDecision ruleDecision, BanditDecision banditDecision) {
        if (ruleDecision == null || banditDecision == null || banditDecision.chosenAction() == null || banditDecision.chosenAction().isBlank()) {
            return ruleDecision;
        }
        List<Map<String, Object>> reordered = new ArrayList<>();
        Map<String, Object> promoted = null;
        for (Map<String, Object> action : ruleDecision.availableActions()) {
            Map<String, Object> copy = new LinkedHashMap<>(action);
            if (banditDecision.chosenAction().equals(copy.get("key"))) {
                promoted = copy;
            } else {
                reordered.add(copy);
            }
        }
        if (promoted == null) {
            return ruleDecision;
        }
        reordered.addFirst(promoted);
        String reason = ruleDecision.reason();
        if (!banditDecision.reason().isBlank() && !banditDecision.chosenAction().equals(ruleDecision.recommendedAction())) {
            reason = ruleDecision.reason() + "；bandit shadow 认为 " + banditDecision.reason();
        }
        return new TutorActionDecision(
                banditDecision.chosenAction(),
                ruleDecision.confidence(),
                reason,
                reordered
        );
    }

    private CardType cardTypeByEvent(String event) {
        return switch (trimToEmpty(event)) {
            case "READING" -> CardType.PROBLEM_GUIDE;
            case "IDEATING" -> CardType.IDEATE_ANALYSIS;
            case "CODING" -> null;
            case "ERROR_FEEDBACK" -> CardType.ERROR_DIAGNOSIS;
            case "AC_REVIEW" -> CardType.POST_AC;
            case "TRANSFER" -> CardType.TRANSFER_PROBLEM;
            case "CHAT" -> CardType.AI_REPLY;
            case "KNOWLEDGE_REVIEW" -> CardType.KNOWLEDGE_REVIEW;
            case "VISUALIZE" -> CardType.VISUALIZE;
            default -> null;
        };
    }

    private String outputKeyByEvent(String event, String phase) {
        CardType cardType = cardTypeByEvent(event);
        return cardType == null ? "" : cardType.outputKey();
    }

    private Object payloadByEvent(Map<String, Object> nodeOutputs, String event, String phase) {
        String key = outputKeyByEvent(event, phase);
        if (key.isBlank()) {
            return Map.of();
        }
        Object payload = nodeOutputs.getOrDefault(key, Map.of());
        if ("CHAT".equals(event)) {
            List<Map<String, Object>> history = normalizeMapList(castMap(payload).get("history"));
            return history.isEmpty() ? Map.of() : history.get(history.size() - 1);
        }
        return payload;
    }

    private CardType resolveCardTypeForEvent(String event, Map<String, Object> nodeOutputs) {
        return cardTypeByEvent(event);
    }

    private List<Map<String, Object>> buildAgentExecutionTrace(
            Map<String, Object> nodeOutputs,
            String event,
            String phase,
            Map<String, Object> eventData
    ) {
        List<Map<String, Object>> trace = new ArrayList<>();
        CardType cardType = resolveCardTypeForEvent(event, nodeOutputs);
        trace.add(Map.of(
                "type", "agent_output",
                "message_type", cardType == null ? "" : cardType.messageType(),
                "output_key", cardType == null ? outputKeyByEvent(event, phase) : cardType.outputKey(),
                "payload", payloadByEvent(nodeOutputs, event, phase),
                "event", event,
                "ts", nowIso()
        ));
        if (parseBoolean(eventData.get("request_execution_trace")) && nodeOutputs.get("execution_trace_explainer") instanceof Map<?, ?> executionTracePayload) {
            trace.add(Map.of(
                    "type", "agent_output",
                    "message_type", CardType.EXECUTION_TRACE_EXPLAINER.messageType(),
                    "output_key", CardType.EXECUTION_TRACE_EXPLAINER.outputKey(),
                    "payload", executionTracePayload,
                    "event", event,
                    "ts", nowIso()
            ));
        }
        return trace;
    }

    private Map<String, Object> buildOrchestrationDecision(String event, EvidencePack evidencePack, CardType cardType) {
        Map<String, Object> orchestration = new LinkedHashMap<>();
        orchestration.put("primary_agent", primaryAgentByEvent(event, cardType));
        orchestration.put("support_agents", supportAgentsByEvidence(evidencePack));
        orchestration.put("consumed_context_keys", evidencePack.orchestration().getOrDefault("shared_context_keys", List.of()));
        return orchestration;
    }

    private String primaryAgentByEvent(String event, CardType cardType) {
        return switch (trimToEmpty(event)) {
            case "READING" -> "problem_guide";
            case "IDEATING" -> "ideate_analysis";
            case "CODING" -> "";
            case "ERROR_FEEDBACK" -> "error_diagnosis";
            case "AC_REVIEW" -> "post_ac";
            case "TRANSFER" -> "transfer_problem";
            case "CHAT" -> "ai_reply";
            default -> "";
        };
    }

    private List<String> supportAgentsByEvidence(EvidencePack evidencePack) {
        List<String> supportAgents = new ArrayList<>();
        if (((Number) evidencePack.similarErrors().getOrDefault("notebook_hit_count", 0)).intValue() > 0) {
            supportAgents.add("similar_error_retriever");
        }
        if (!normalizeMapList(evidencePack.courseware().get("hits")).isEmpty()) {
            supportAgents.add("courseware_retriever");
        }
        if (!normalizeMapList(evidencePack.learnerLongTerm().get("memory_refs")).isEmpty()) {
            supportAgents.add("memory_projector");
        }
        return supportAgents;
    }

    private void saveCheckpoint(String sessionId, String label, Map<String, Object> channel) {
        Map<String, Object> values = new LinkedHashMap<>(channel);
        values.put("label", label);
        jdbcTemplate.update(
                """
                insert into ai_workflow_checkpoint(session_id, checkpoint_id, channel_values, created_at)
                values (?, ?, cast(? as jsonb), now())
                """,
                sessionId,
                randomId(24),
                toJson(values)
        );
    }

    private String labelByEvent(String event, String phase, Map<String, Object> nodeOutputs) {
        if ("CHAT".equals(event)) {
            return "对话";
        }
        return switch (phase) {
            case "READING" -> "审题引导";
            case "IDEATING" -> "思路分析";
            case "ERROR_FEEDBACK" -> "错误诊断";
            case "AC_REVIEW" -> "AC 总结";
            case "TRANSFER" -> "迁移出题";
            default -> "工作流节点";
        };
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false, false, Set.of());
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        long userId = rs.getLong("id");
                        String adminType = rs.getString("admin_type");
                        boolean teacher = "Teacher".equals(adminType);
                        boolean admin = "Admin".equals(adminType) || teacher;
                        boolean adminManager = "Admin".equals(adminType);
                        Set<Long> accessibleLanguagePackIds = teacher ? loadTeacherLanguagePackIds(userId) : Set.of();
                        return new UserAuth(true, userId, admin, adminManager, teacher, accessibleLanguagePackIds);
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false, false, Set.of());
        }
    }

    private Set<Long> loadTeacherLanguagePackIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                select distinct clp.language_pack_id
                from classroom_member cm
                join classroom c on c.id = cm.classroom_id
                join classroom_language_pack clp on clp.classroom_id = cm.classroom_id
                where cm.user_id = ?
                  and c.is_active = true
                  and cm.role in ('owner', 'ta')
                """,
                Long.class,
                userId
        ));
    }

    private List<Object> parseJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return new ArrayList<>();
        }
    }

    private String extractFirstSampleInput(String samplesJson) {
        List<Object> samples = parseJsonList(samplesJson);
        if (samples.isEmpty() || !(samples.getFirst() instanceof Map<?, ?> sample)) {
            return "";
        }
        Object input = sample.get("input");
        return input == null ? "" : String.valueOf(input);
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String nowIso() {
        return DATE_TIME_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC));
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseInt using fallback: raw={}, fallback={}", raw, fallback, e);
            return fallback;
        }
    }

    private long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = trimToNull(String.valueOf(raw));
        if (text == null) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseLong returned null: raw={}", raw, e);
            return null;
        }
    }

    private Double parseDoubleObj(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseDoubleObj returned null: value={}", value, e);
            return null;
        }
    }

    private double toDouble(Object value) {
        Double parsed = parseDoubleObj(value);
        return parsed == null ? 0.0 : parsed;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private String normalizeWorkflowEvent(String rawEvent) {
        String event = trimToNull(rawEvent);
        if (event == null) {
            return null;
        }
        String upper = event.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "PROBLEM_GUIDE" -> "READING";
            case "IDEATE" -> "IDEATING";
            case "ERROR_CHAIN", "ERROR_DIAGNOSIS" -> "ERROR_FEEDBACK";
            case "POST_AC" -> "AC_REVIEW";
            case "TRANSFER_PROBLEM" -> "TRANSFER";
            case "AI_REPLY" -> "CHAT";
            default -> upper;
        };
    }

    private Map<String, Object> loadSubmissionRecord(String submissionId, Long userId) {
        if (trimToNull(submissionId) == null) {
            throw new IllegalStateException("submission_id is required");
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select id, code, language, result, info::text as info_json, statistic_info::text as statistic_info_json
                    from submission
                    where id = ? and user_id = ?
                    limit 1
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("code", trimToEmpty(rs.getString("code")));
                        row.put("language", trimToEmpty(rs.getString("language")));
                        row.put("result", rs.getInt("result"));
                        row.put("info", parseJsonMap(rs.getString("info_json")));
                        row.put("statistic_info", parseJsonMap(rs.getString("statistic_info_json")));
                        Map<String, Object> info = castMap(row.get("info"));
                        Map<String, Object> statisticInfo = castMap(row.get("statistic_info"));
                        String errorInfo = trimToEmpty(stringValue(statisticInfo.get("err_info")));
                        if (errorInfo.isBlank()) {
                            errorInfo = trimToEmpty(stringValue(info.get("err_info")));
                        }
                        row.put("error_info", errorInfo);
                        return row;
                    },
                    submissionId,
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw new IllegalStateException("Submission not found: " + submissionId);
        }
    }

    private Map<String, Object> enrichAgentDiagnosis(Map<String, Object> agentOutput,
                                                      EvidencePack evidencePack,
                                                      LearnerState learnerState,
                                                      Map<String, Object> eventData) {
        Map<String, Object> diagnosis = new LinkedHashMap<>(agentOutput);

        int result = parseInt(stringValue(evidencePack.submission().get("result")), 0);
        String errorInfo = trimToEmpty(stringValue(castMap(evidencePack.submission().get("statistic_info")).get("err_info")));
        diagnosis.putIfAbsent("error_taxonomy", classifyErrorTaxonomy(result, errorInfo));
        diagnosis.putIfAbsent("root_cause", "");
        diagnosis.putIfAbsent("what_program_is_doing", "");
        diagnosis.putIfAbsent("expected_behavior", "");
        diagnosis.putIfAbsent("fix_direction", "");
        diagnosis.putIfAbsent("related_kcs", java.util.List.of());
        diagnosis.putIfAbsent("encouragement", "");
        diagnosis.putIfAbsent("hint_level", 1);
        diagnosis.putIfAbsent("misconception_hits", java.util.List.of());
        diagnosis.putIfAbsent("misconception_info", java.util.List.of());
        diagnosis.putIfAbsent("courseware_refs", normalizeMapList(evidencePack.courseware().get("hits")));
        diagnosis.putIfAbsent("similar_error_summary", buildSimilarErrorSummary(
                normalizeMapList(evidencePack.similarErrors().get("similar_notebook_hits")),
                normalizeMapList(evidencePack.similarErrors().get("similar_memory_hits"))));
        diagnosis.putIfAbsent("similar_error_refs", buildSimilarErrorRefs(
                normalizeMapList(evidencePack.similarErrors().get("similar_notebook_hits")),
                normalizeMapList(evidencePack.similarErrors().get("similar_memory_hits"))));
        diagnosis.putIfAbsent("repeat_pattern_detected",
                !normalizeMapList(evidencePack.similarErrors().get("similar_notebook_hits")).isEmpty()
                || !normalizeMapList(evidencePack.similarErrors().get("similar_memory_hits")).isEmpty());
        diagnosis.putIfAbsent("first_failed_test_case", buildFirstFailedTestCasePayload(evidencePack.submission()));

        Map<String, Object> behaviorMetrics = castMap(eventData.get("behavior_metrics"));
        int consecutiveErrors = learnerState == null
                ? parseInt(stringValue(behaviorMetrics.get("consecutiveErrors")), 0)
                : parseInt(stringValue(learnerState.recentBehavior().get("consecutiveErrors")), 0);
        if (!diagnosis.containsKey("frustration_level")) {
            if (consecutiveErrors >= 5) {
                diagnosis.put("frustration_level", "severe");
                diagnosis.put("frustration_encouragement", "先停 30 秒，只看最小样例，再继续。");
            } else if (consecutiveErrors >= 3) {
                diagnosis.put("frustration_level", "moderate");
                diagnosis.put("frustration_encouragement", "这次只改一个点，再重新提交验证。");
            } else {
                diagnosis.put("frustration_level", "low");
                diagnosis.put("frustration_encouragement", "");
            }
        }
        diagnosis.putIfAbsent("frustration_encouragement", "");
        diagnosis.put("react_mode", true);

        return diagnosis;
    }

    private String classifyErrorTaxonomy(int result, String errorInfo) {
        String normalized = trimToEmpty(errorInfo).toLowerCase(Locale.ROOT);
        if (result == -2 || normalized.contains("compile") || normalized.contains("syntaxerror") || normalized.contains("indentationerror")) {
            return com.alethicode.service.aitutor.contract.ErrorTaxonomy.SYNTAX_ERROR;
        }
        if (normalized.contains("nameerror") || normalized.contains("typeerror")) {
            return com.alethicode.service.aitutor.contract.ErrorTaxonomy.NAME_OR_TYPE_ERROR;
        }
        if (normalized.contains("indexerror") || normalized.contains("keyerror")) {
            return com.alethicode.service.aitutor.contract.ErrorTaxonomy.BOUNDARY_CONDITION;
        }
        if (result == 1 || result == 2 || result == 3) {
            return com.alethicode.service.aitutor.contract.ErrorTaxonomy.PERFORMANCE;
        }
        if (normalized.contains("exception") || normalized.contains("traceback") || normalized.contains("runtime") || result == 4) {
            return com.alethicode.service.aitutor.contract.ErrorTaxonomy.RUNTIME_ERROR;
        }
        return com.alethicode.service.aitutor.contract.ErrorTaxonomy.LOGIC_ERROR;
    }

    private String judgeResultLabel(int resultCode) {
        return switch (resultCode) {
            case 0 -> "AC";
            case -2 -> "CE";
            case -1 -> "WA";
            case 1 -> "CPU_TLE";
            case 2 -> "REAL_TLE";
            case 3 -> "MLE";
            case 4 -> "RE";
            case 5 -> "SE";
            case 6 -> "PENDING";
            case 7 -> "JUDGING";
            default -> "UNKNOWN";
        };
    }

    private String summarizeErrorInfo(String errorInfo) {
        String normalized = trimToEmpty(errorInfo).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120);
    }

    private List<String> inferRelatedKcs(String problemContext, String category) {
        if (com.alethicode.service.aitutor.contract.ErrorTaxonomy.SYNTAX_ERROR.equals(category)) {
            return List.of("语法格式", "输入输出");
        }
        if (com.alethicode.service.aitutor.contract.ErrorTaxonomy.RUNTIME_ERROR.equals(category)
                || com.alethicode.service.aitutor.contract.ErrorTaxonomy.BOUNDARY_CONDITION.equals(category)) {
            return List.of("边界条件", "下标访问");
        }
        if (problemContext.contains("循环")) {
            return List.of("循环", "边界条件");
        }
        return List.of("边界条件", "状态更新");
    }

    private List<Map<String, Object>> trimChatHistory(List<Map<String, Object>> history) {
        if (history.size() <= MAX_CHAT_HISTORY) {
            return history;
        }
        return new ArrayList<>(history.subList(history.size() - MAX_CHAT_HISTORY, history.size()));
    }

    private static final String PHASE_SUMMARY_PROMPT = """
            你是教学摘要助手。根据以下教学过程信息，生成一份不超过200字的结构化教学摘要。
            要求严格按以下三部分输出，每部分一行：
            已掌握：[学生在本轮已展示的正确理解，用逗号分隔]
            仍存在：[学生仍在犯的错误或未理解的概念，用逗号分隔]
            下一步：[一句话建议]

            教学过程信息：
            %s
            """;

    private void compressPhaseSummary(Map<String, Object> nodeOutputs, LearnerState learnerState) {
        String rawContext = buildRecentAgentContextSummary(nodeOutputs);
        if (rawContext.isBlank()) {
            return;
        }
        String enrichedContext = rawContext;
        if (!learnerState.weakKcs().isEmpty()) {
            enrichedContext += "\n薄弱知识点: " + String.join(", ", learnerState.weakKcs());
        }
        enrichedContext += "\n挫败感: " + learnerState.frustrationLevel();
        try {
            String summary = aiModelGateway.callForContent(PHASE_SUMMARY_PROMPT.formatted(enrichedContext));
            if (summary != null && !summary.isBlank()) {
                nodeOutputs.put("phase_summary", Map.of(
                        "text", shortenForPrompt(summary.trim(), 300),
                        "generated_at", nowIso()
                ));
            }
        } catch (Exception e) {
            log.warn("Phase summary generation failed, falling back to raw context: {}", e.getMessage());
        }
    }

    private void generatePhaseTransitionSummary(Map<String, Object> nodeOutputs, LearnerState learnerState) {
        Map<String, Object> outputs = castMap(nodeOutputs);
        List<Map<String, Object>> diagnosisList = normalizeMapList(outputs.get("error_diagnosis"));
        if (diagnosisList.isEmpty() && outputs.get("error_diagnosis") instanceof Map<?, ?> diagnosisMap) {
            diagnosisList = List.of(castMap(diagnosisMap));
        }
        String rootCause = "";
        String fixDirection = "";
        String errorTaxonomy = "";
        if (!diagnosisList.isEmpty()) {
            Map<String, Object> diagnosis = castMap(diagnosisList.getFirst());
            rootCause = trimToEmpty(stringValue(diagnosis.get("root_cause")));
            fixDirection = trimToEmpty(stringValue(diagnosis.get("fix_direction")));
            errorTaxonomy = trimToEmpty(stringValue(diagnosis.get("error_taxonomy")));
        }

        String weakKcsText = learnerState.weakKcs().isEmpty() ? "无" : String.join(", ", learnerState.weakKcs());
        String frustration = learnerState.frustrationLevel();
        Map<String, Object> phaseSummary = castMap(outputs.get("phase_summary"));
        String previousSummary = trimToEmpty(stringValue(phaseSummary.get("text")));

        String text = "错误类型: " + (errorTaxonomy.isBlank() ? "未知" : errorTaxonomy) + "\n"
                + "根本原因: " + (rootCause.isBlank() ? "未识别" : shortenForPrompt(rootCause, 200)) + "\n"
                + "修复方向: " + (fixDirection.isBlank() ? "无" : shortenForPrompt(fixDirection, 200)) + "\n"
                + "薄弱KC: " + weakKcsText + "\n"
                + "挫败感: " + (frustration == null ? "low" : frustration);
        if (!previousSummary.isBlank()) {
            text += "\n教学进展: " + shortenForPrompt(previousSummary, 200);
        }

        nodeOutputs.put("phase_transition_summary", Map.of(
                "from_phase", "ERROR_FEEDBACK",
                "to_phase", "CODING",
                "text", text,
                "generated_at", nowIso()
        ));
    }

    private String enrichProblemContextWithSharedMemory(String problemContext, Map<String, Object> nodeOutputs) {
        String base = trimToEmpty(problemContext);
        Map<String, Object> transitionSummary = castMap(nodeOutputs.get("phase_transition_summary"));
        String transitionText = trimToEmpty(stringValue(transitionSummary.get("text")));
        if (!transitionText.isBlank()) {
            return base + "\n\n【阶段转换摘要 " + trimToEmpty(stringValue(transitionSummary.get("from_phase")))
                    + "→" + trimToEmpty(stringValue(transitionSummary.get("to_phase"))) + "】\n" + transitionText;
        }
        Map<String, Object> phaseSummary = castMap(nodeOutputs.get("phase_summary"));
        String summaryText = trimToEmpty(stringValue(phaseSummary.get("text")));
        if (!summaryText.isBlank()) {
            return base + "\n\n【教学进展摘要】\n" + summaryText;
        }
        String sharedSummary = buildRecentAgentContextSummary(nodeOutputs);
        if (sharedSummary.isBlank()) {
            return base;
        }
        return base + "\n\n【同会话共享上下文】\n" + sharedSummary;
    }

    private String buildRecentAgentContextSummary(Map<String, Object> nodeOutputs) {
        Map<String, Object> outputs = castMap(nodeOutputs);
        List<String> lines = new ArrayList<>();

        Map<String, Object> lastEvent = castMap(outputs.get("last_event"));
        String lastEventName = trimToEmpty(stringValue(lastEvent.get("event")));
        if (!lastEventName.isBlank()) {
            lines.add("最近事件: " + lastEventName);
        }
        String lastQuestion = trimToEmpty(stringValue(castMap(lastEvent.get("event_data")).get("message")));
        if (!lastQuestion.isBlank()) {
            lines.add("最近提问: " + shortenForPrompt(lastQuestion, 120));
        }

        String guideTask = trimToEmpty(stringValue(castMap(outputs.get("problem_guide")).get("plain_task")));
        if (!guideTask.isBlank()) {
            lines.add("审题结论: " + shortenForPrompt(guideTask, 120));
        }
        String ideateSummary = trimToEmpty(stringValue(castMap(outputs.get("ideate")).get("understood_as")));
        if (!ideateSummary.isBlank()) {
            lines.add("思路状态: " + shortenForPrompt(ideateSummary, 120));
        }
        List<Map<String, Object>> diagnosisList = normalizeMapList(outputs.get("error_diagnosis"));
        if (diagnosisList.isEmpty() && outputs.get("error_diagnosis") instanceof Map<?, ?> diagnosisMap) {
            diagnosisList = List.of(castMap(diagnosisMap));
        }
        if (!diagnosisList.isEmpty()) {
            Map<String, Object> diagnosis = castMap(diagnosisList.getFirst());
            String category = trimToEmpty(stringValue(diagnosis.get("error_taxonomy")));
            String cause = trimToEmpty(stringValue(diagnosis.get("root_cause")));
            String diagnosisSummary = (category + " " + cause).trim();
            if (!diagnosisSummary.isBlank()) {
                lines.add("诊断结论: " + shortenForPrompt(diagnosisSummary, 140));
            }
        }
        String postAcSummary = trimToEmpty(stringValue(castMap(outputs.get("post_ac")).get("key_success_point")));
        if (!postAcSummary.isBlank()) {
            lines.add("AC 复盘: " + shortenForPrompt(postAcSummary, 120));
        }
        String transferTitle = trimToEmpty(stringValue(castMap(outputs.get("transfer")).get("title")));
        if (!transferTitle.isBlank()) {
            lines.add("迁移题: " + shortenForPrompt(transferTitle, 120));
        }

        List<Map<String, Object>> chatHistory = normalizeMapList(castMap(outputs.get("chat")).get("history"));
        if (!chatHistory.isEmpty()) {
            int fromIndex = Math.max(0, chatHistory.size() - 4);
            for (int i = fromIndex; i < chatHistory.size(); i++) {
                Map<String, Object> entry = castMap(chatHistory.get(i));
                String role = trimToEmpty(stringValue(entry.get("role")));
                String content = trimToEmpty(stringValue(entry.get("content")));
                if (content.isBlank()) {
                    continue;
                }
                String roleLabel = "assistant".equals(role) ? "助手" : ("user".equals(role) ? "学生" : role);
                lines.add(roleLabel + ": " + shortenForPrompt(content, 140));
            }
        }

        if (lines.isEmpty()) {
            return "";
        }
        return shortenForPrompt(String.join("\n", lines), 900);
    }

    private Map<String, Object> issue(String severity, String title, String message, int line, String fixHint) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("severity", severity);
        issue.put("title", title);
        issue.put("message", message);
        issue.put("line", line);
        issue.put("fix_hint", fixHint);
        return issue;
    }

    private int findFirstLine(String code, String needle) {
        if (trimToNull(code) == null || trimToNull(needle) == null) {
            return 1;
        }
        String[] lines = code.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i + 1;
            }
        }
        return 1;
    }

    private String requireNonBlank(Map<String, Object> raw, String field) {
        String value = trimToNull(stringValue(raw.get(field)));
        if (value == null) {
            throw new IllegalStateException(field + " is required");
        }
        return value;
    }

    /**
     * 按字段名顺序从 Map 中取第一个非空白值。
     * 命名特意区分于 {@code ServiceParseUtils.firstNonBlank(String...)}，
     * 方便一眼区分"找 map field"和"找 list item"两种语义，同时避免
     * 与 static import 进来的 String... 版本重载冲突。
     */
    private String firstNonBlankField(Map<String, Object> raw, String... fields) {
        for (String field : fields) {
            String value = trimToNull(stringValue(raw.get(field)));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<Map<String, Object>> normalizeMapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> map = castMap(item);
                if (!map.isEmpty()) {
                    result.add(map);
                }
            }
        }
        return result;
    }

    private List<String> normalizeStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String one = trimToNull(stringValue(item));
                if (one != null) {
                    result.add(one);
                }
            }
        }
        return result;
    }

    private String normalizeWorkflowPhase(String phase) {
        String normalized = trimToEmpty(phase).trim().toUpperCase(Locale.ROOT);
        if ("SCAFFOLDING".equals(normalized)) {
            return "IDEATING";
        }
        return normalized;
    }

    private record UserAuth(boolean authenticated,
                            Long userId,
                            boolean admin,
                            boolean adminManager,
                            boolean teacher,
                            Set<Long> accessibleLanguagePackIds) {
    }

    private record StatementSplit(String description, String inputDescription, String outputDescription) {
    }

}
