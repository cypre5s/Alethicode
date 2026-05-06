package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.SessionUsage;
import com.alethicode.service.aitutor.contract.RuntimeContract;
import com.alethicode.service.aitutor.contract.RuntimeState;
import com.alethicode.service.aitutor.contract.ServerEvent;
import com.alethicode.service.aitutor.contract.FailureBucket;
import com.alethicode.service.languagepack.AnswerSynthesisService;
import com.alethicode.service.languagepack.ConversationContextService;
import com.alethicode.service.languagepack.GroundedAnswer;
import com.alethicode.service.languagepack.LanguagePackQaService;
import com.alethicode.service.languagepack.RetrievalTrace;
import com.alethicode.service.languagepack.SessionContext;
import com.alethicode.service.languagepack.PageRetrievalHit;
import com.alethicode.service.languagepack.PageRetrievalService;
import com.alethicode.service.languagepack.VideoJobService;
import com.alethicode.service.rag.RagServiceException;
import com.alethicode.websocket.QaWebSocketHandler;
import com.alethicode.websocket.WorkflowRealtimeSupport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(rollbackFor = Exception.class)
public class LanguagePackQaServiceImpl implements LanguagePackQaService {

    private static final String TASKS_PATH_MARKER = "/tasks/";
    private static final Pattern SHORT_OJ_VERDICT_PATTERN = Pattern.compile("\\b(ac|wa|tle|mle|ce|re)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> OJ_STRONG_TERMS = Set.of(
            "oj", "题目编号", "problem id", "submission", "judge", "判题", "测试点",
            "样例输入", "样例输出", "输入描述", "输出描述", "sample input", "sample output",
            "time limit", "memory limit", "运行错误", "编译错误", "超时", "内存超限"
    );
    private static final Set<String> OJ_PROBLEM_TERMS = Set.of(
            "这道题", "这题", "题目", "解题", "题解", "思路", "提交", "样例", "输入", "输出"
    );
    private static final Set<String> OJ_SOLVING_TERMS = Set.of(
            "怎么写", "怎么做", "帮我写", "给我代码", "完整代码", "直接给答案", "答案", "通过不了", "过不了", "卡住了"
    );
    private static final int SESSION_TITLE_MAX_LENGTH = 24;
    private static final int SESSION_TITLE_REFRESH_TURN_LIMIT = 4;
    private static final int SESSION_TITLE_SOURCE_TURN_LIMIT = 4;
    private static final int SESSION_TITLE_SOURCE_MAX_CONTENT_LENGTH = 120;

    private static final String TITLE_GEN_SYSTEM_PROMPT =
            "你是一个对话标题生成器。请根据用户多轮提问，生成一个8-16个汉字的简洁标题，概括整段对话的核心主题。" +
            "标题要具体、自然，不要照抄原句。只输出JSON对象，格式为 {\"title\": \"标题内容\"}，不要包含其他内容。";

    private final JdbcTemplate jdbcTemplate;
    private final PageRetrievalService pageRetrievalService;
    private final AnswerSynthesisService answerSynthesisService;
    private final ConversationContextService conversationContextService;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties.LanguagePack languagePackProperties;
    private final VideoJobService videoJobService;
    private final WorkflowRealtimeSupport workflowRealtimeSupport;
    private final AiModelGateway aiModelGateway;
    private final boolean ragQaAllowNotReady;

    public LanguagePackQaServiceImpl(JdbcTemplate jdbcTemplate,
                                     PageRetrievalService pageRetrievalService,
                                     AnswerSynthesisService answerSynthesisService,
                                     ConversationContextService conversationContextService,
                                     ObjectMapper objectMapper,
                                     AlethicodeProperties properties,
                                     VideoJobService videoJobService,
                                     WorkflowRealtimeSupport workflowRealtimeSupport,
                                     AiModelGateway aiModelGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.pageRetrievalService = pageRetrievalService;
        this.answerSynthesisService = answerSynthesisService;
        this.conversationContextService = conversationContextService;
        this.objectMapper = objectMapper;
        this.languagePackProperties = properties.getLanguagePack();
        this.videoJobService = videoJobService;
        this.workflowRealtimeSupport = workflowRealtimeSupport;
        this.aiModelGateway = aiModelGateway;
        this.ragQaAllowNotReady = properties.getRag().isQaAllowNotReady();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listQaPacks(String username) {
        Long userId = requireUserId(username);

        String ragReadyClause = ragQaAllowNotReady ? "" : """
                  AND NOT EXISTS (
                      SELECT 1
                      FROM rag_index_outbox o
                      JOIN language_pack_page p
                        ON o.entity_type = 'courseware_page'
                       AND o.entity_id = p.id::text
                      WHERE p.language_pack_id = lp.id
                        AND (o.given_up_at IS NOT NULL OR o.indexed_at IS NULL)
                  )
                """;

        if (isAdmin(username)) {
            return jdbcTemplate.query(
                    """
                    SELECT lp.id, lp.slug, lp.version, lp.name, lp.primary_language,
                           lp.document_count, lp.page_count, lp.update_time
                    FROM language_pack lp
                    WHERE lp.status = 'published'
                      AND EXISTS (
                          SELECT 1
                          FROM language_pack_page p
                          WHERE p.language_pack_id = lp.id
                      )
                      AND NOT EXISTS (
                          SELECT 1
                          FROM language_pack_page p
                          WHERE p.language_pack_id = lp.id
                            AND coalesce(p.preview_asset_path, '') = ''
                      )
                    """ + ragReadyClause + """
                    ORDER BY lp.update_time DESC, lp.id DESC
                    """,
                    (rs, rowNum) -> packRow(rs.getLong("id"), rs.getString("slug"), rs.getInt("version"),
                            rs.getString("name"), rs.getString("primary_language"),
                            rs.getInt("document_count"), rs.getInt("page_count"), toInstant(rs.getTimestamp("update_time")))
            );
        }

        return jdbcTemplate.query(
                """
                SELECT DISTINCT lp.id, lp.slug, lp.version, lp.name, lp.primary_language,
                       lp.document_count, lp.page_count, lp.update_time
                FROM classroom_member cm
                JOIN classroom c ON c.id = cm.classroom_id
                JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
                JOIN language_pack lp ON lp.id = clp.language_pack_id
                WHERE cm.user_id = ?
                  AND c.is_active = true
                  AND lp.status = 'published'
                  AND EXISTS (
                      SELECT 1
                      FROM language_pack_page p
                      WHERE p.language_pack_id = lp.id
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM language_pack_page p
                      WHERE p.language_pack_id = lp.id
                        AND coalesce(p.preview_asset_path, '') = ''
                  )
                """ + ragReadyClause + """
                ORDER BY lp.update_time DESC, lp.id DESC
                """,
                (rs, rowNum) -> packRow(rs.getLong("id"), rs.getString("slug"), rs.getInt("version"),
                        rs.getString("name"), rs.getString("primary_language"),
                        rs.getInt("document_count"), rs.getInt("page_count"), toInstant(rs.getTimestamp("update_time"))),
                userId
        );
    }

    @Override
    public Map<String, Object> createSession(String username, Long languagePackId) {
        assertPackAccessibleAndReady(username, languagePackId);
        Long userId = requireUserId(username);
        Long sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_chat_session(user_id, language_pack_id, status, create_time, update_time)
                VALUES (?, ?, 'active', now(), now())
                RETURNING id
                """,
                Long.class,
                userId,
                languagePackId
        );
        return getSessionRow(sessionId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSessions(String username, Long languagePackId) {
        Long userId = requireUserId(username);
        if (languagePackId != null) {
            assertPackAccessibleAndReady(username, languagePackId);
        }
        return jdbcTemplate.query(
                """
                SELECT s.id, s.language_pack_id, s.status, s.title, s.starred, s.create_time, s.update_time,
                       lp.name AS language_pack_name,
                       COALESCE(
                           NULLIF(last_message.content, ''),
                           last_message.answer_json ->> 'answer_markdown',
                           ''
                       ) AS last_message_preview
                FROM language_pack_chat_session s
                JOIN language_pack lp ON lp.id = s.language_pack_id
                LEFT JOIN LATERAL (
                    SELECT content, answer_json
                    FROM language_pack_chat_message
                    WHERE session_id = s.id
                    ORDER BY id DESC
                    LIMIT 1
                ) last_message ON TRUE
                WHERE s.user_id = ?
                  AND (? IS NULL OR s.language_pack_id = ?)
                ORDER BY s.starred DESC, s.update_time DESC, s.id DESC
                """,
                (rs, rowNum) -> {
                    Map<String, Object> session = new LinkedHashMap<>();
                    session.put("id", rs.getLong("id"));
                    session.put("language_pack_id", rs.getLong("language_pack_id"));
                    session.put("language_pack_name", rs.getString("language_pack_name"));
                    session.put("status", rs.getString("status"));
                    session.put("title", safeString(rs.getString("title")));
                    session.put("starred", rs.getBoolean("starred"));
                    session.put("last_message_preview", safeString(rs.getString("last_message_preview")));
                    session.put("create_time", toInstant(rs.getTimestamp("create_time")));
                    session.put("update_time", toInstant(rs.getTimestamp("update_time")));
                    return session;
                },
                userId,
                languagePackId,
                languagePackId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMessages(String username, Long sessionId) {
        requireOwnedSessionId(username, sessionId);
        return jdbcTemplate.query(
                """
                SELECT m.id,
                       m.session_id,
                       m.role,
                       m.content,
                       m.answer_json::text AS answer_json_text,
                       retrieval.page_hit_json::text AS fallback_hit_json,
                       m.create_time
                FROM language_pack_chat_message m
                LEFT JOIN LATERAL (
                    SELECT r.page_hit_json
                    FROM language_pack_chat_retrieval_log r
                    WHERE r.session_id = m.session_id
                      AND r.create_time <= m.create_time
                      AND jsonb_typeof(r.page_hit_json) = 'array'
                      AND jsonb_array_length(r.page_hit_json) > 0
                    ORDER BY r.create_time DESC, r.id DESC
                    LIMIT 1
                ) retrieval ON m.role = 'assistant'
                WHERE m.session_id = ?
                ORDER BY m.id ASC
                """,
                (rs, rowNum) -> messageRow(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("answer_json_text"),
                        rs.getString("fallback_hit_json"),
                        toInstant(rs.getTimestamp("create_time"))
                ),
                sessionId
        );
    }

    @Override
    public Map<String, Object> sendMessage(String username, Long sessionId, String content) {
        Long ownedSessionId = requireOwnedSessionId(username, sessionId);
        String normalizedContent = normalizeRequired(content, "content is required");
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_chat_session WHERE id = ?",
                Long.class,
                ownedSessionId
        );
        assertPackAccessibleAndReady(username, languagePackId);

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_chat_message(session_id, role, content, create_time)
                VALUES (?, 'user', ?, now())
                """,
                ownedSessionId,
                normalizedContent
        );
        if (looksLikeOjProblemQuestion(normalizedContent)) {
            GroundedAnswer blockedAnswer = refusalAnswer(
                    "这里是课件问答助手，不处理 OJ 题目、提交结果或索要完整解法。请回到题目页 AI 面板提问。",
                    "oj_problem_question"
            );
            return storeAssistantAnswer(ownedSessionId, blockedAnswer);
        }

        SessionContext sessionContext = conversationContextService.buildSessionContext(ownedSessionId);
        String recentContext = sessionContext.recentDialogue();
        String effectiveQuery = resolveQueryReferences(normalizedContent, sessionContext);
        RetrievalTrace retrievalTrace = pageRetrievalService.retrieveWithTrace(languagePackId, effectiveQuery, recentContext);
        List<PageRetrievalHit> hits = retrievalTrace.hits();
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_chat_retrieval_log(session_id, query_text, page_hit_json, create_time)
                VALUES (?, ?, CAST(? AS jsonb), now())
                """,
                ownedSessionId,
                effectiveQuery,
                toJson(hits.stream().map(PageRetrievalHit::toMap).toList())
        );

        String primaryLanguage = jdbcTemplate.queryForObject(
                "SELECT primary_language FROM language_pack WHERE id = ?", String.class, languagePackId);
        GroundedAnswer answer = answerSynthesisService.synthesizeAnswer(effectiveQuery, hits, languagePackId, primaryLanguage);
        Map<String, Object> result = storeAssistantAnswer(ownedSessionId, answer);
        maybeStoreSessionTitle(ownedSessionId, normalizedContent);
        return result;
    }

    @Override
    public Map<String, Object> sendMessageAsync(String username, Long sessionId, String content) {
        Long ownedSessionId = requireOwnedSessionId(username, sessionId);
        String normalizedContent = normalizeRequired(content, "content is required");
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_chat_session WHERE id = ?",
                Long.class,
                ownedSessionId
        );
        assertPackAccessibleAndReady(username, languagePackId);

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_chat_message(session_id, role, content, create_time)
                VALUES (?, 'user', ?, now())
                """,
                ownedSessionId,
                normalizedContent
        );

        if (looksLikeOjProblemQuestion(normalizedContent)) {
            GroundedAnswer blockedAnswer = refusalAnswer(
                    "这里是课件问答助手，不处理 OJ 题目、提交结果或索要完整解法。请回到题目页 AI 面板提问。",
                    "oj_problem_question"
            );
            storeAssistantAnswer(ownedSessionId, blockedAnswer);
            maybeStoreSessionTitle(ownedSessionId, normalizedContent);
            String channelId = QaWebSocketHandler.qaChannelId(String.valueOf(ownedSessionId));
            RuntimeContract contract = RuntimeContract.builder()
                    .sessionId(String.valueOf(ownedSessionId))
                    .runtimeState(RuntimeState.COMPLETED)
                    .serverEvent(ServerEvent.TASK_COMPLETED)
                    .build();
            workflowRealtimeSupport.broadcastEvent(channelId, ServerEvent.TASK_COMPLETED, contract);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "completed");
            result.put("session_id", ownedSessionId);
            return result;
        }

        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String channelId = QaWebSocketHandler.qaChannelId(String.valueOf(ownedSessionId));

        workflowRealtimeSupport.submitTrackedTask(channelId, () -> {
            RuntimeContract startedContract = RuntimeContract.builder()
                    .sessionId(String.valueOf(ownedSessionId))
                    .taskId(taskId)
                    .runtimeState(RuntimeState.RUNNING)
                    .serverEvent(ServerEvent.TASK_STARTED)
                    .build();
            workflowRealtimeSupport.broadcastEvent(channelId, ServerEvent.TASK_STARTED, startedContract);

            try {
                SessionContext sessionContext = conversationContextService.buildSessionContext(ownedSessionId);
                String recentContext = sessionContext.recentDialogue();
                String effectiveQuery = resolveQueryReferences(normalizedContent, sessionContext);
                RetrievalTrace retrievalTrace = pageRetrievalService.retrieveWithTrace(languagePackId, effectiveQuery, recentContext);
                List<PageRetrievalHit> hits = retrievalTrace.hits();
                jdbcTemplate.update(
                        """
                        INSERT INTO language_pack_chat_retrieval_log(session_id, query_text, page_hit_json, create_time)
                        VALUES (?, ?, CAST(? AS jsonb), now())
                        """,
                        ownedSessionId,
                        effectiveQuery,
                        toJson(hits.stream().map(PageRetrievalHit::toMap).toList())
                );

                String asyncPrimaryLanguage = jdbcTemplate.queryForObject(
                        "SELECT primary_language FROM language_pack WHERE id = ?", String.class, languagePackId);
                GroundedAnswer answer = answerSynthesisService.synthesizeAnswer(effectiveQuery, hits, languagePackId, asyncPrimaryLanguage);
                storeAssistantAnswer(ownedSessionId, answer);
                maybeStoreSessionTitle(ownedSessionId, normalizedContent);

                RuntimeContract completedContract = RuntimeContract.builder()
                        .sessionId(String.valueOf(ownedSessionId))
                        .taskId(taskId)
                        .runtimeState(RuntimeState.COMPLETED)
                        .serverEvent(ServerEvent.TASK_COMPLETED)
                        .build();
                workflowRealtimeSupport.broadcastEvent(channelId, ServerEvent.TASK_COMPLETED, completedContract);
            } catch (Exception exception) {
                boolean isRagFailure = exception instanceof RagServiceException
                        || exception instanceof CallNotPermittedException;
                FailureBucket bucket = isRagFailure
                        ? FailureBucket.RAG_RETRIEVAL_FAILED
                        : FailureBucket.SYSTEM_ERROR;
                RuntimeContract failedContract = RuntimeContract.builder()
                        .sessionId(String.valueOf(ownedSessionId))
                        .taskId(taskId)
                        .runtimeState(RuntimeState.FAILED)
                        .serverEvent(ServerEvent.TASK_FAILED)
                        .failureBucket(bucket)
                        .build();
                workflowRealtimeSupport.broadcastEvent(channelId, ServerEvent.TASK_FAILED, failedContract,
                        Map.of("data", Map.of("error", exception.getMessage() != null ? exception.getMessage() : "QA task failed")));
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "dispatched");
        result.put("session_id", ownedSessionId);
        result.put("task_id", taskId);
        return result;
    }

    @Override
    public void submitFeedback(String username, Long messageId, String feedbackLabel, String comment) {
        Long userId = requireUserId(username);
        String normalizedLabel = normalizeRequired(feedbackLabel, "feedback_label is required");
        if (!List.of("helpful", "unhelpful", "citation_incorrect").contains(normalizedLabel)) {
            throw new BadRequestException("feedback_label has invalid value");
        }
        Long sessionId = jdbcTemplate.query(
                """
                SELECT m.session_id
                FROM language_pack_chat_message m
                JOIN language_pack_chat_session s ON s.id = m.session_id
                WHERE m.id = ?
                  AND s.user_id = ?
                  AND m.role = 'assistant'
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                messageId,
                userId
        );
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "QA assistant message not found");
        }
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_chat_feedback(session_id, message_id, feedback_label, comment, create_time)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (message_id)
                DO UPDATE SET
                    feedback_label = EXCLUDED.feedback_label,
                    comment = EXCLUDED.comment,
                    create_time = now()
                """,
                sessionId,
                messageId,
                normalizedLabel,
                comment == null ? "" : comment.trim()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCitationPage(String username, Long languagePackId, Long documentId, Integer pageNo) {
        assertPackAccessibleAndReady(username, languagePackId);
        if (documentId == null) {
            throw new BadRequestException("document_id is required");
        }
        if (pageNo == null) {
            throw new BadRequestException("page_no is required");
        }
        return jdbcTemplate.query(
                """
                SELECT p.document_id,
                       d.original_filename,
                       p.page_no,
                       p.page_title,
                       p.excerpt,
                       p.page_text,
                       p.preview_asset_path
                FROM language_pack_page p
                JOIN language_pack_document d ON d.id = p.document_id
                WHERE p.language_pack_id = ?
                  AND p.document_id = ?
                  AND p.page_no = ?
                ORDER BY p.chunk_index ASC
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> page = new LinkedHashMap<>();
                    page.put("document_id", rs.getLong("document_id"));
                    page.put("document_title", rs.getString("original_filename"));
                    page.put("page_no", rs.getInt("page_no"));
                    page.put("page_title", safeString(rs.getString("page_title")));
                    page.put("excerpt", safeString(rs.getString("excerpt")));
                    page.put("page_text", safeString(rs.getString("page_text")));
                    page.put("preview_url", buildPreviewUrl(languagePackId, documentId));
                    return page;
                },
                languagePackId,
                documentId,
                pageNo
        );
    }

    @Override
    @Transactional
    public Path getPreviewDocumentPath(String username, Long languagePackId, Long documentId) {
        assertPackAccessibleAndReady(username, languagePackId);
        if (documentId == null) {
            throw new BadRequestException("document_id is required");
        }
        String rawPath = jdbcTemplate.query(
                """
                SELECT preview_pdf_path
                FROM language_pack_document
                WHERE id = ?
                  AND language_pack_id = ?
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                documentId,
                languagePackId
        );
        if (rawPath == null || rawPath.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Preview document not found");
        }
        Path previewPath = Path.of(rawPath).toAbsolutePath().normalize();
        Path allowedRoot = Path.of(languagePackProperties.getPreviewDir()).toAbsolutePath().normalize();
        if (previewPath.startsWith(allowedRoot)) {
            if (!Files.isRegularFile(previewPath)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Preview document not found");
            }
            return previewPath;
        }
        Path remappedPath = remapLegacyPreviewPath(previewPath, allowedRoot);
        if (remappedPath != null && Files.isRegularFile(remappedPath)) {
            persistPreviewPathMigration(languagePackId, documentId, rawPath, remappedPath.toString());
            return remappedPath;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Preview path is outside allowed directory");
    }

    @Override
    public void deleteSession(String username, Long sessionId) {
        Long ownedSessionId = requireOwnedSessionId(username, sessionId);
        jdbcTemplate.update(
                "DELETE FROM language_pack_chat_session WHERE id = ?",
                ownedSessionId
        );
    }

    @Override
    public Map<String, Object> toggleSessionStarred(String username, Long sessionId) {
        Long ownedSessionId = requireOwnedSessionId(username, sessionId);
        Long userId = requireUserId(username);
        jdbcTemplate.update(
                "UPDATE language_pack_chat_session SET starred = NOT starred WHERE id = ?",
                ownedSessionId
        );
        return getSessionRow(ownedSessionId, userId);
    }

    @Override
    public SessionUsage getSessionUsage(String username, Long sessionId) {
        Long ownedSessionId = requireOwnedSessionId(username, sessionId);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT tokens_used, tokens_limit, model_name, update_time
                FROM language_pack_chat_session
                WHERE id = ?
                """,
                ownedSessionId
        );
        long used = row.get("tokens_used") instanceof Number n ? n.longValue() : 0L;
        long limit = row.get("tokens_limit") instanceof Number n ? n.longValue() : 0L;
        Object modelRaw = row.get("model_name");
        String modelName = modelRaw == null ? "" : String.valueOf(modelRaw);
        Object updateRaw = row.get("update_time");
        Instant updated = updateRaw instanceof Timestamp ts ? ts.toInstant() : null;
        return new SessionUsage(used, limit, modelName, updated);
    }

    private Map<String, Object> getSessionRow(Long sessionId, Long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, language_pack_id, status, starred, create_time, update_time
                FROM language_pack_chat_session
                WHERE id = ? AND user_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> session = new LinkedHashMap<>();
                    session.put("id", rs.getLong("id"));
                    session.put("language_pack_id", rs.getLong("language_pack_id"));
                    session.put("status", rs.getString("status"));
                    session.put("starred", rs.getBoolean("starred"));
                    session.put("create_time", toInstant(rs.getTimestamp("create_time")));
                    session.put("update_time", toInstant(rs.getTimestamp("update_time")));
                    return session;
                },
                sessionId,
                userId
        );
    }

    private Map<String, Object> messageRow(Long id,
                                           Long sessionId,
                                           String role,
                                           String content,
                                           String answerJsonText,
                                           String fallbackHitJson,
                                           Instant createTime) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", id);
        message.put("session_id", sessionId);
        message.put("role", role);
        message.put("content", safeString(content));
        if (answerJsonText != null && !answerJsonText.isBlank()) {
            message.put("answer_json", normalizeAssistantAnswerPayload(parseJsonMap(answerJsonText), fallbackHitJson));
        }
        message.put("create_time", createTime);
        if ("assistant".equals(role)) {
            Map<String, Object> videoJob = videoJobService.getJobByMessageId(id);
            if (videoJob != null) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", videoJob.get("id"));
                summary.put("status", videoJob.get("status"));
                summary.put("progress_percent", videoJob.get("progress_percent"));
                summary.put("video_path", videoJob.get("video_path"));
                summary.put("poster_path", videoJob.get("poster_path"));
                summary.put("duration_seconds", videoJob.get("duration_seconds"));
                message.put("video_job", summary);
            }
        }
        return message;
    }

    private Map<String, Object> storeAssistantAnswer(Long sessionId, GroundedAnswer answer) {
        Instant now = Instant.now();
        String fallbackHitJson = findLatestRetrievalHitJson(sessionId, now);
        Map<String, Object> answerPayload = normalizeAssistantAnswerPayload(answer.toMap(), fallbackHitJson);
        String answerPayloadJson = toJson(answerPayload);
        Long assistantMessageId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_chat_message(session_id, role, content, answer_json, create_time)
                VALUES (?, 'assistant', ?, CAST(? AS jsonb), now())
                RETURNING id
                """,
                Long.class,
                sessionId,
                answer.answerMarkdown(),
                answerPayloadJson
        );
        jdbcTemplate.update(
                "UPDATE language_pack_chat_session SET update_time = now() WHERE id = ?",
                sessionId
        );
        return messageRow(assistantMessageId, sessionId, "assistant", answer.answerMarkdown(), answerPayloadJson, null, now);
    }

    private Map<String, Object> packRow(Long id,
                                        String slug,
                                        Integer version,
                                        String name,
                                        String primaryLanguage,
                                        Integer documentCount,
                                        Integer pageCount,
                                        Instant updateTime) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("id", id);
        pack.put("slug", slug);
        pack.put("version", version == null ? 1 : version);
        pack.put("name", name);
        pack.put("primary_language", primaryLanguage);
        pack.put("document_count", documentCount == null ? 0 : documentCount);
        pack.put("page_count", pageCount == null ? 0 : pageCount);
        pack.put("update_time", updateTime);
        return pack;
    }

    private Long requireOwnedSessionId(String username, Long sessionId) {
        Long userId = requireUserId(username);
        Long ownedSessionId = jdbcTemplate.query(
                """
                SELECT id
                FROM language_pack_chat_session
                WHERE id = ?
                  AND user_id = ?
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                sessionId,
                userId
        );
        if (ownedSessionId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "QA session not found");
        }
        return ownedSessionId;
    }

    private Long requireUserId(String username) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        try {
            Long userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM \"user\" WHERE username = ?",
                    Long.class,
                    normalizedUsername
            );
            if (userId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
            }
            return userId;
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
    }

    private void assertPackAccessibleAndReady(String username, Long languagePackId) {
        if (languagePackId == null) {
            throw new BadRequestException("language_pack_id is required");
        }
        requireUserId(username);
        Integer published = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack WHERE id = ? AND status = 'published'",
                Integer.class,
                languagePackId
        );
        if (published == null || published == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Language pack not found");
        }
        if (!isAdmin(username)) {
            Integer allowed = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*)
                    FROM classroom_member cm
                    JOIN classroom c ON c.id = cm.classroom_id
                    JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
                    WHERE cm.user_id = ?
                      AND c.is_active = true
                      AND clp.language_pack_id = ?
                    """,
                    Integer.class,
                    requireUserId(username),
                    languagePackId
            );
            if (allowed == null || allowed == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Permission denied");
            }
        }

        Integer pageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_page WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        if (pageCount == null || pageCount == 0) {
            throw new BadRequestException("language pack is not qa ready");
        }
        Integer invalidPageCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM language_pack_page
                WHERE language_pack_id = ?
                  AND coalesce(preview_asset_path, '') = ''
                """,
                Integer.class,
                languagePackId
        );
        if (invalidPageCount != null && invalidPageCount > 0) {
            throw new BadRequestException("language pack is not qa ready");
        }
    }

    private boolean isAdmin(String username) {
        try {
            String adminType = jdbcTemplate.queryForObject(
                    "SELECT admin_type FROM \"user\" WHERE username = ?",
                    String.class,
                    username
            );
            return "Admin".equals(adminType) || "Teacher".equals(adminType);
        } catch (EmptyResultDataAccessException exception) {
            return false;
        }
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON payload", exception);
        }
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse stored JSON payload", exception);
        }
    }

    private List<Map<String, Object>> parseJsonList(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse stored JSON array payload", exception);
        }
    }

    private Map<String, Object> normalizeAssistantAnswerPayload(Map<String, Object> answerJson, String fallbackHitJson) {
        if (answerJson == null || answerJson.isEmpty()) {
            return answerJson;
        }
        if (hasNonEmptyCitations(answerJson) || asBoolean(answerJson.get("insufficient_evidence"))) {
            return answerJson;
        }
        List<Map<String, Object>> fallbackCitations = buildCitationsFromHitJson(fallbackHitJson);
        if (fallbackCitations.isEmpty()) {
            return answerJson;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(answerJson);
        normalized.put("citations", fallbackCitations);
        return normalized;
    }

    private boolean hasNonEmptyCitations(Map<String, Object> answerJson) {
        Object citations = answerJson.get("citations");
        return citations instanceof List<?> list && !list.isEmpty();
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String textValue) {
            return "true".equalsIgnoreCase(textValue.trim());
        }
        return false;
    }

    private List<Map<String, Object>> buildCitationsFromHitJson(String fallbackHitJson) {
        if (fallbackHitJson == null || fallbackHitJson.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> hits = parseJsonList(fallbackHitJson);
        if (hits.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> citationsByKey = new LinkedHashMap<>();
        for (Map<String, Object> hit : hits) {
            Long documentId = toLong(hit.get("document_id"));
            Integer pageNo = toInteger(hit.get("page_no"));
            if (documentId == null || pageNo == null) {
                continue;
            }
            String key = documentId + ":" + pageNo;
            if (citationsByKey.containsKey(key)) {
                continue;
            }
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("document_id", documentId);
            citation.put("document_title", safeString(asText(hit.get("document_title"))));
            citation.put("page_no", pageNo);
            citation.put("excerpt", safeString(asText(hit.get("excerpt"))));
            citation.put("confidence", normalizeConfidence(hit.get("confidence"), hit.get("score")));
            citationsByKey.put(key, citation);
        }
        return List.copyOf(citationsByKey.values());
    }

    private String findLatestRetrievalHitJson(Long sessionId, Instant beforeTime) {
        return jdbcTemplate.query(
                """
                SELECT page_hit_json::text
                FROM language_pack_chat_retrieval_log
                WHERE session_id = ?
                  AND create_time <= ?
                  AND jsonb_typeof(page_hit_json) = 'array'
                  AND jsonb_array_length(page_hit_json) > 0
                ORDER BY create_time DESC, id DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                sessionId,
                Timestamp.from(beforeTime)
        );
    }

    private Long toLong(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String textValue && !textValue.trim().isBlank()) {
            return Long.valueOf(textValue.trim());
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String textValue && !textValue.trim().isBlank()) {
            return Integer.valueOf(textValue.trim());
        }
        return null;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double normalizeConfidence(Object confidenceValue, Object scoreValue) {
        Object numericValue = confidenceValue != null ? confidenceValue : scoreValue;
        if (numericValue instanceof Number numberValue) {
            return Math.round(numberValue.doubleValue() * 1000.0) / 1000.0;
        }
        if (numericValue instanceof String textValue && !textValue.trim().isBlank()) {
            return Math.round(Double.parseDouble(textValue.trim()) * 1000.0) / 1000.0;
        }
        return 0.0;
    }

    private String buildPreviewUrl(Long languagePackId, Long documentId) {
        return "/api/language-pack-qa/packs/" + languagePackId + "/documents/" + documentId + "/preview";
    }

    private GroundedAnswer refusalAnswer(String answerMarkdown, String refusalReason) {
        return new GroundedAnswer(answerMarkdown, List.of(), false, true, refusalReason);
    }

    private Path remapLegacyPreviewPath(Path previewPath, Path allowedRoot) {
        String normalizedPath = previewPath.toString().replace('\\', '/');
        int markerIndex = normalizedPath.indexOf(TASKS_PATH_MARKER);
        if (markerIndex < 0) {
            return null;
        }
        String relative = normalizedPath.substring(markerIndex + 1);
        Path candidate = allowedRoot.resolve(relative).normalize();
        if (!candidate.startsWith(allowedRoot)) {
            return null;
        }
        return candidate;
    }

    private void persistPreviewPathMigration(Long languagePackId, Long documentId, String oldPath, String newPath) {
        jdbcTemplate.update(
                """
                UPDATE language_pack_document
                SET preview_pdf_path = ?,
                    update_time = now()
                WHERE id = ?
                  AND language_pack_id = ?
                  AND preview_pdf_path = ?
                """,
                newPath,
                documentId,
                languagePackId,
                oldPath
        );
        jdbcTemplate.update(
                """
                UPDATE language_pack_page
                SET preview_asset_path = ?
                WHERE document_id = ?
                  AND language_pack_id = ?
                  AND preview_asset_path = ?
                """,
                newPath,
                documentId,
                languagePackId,
                oldPath
        );
    }

    private void maybeStoreSessionTitle(Long sessionId, String latestUserQuestion) {
        try {
            String existingTitle = jdbcTemplate.query(
                    "SELECT title FROM language_pack_chat_session WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null,
                    sessionId
            );
            if (existingTitle != null && !existingTitle.isBlank()) {
                return;
            }
            Integer userMessageCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM language_pack_chat_message WHERE session_id = ? AND role = 'user'",
                    Integer.class,
                    sessionId
            );
            if (userMessageCount == null || userMessageCount <= 0) {
                return;
            }
            boolean titleMissing = existingTitle == null || existingTitle.isBlank();
            boolean inEarlyDialogue = userMessageCount <= SESSION_TITLE_REFRESH_TURN_LIMIT;
            if (!titleMissing && !inEarlyDialogue) {
                return;
            }
            String conversationSource = buildSessionTitleSource(sessionId);
            String title = generateSessionTitle(conversationSource, latestUserQuestion);
            if (title.isBlank() || title.equals(existingTitle)) {
                return;
            }
            jdbcTemplate.update(
                    "UPDATE language_pack_chat_session SET title = ? WHERE id = ?",
                    title,
                    sessionId
            );
        } catch (Exception ignored) {
            // title generation is best-effort, never block the main flow
        }
    }

    private String generateSessionTitle(String conversationSource, String latestUserQuestion) {
        try {
            String normalizedSource = conversationSource == null ? "" : conversationSource.trim();
            String promptBody = normalizedSource.isBlank()
                    ? "用户问题：" + normalizeTitleSourceQuestion(latestUserQuestion)
                    : "用户多轮问题如下：\n" + normalizedSource;
            Map<String, Object> result = aiModelGateway.callForJson(TITLE_GEN_SYSTEM_PROMPT, promptBody);
            Object titleObj = result.get("title");
            if (titleObj instanceof String title && !title.isBlank()) {
                String normalizedTitle = normalizeSessionTitle(title);
                if (!normalizedTitle.isBlank()) {
                    return normalizedTitle;
                }
            }
        } catch (Exception ignored) {
            // fall through to heuristic fallback
        }
        return fallbackSessionTitle(conversationSource, latestUserQuestion);
    }

    private String buildSessionTitleSource(Long sessionId) {
        List<String> normalizedQuestions = jdbcTemplate.query(
                """
                SELECT content
                FROM language_pack_chat_message
                WHERE session_id = ?
                  AND role = 'user'
                ORDER BY id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> normalizeTitleSourceQuestion(rs.getString("content")),
                sessionId,
                SESSION_TITLE_SOURCE_TURN_LIMIT
        );
        StringBuilder sourceBuilder = new StringBuilder();
        int round = 1;
        for (String question : normalizedQuestions) {
            if (question.isBlank()) {
                continue;
            }
            sourceBuilder.append("第").append(round).append("轮：").append(question).append('\n');
            round++;
        }
        return sourceBuilder.toString().trim();
    }

    private String normalizeTitleSourceQuestion(String question) {
        if (question == null) {
            return "";
        }
        String normalized = question.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() > SESSION_TITLE_SOURCE_MAX_CONTENT_LENGTH) {
            return normalized.substring(0, SESSION_TITLE_SOURCE_MAX_CONTENT_LENGTH);
        }
        return normalized;
    }

    private String normalizeSessionTitle(String title) {
        if (title == null) {
            return "";
        }
        String normalized = title.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() > 1 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.length() > SESSION_TITLE_MAX_LENGTH) {
            return normalized.substring(0, SESSION_TITLE_MAX_LENGTH);
        }
        return normalized;
    }

    private String fallbackSessionTitle(String conversationSource, String latestUserQuestion) {
        String normalizedLatestQuestion = normalizeTitleSourceQuestion(latestUserQuestion);
        if (!normalizedLatestQuestion.isBlank()) {
            if (normalizedLatestQuestion.length() > SESSION_TITLE_MAX_LENGTH) {
                return normalizedLatestQuestion.substring(0, SESSION_TITLE_MAX_LENGTH) + "…";
            }
            return normalizedLatestQuestion;
        }
        String normalizedSource = conversationSource == null ? "" : conversationSource.trim();
        if (!normalizedSource.isBlank()) {
            String firstLine = normalizedSource.lines().findFirst().orElse("").replaceFirst("^第\\d+轮：", "").trim();
            if (!firstLine.isBlank()) {
                if (firstLine.length() > SESSION_TITLE_MAX_LENGTH) {
                    return firstLine.substring(0, SESSION_TITLE_MAX_LENGTH) + "…";
                }
                return firstLine;
            }
        }
        return "新会话";
    }

    private String resolveQueryReferences(String query, SessionContext sessionContext) {
        if (query == null || query.isBlank()) {
            return query;
        }
        String lower = query.toLowerCase();
        boolean hasReference = lower.contains("这个") || lower.contains("那个")
                || lower.contains("上面") || lower.contains("刚才")
                || lower.contains("上一条") || lower.contains("上一个");
        if (!hasReference || sessionContext.sessionSummary().isBlank()) {
            return query;
        }
        return query + " [上下文: " + sessionContext.sessionSummary() + "]";
    }

    private boolean looksLikeOjProblemQuestion(String question) {
        String normalized = normalizeForDetection(question);
        if (normalized.isBlank()) {
            return false;
        }
        for (String term : OJ_STRONG_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        if (SHORT_OJ_VERDICT_PATTERN.matcher(normalized).find()) {
            return true;
        }
        boolean mentionsProblem = containsAny(normalized, OJ_PROBLEM_TERMS);
        boolean asksForSolution = containsAny(normalized, OJ_SOLVING_TERMS);
        return mentionsProblem && asksForSolution;
    }

    private boolean containsAny(String haystack, Set<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForDetection(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().toLowerCase();
        if (normalized.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isLetterOrDigit(current) || Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                builder.append(current);
            } else {
                builder.append(' ');
            }
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }
}
