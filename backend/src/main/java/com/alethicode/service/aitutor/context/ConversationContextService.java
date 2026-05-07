package com.alethicode.service.aitutor.context;

import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.contract.Phase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Chat 的跨卡片会话上下文池。
 *
 * <p>核心职责：
 * <ul>
 *   <li>跟踪每个会话的 {@code active_mode}，为卡片写入 {@code mode_when_produced}。</li>
 *   <li>返回会话最近 N 张卡片作为 Chat 证据。</li>
 *   <li>解析 {@code @card:&lt;id&gt;} 和 {@code @last_error} 等引用。</li>
 *   <li>按 Phase 允许矩阵切换 Mode，非法切换直接 422。</li>
 * </ul>
 *
 * <p>Design: <code>docs/plans/2026-04-25-unified-chat-context-design.md</code> §7.3</p>
 */
@Service
public class ConversationContextService {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextService.class);
    private static final int DEFAULT_LAST_CARDS_LIMIT = 5;
    private static final int MAX_LAST_CARDS_LIMIT = 10;
    private static final int SHORT_TEXT_MAX_CHARS = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConversationContextService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 快速断言会话存在且属于指定用户。
     *
     * @throws com.alethicode.exception.LegacyBusinessException 会话不属于该用户时抛出 403
     */
    public void assertSessionOwnedBy(String sessionId, long userId) {
        if (sessionId == null || sessionId.isBlank()) return;
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_tutor_workflow_session WHERE session_id = :sid AND user_id = :uid AND is_active = TRUE",
                new MapSqlParameterSource()
                        .addValue("sid", sessionId)
                        .addValue("uid", userId),
                Long.class
        );
        if (count == null || count == 0) {
            throw BusinessExceptions.fromLegacy("error", "Session does not belong to user");
        }
    }

    /**
     * @return 会话当前持久化的 active mode；会话缺失时返回默认 Mode
     */
    public ConversationMode getActiveMode(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ConversationMode.defaultMode();
        }
        try {
            String raw = jdbc.queryForObject(
                    "SELECT active_mode FROM ai_tutor_workflow_session WHERE session_id = :sid",
                    new MapSqlParameterSource("sid", sessionId),
                    String.class
            );
            return ConversationMode.fromKey(raw).orElse(ConversationMode.defaultMode());
        } catch (EmptyResultDataAccessException e) {
            return ConversationMode.defaultMode();
        }
    }

    /**
     * 切换会话 active mode。
     *
     * @throws com.alethicode.exception.LegacyBusinessException 目标 Mode 不允许用于当前 Phase 时抛出 422
     */
    @Transactional
    public ConversationMode switchMode(String sessionId, ConversationMode newMode, Phase currentPhase) {
        if (sessionId == null || sessionId.isBlank()) {
            throw BusinessExceptions.fromLegacy("error", "session_id is required");
        }
        if (newMode == null) {
            throw BusinessExceptions.fromLegacy("error", "mode is required");
        }
        if (currentPhase == null) {
            throw BusinessExceptions.fromLegacy("error", "current_phase is required");
        }
        if (!newMode.allowedIn(currentPhase)) {
            throw BusinessExceptions.fromLegacy(
                    "error",
                    "Mode " + newMode.key() + " is not allowed in phase " + currentPhase.name()
            );
        }
        int updated = jdbc.update(
                "UPDATE ai_tutor_workflow_session SET active_mode = :mode, last_mode_switched_at = NOW(), updated_at = NOW() "
                        + "WHERE session_id = :sid AND is_active = TRUE",
                new MapSqlParameterSource()
                        .addValue("mode", newMode.key())
                        .addValue("sid", sessionId)
        );
        if (updated == 0) {
            throw BusinessExceptions.fromLegacy("error", "session not found or inactive: " + sessionId);
        }
        return newMode;
    }

    /**
     * @return 会话最近的卡片列表，按时间倒序，最多 {@value #MAX_LAST_CARDS_LIMIT} 条
     */
    public List<CardSummary> listLastCards(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        int safe = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LAST_CARDS_LIMIT : limit, MAX_LAST_CARDS_LIMIT));
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT e.card_id,
                       COALESCE(e.card_type, CASE e.client_event
                           WHEN 'READING' THEN 'problem_guide'
                           WHEN 'IDEATING' THEN 'ideate_analysis'
                           WHEN 'SKELETON' THEN 'skeleton_code'
                           WHEN 'ERROR_FEEDBACK' THEN 'error_diagnosis'
                           WHEN 'AC_REVIEW' THEN 'post_ac'
                           WHEN 'TRANSFER' THEN 'transfer_problem'
                           WHEN 'KNOWLEDGE_REVIEW' THEN 'knowledge_review'
                           WHEN 'VISUALIZE' THEN 'visualize'
                           WHEN 'CHAT' THEN 'ai_reply'
                           ELSE NULL
                       END) AS card_type,
                       e.mode_when_produced, e.event_data::text AS event_data_json, e.created_at
                FROM ai_tutor_workflow_event e
                INNER JOIN ai_tutor_workflow_session s ON e.session_id = s.session_id
                WHERE e.session_id = :sid
                  AND (e.runtime_state IS NULL OR e.runtime_state = 'COMPLETED')
                  AND (
                      e.card_id IS NOT NULL
                      OR e.card_type IS NOT NULL
                      OR e.client_event IN (
                          'READING', 'IDEATING', 'SKELETON', 'ERROR_FEEDBACK',
                          'AC_REVIEW', 'TRANSFER', 'KNOWLEDGE_REVIEW', 'VISUALIZE', 'CHAT'
                      )
                  )
                ORDER BY e.created_at DESC
                LIMIT :lim
                """,
                new MapSqlParameterSource()
                        .addValue("sid", sessionId)
                        .addValue("lim", safe)
        );
        List<CardSummary> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String cardId = (String) row.get("card_id");
            String cardType = (String) row.get("card_type");
            String mode = (String) row.get("mode_when_produced");
            String json = (String) row.get("event_data_json");
            Object createdAtRaw = row.get("created_at");
            Instant createdAt = createdAtRaw instanceof Timestamp ts ? ts.toInstant() : Instant.now();
            result.add(new CardSummary(cardId, cardType, mode, summarizeCardJson(cardType, json), createdAt));
        }
        return result;
    }

    /**
     * 将原始引用 token 解析为当前会话范围内的 {@link CardSummary}。
     *
     * <p>解析规则：
     * <ul>
     *   <li>显式 {@code @card:<id>} 仅匹配当前会话内的卡片。</li>
     *   <li>{@code @last_xxx} 返回当前会话中最近的对应类型卡片。</li>
     *   <li>同一卡片被多次引用时只返回一次。</li>
     * </ul></p>
     */
    public List<CardSummary> resolveReferences(String sessionId, List<String> references) {
        if (sessionId == null || sessionId.isBlank() || references == null || references.isEmpty()) {
            return List.of();
        }
        Map<String, CardSummary> dedup = new LinkedHashMap<>();
        for (String raw : references) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            String explicit = ReferenceResolver.extractCardId(trimmed);
            if (explicit != null) {
                CardSummary card = loadCardById(sessionId, explicit);
                if (card != null) dedup.putIfAbsent(card.cardId(), card);
                continue;
            }

            ReferenceResolver.ShorthandKind kind = ReferenceResolver.classifyShorthand(trimmed);
            if (kind != null) {
                CardSummary card = loadLastCardOfType(sessionId, kind.cardType());
                if (card != null) {
                    String dedupKey = card.cardId() == null || card.cardId().isBlank()
                            ? "last:" + kind.cardType()
                            : card.cardId();
                    dedup.putIfAbsent(dedupKey, card);
                }
            }
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * 为会话最近写入的事件补齐卡片元数据。
     *
     * <p>事件不产生稳定卡片时返回 null，例如 CODING 证据采样或 FAILED run。</p>
     */
    @Transactional
    public String stampCardForLatestEvent(String sessionId, String runId, String cardType,
                                          ConversationMode modeWhenProduced,
                                          List<String> referencedCardIds) {
        if (sessionId == null || runId == null || cardType == null) {
            return null;
        }
        String cardId = newCardId(cardType);
        String referencedJson = serializeJsonArray(referencedCardIds == null ? List.of() : referencedCardIds);
        int updated = jdbc.update(
                """
                UPDATE ai_tutor_workflow_event
                SET card_id = :cid,
                    card_type = :ctype,
                    mode_when_produced = :mode,
                    referenced_card_ids = CAST(:refs AS jsonb)
                WHERE id = (
                    SELECT id FROM ai_tutor_workflow_event
                    WHERE session_id = :sid AND run_id = :rid AND card_id IS NULL
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                )
                """,
                new MapSqlParameterSource()
                        .addValue("cid", cardId)
                        .addValue("ctype", cardType)
                        .addValue("mode", modeWhenProduced == null ? null : modeWhenProduced.key())
                        .addValue("refs", referencedJson)
                        .addValue("sid", sessionId)
                        .addValue("rid", runId)
        );
        return updated > 0 ? cardId : null;
    }

    private CardSummary loadCardById(String sessionId, String cardId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT e.card_id, e.card_type, e.mode_when_produced, e.event_data::text AS event_data_json, e.created_at
                    FROM ai_tutor_workflow_event e
                    INNER JOIN ai_tutor_workflow_session s ON e.session_id = s.session_id
                    WHERE e.session_id = :sid AND e.card_id = :cid
                    LIMIT 1
                    """,
                    new MapSqlParameterSource()
                            .addValue("sid", sessionId)
                            .addValue("cid", cardId),
                    (rs, rowNum) -> new CardSummary(
                            rs.getString("card_id"),
                            rs.getString("card_type"),
                            rs.getString("mode_when_produced"),
                            summarizeCardJson(rs.getString("card_type"), rs.getString("event_data_json")),
                            rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toInstant() : Instant.now()
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private CardSummary loadLastCardOfType(String sessionId, String cardType) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT e.card_id, COALESCE(e.card_type, :ctype) AS card_type,
                           e.mode_when_produced, e.event_data::text AS event_data_json, e.created_at
                    FROM ai_tutor_workflow_event e
                    INNER JOIN ai_tutor_workflow_session s ON e.session_id = s.session_id
                    WHERE e.session_id = :sid
                      AND (e.runtime_state IS NULL OR e.runtime_state = 'COMPLETED')
                      AND (
                          e.card_type = :ctype
                          OR (e.card_type IS NULL AND e.client_event = :client_event)
                      )
                    ORDER BY e.created_at DESC
                    LIMIT 1
                    """,
                    new MapSqlParameterSource()
                            .addValue("sid", sessionId)
                            .addValue("ctype", cardType)
                            .addValue("client_event", clientEventForCardType(cardType)),
                    (rs, rowNum) -> new CardSummary(
                            rs.getString("card_id"),
                            rs.getString("card_type"),
                            rs.getString("mode_when_produced"),
                            summarizeCardJson(rs.getString("card_type"), rs.getString("event_data_json")),
                            rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toInstant() : Instant.now()
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 从 {@code event_data.node_outputs} 提取关键字段，生成适合 prompt 的短卡片描述。
     */
    private String summarizeCardJson(String cardType, String eventDataJson) {
        if (eventDataJson == null || eventDataJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(eventDataJson);
            JsonNode outputs = root.path("node_outputs");
            JsonNode card = outputs.path(outputKeyForCardType(cardType));
            if (card.isMissingNode() || card.isNull()) {
                card = root.path("card_payload");
            }
            String summary = pickSummaryField(cardType, card);
            if (summary == null || summary.isBlank()) {
                summary = firstTextualValue(card);
            }
            return truncate(summary);
        } catch (Exception e) {
            log.debug("summarizeCardJson failed for type {}: {}", cardType, e.getMessage());
            return "";
        }
    }

    private String pickSummaryField(String cardType, JsonNode card) {
        if (card == null || card.isMissingNode() || card.isNull()) return "";
        return switch (cardType == null ? "" : cardType) {
            case "problem_guide" -> textOf(card, "plain_task");
            case "ideate_analysis", "ideate" -> textOf(card, "analysis");
            case "skeleton_code" -> textOf(card, "description");
            case "error_diagnosis" -> textOf(card, "root_cause");
            case "post_ac" -> textOf(card, "key_success_point");
            case "transfer_problem", "transfer" -> textOf(card, "title");
            case "knowledge_review" -> textOfAny(card, "review_content", "reply", "summary", "title");
            case "ai_reply", "chat" -> textOf(card, "content");
            case "visualize" -> textOfAny(card, "alt_text", "title", "description", "intent");
            default -> "";
        };
    }

    private String textOfAny(JsonNode card, String... fields) {
        for (String field : fields) {
            String value = textOf(card, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String textOf(JsonNode card, String field) {
        JsonNode v = card.path(field);
        return v.isTextual() ? v.asText() : "";
    }

    private String firstTextualValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                JsonNode value = fields.next().getValue();
                String text = firstTextualValue(value);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode value : node) {
                String text = firstTextualValue(value);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private String outputKeyForCardType(String cardType) {
        return switch (cardType == null ? "" : cardType) {
            case "ideate_analysis" -> "ideate";
            case "transfer_problem" -> "transfer";
            case "ai_reply" -> "chat";
            default -> cardType == null ? "" : cardType;
        };
    }

    private String clientEventForCardType(String cardType) {
        return switch (cardType == null ? "" : cardType) {
            case "problem_guide" -> "READING";
            case "ideate_analysis", "ideate" -> "IDEATING";
            case "skeleton_code" -> "SKELETON";
            case "error_diagnosis" -> "ERROR_FEEDBACK";
            case "post_ac" -> "AC_REVIEW";
            case "transfer_problem", "transfer" -> "TRANSFER";
            case "knowledge_review" -> "KNOWLEDGE_REVIEW";
            case "visualize" -> "VISUALIZE";
            case "ai_reply", "chat" -> "CHAT";
            default -> "";
        };
    }

    private String truncate(String text) {
        if (text == null) return "";
        String collapsed = text.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= SHORT_TEXT_MAX_CHARS) return collapsed;
        return collapsed.substring(0, SHORT_TEXT_MAX_CHARS) + "…";
    }

    private String serializeJsonArray(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String newCardId(String cardType) {
        String prefix = "C-" + prefixForCardType(cardType) + "-";
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + suffix;
    }

    private static String prefixForCardType(String cardType) {
        return switch (cardType == null ? "" : cardType) {
            case "problem_guide" -> "G";
            case "ideate_analysis", "ideate" -> "I";
            case "skeleton_code" -> "S";
            case "error_diagnosis" -> "E";
            case "execution_trace_explainer" -> "X";
            case "post_ac" -> "A";
            case "transfer_problem", "transfer" -> "T";
            case "knowledge_review" -> "K";
            case "ai_reply", "chat" -> "M";
            case "visualize" -> "V";
            default -> "Z";
        };
    }
}
