package com.alethicode.service.aitutor.profile;

import com.alethicode.service.aitutor.contract.ErrorTaxonomy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 组装 AI 学习助手欢迎面板的 greeting / starter_actions / memory_tags。
 *
 * tags 设计原则（fail-fast）：
 * - 仅从 {@code memory_payload} 中的 {@code error_taxonomy}（或旧字段 {@code error_category}）
 *   映射到 {@link ErrorTaxonomy#label} 的中文分类名作为 tag；
 * - 不再对 memory_key（如 {@code notebook:<uuid>}、{@code event:<id>}）做字符串反推——
 *   内部主键不属于展示语义；
 * - 若某条 memory 的 payload 无法解析或缺失 taxonomy，直接跳过，
 *   避免把脏数据展示给学生。
 */
@Service
public class AITutorWelcomeService {

    private static final Logger log = LoggerFactory.getLogger(AITutorWelcomeService.class);

    private static final double WEAK_THRESHOLD = 0.4;
    private static final int MAX_MEMORIES = 3;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AITutorWelcomeService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getWelcome(Long userId, Long problemId) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> context = buildContext(userId, problemId);
        String greeting = buildGreeting(context);
        List<Map<String, Object>> starterActions = buildStarterActions(context);

        result.put("greeting", greeting);
        result.put("starter_actions", starterActions);
        result.put("has_personalization", Boolean.TRUE.equals(context.get("has_memory")));

        if (context.get("memory_tags") != null) {
            result.put("memory_tags", context.get("memory_tags"));
        }

        return result;
    }

    private Map<String, Object> buildContext(Long userId, Long problemId) {
        Map<String, Object> ctx = new LinkedHashMap<>();

        List<Map<String, Object>> memories = jdbcTemplate.queryForList("""
            SELECT memory_payload::text AS memory_payload_json, confidence
            FROM ai_learner_memory
            WHERE user_id = ? AND enabled = true AND memory_type = 'error_pattern'
              AND confidence > 0.5
            ORDER BY confidence DESC
            LIMIT ?
            """, userId, MAX_MEMORIES);

        LinkedHashSet<String> tagSet = new LinkedHashSet<>();
        String primaryLabel = null;
        for (Map<String, Object> row : memories) {
            String rawTaxonomy = extractTaxonomy((String) row.get("memory_payload_json"));
            if (rawTaxonomy == null) {
                continue;
            }
            String normalized = ErrorTaxonomy.normalize(rawTaxonomy);
            if (ErrorTaxonomy.UNKNOWN.equals(normalized)) {
                continue;
            }
            String label = ErrorTaxonomy.label(normalized);
            if (primaryLabel == null) {
                primaryLabel = label;
            }
            tagSet.add(label);
        }

        ctx.put("has_memory", !tagSet.isEmpty());
        if (!tagSet.isEmpty()) {
            ctx.put("memory_tags", new ArrayList<>(tagSet));
            ctx.put("primary_taxonomy_label", primaryLabel);
        }

        List<Map<String, Object>> weakKcs = jdbcTemplate.queryForList("""
            SELECT k.name, COALESCE(km.mastery, 0) AS mastery
            FROM ai_problem_kc_mapping m
            JOIN language_pack_kc k ON k.synced_ai_kc_id = m.kc_id
            LEFT JOIN learner_kc_mastery km ON km.kc_id = k.id AND km.user_id = ?
            WHERE m.problem_id = ? AND COALESCE(km.mastery, 0) < ?
            ORDER BY km.mastery ASC
            """, userId, problemId, WEAK_THRESHOLD);
        ctx.put("weak_kcs", weakKcs);

        String recentFailedSubmissionId = queryRecentFailedSubmissionId(userId, problemId);
        if (recentFailedSubmissionId != null) {
            ctx.put("recent_failed_submission_id", recentFailedSubmissionId);
        }

        return ctx;
    }

    /**
     * 查询用户在当前题目最近一次非 AC (result != 0) 的提交 id。
     * 用于判断是否展示「我遇到了错误，帮我看看」入口——没有错题就不展示。
     *
     * submission.id 为 32 字符随机字符串（见 {@link com.alethicode.service.submission.impl.SubmissionServiceImpl}），
     * 因此返回 String。
     */
    private String queryRecentFailedSubmissionId(Long userId, Long problemId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id
            FROM submission
            WHERE user_id = ? AND problem_id = ? AND result <> 0
            ORDER BY create_time DESC
            LIMIT 1
            """, userId, problemId);
        if (rows.isEmpty()) {
            return null;
        }
        Object raw = rows.getFirst().get("id");
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String extractTaxonomy(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payloadJson);
        } catch (Exception ex) {
            log.warn("failed to parse memory_payload json: {}", ex.getMessage());
            return null;
        }
        String value = textOrNull(root.get("error_taxonomy"));
        if (value == null) {
            value = textOrNull(root.get("error_category"));
        }
        return value;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @SuppressWarnings("unchecked")
    private String buildGreeting(Map<String, Object> ctx) {
        String primaryLabel = (String) ctx.get("primary_taxonomy_label");
        if (primaryLabel != null && !primaryLabel.isBlank()) {
            return "根据你的学习记录，你最近在「" + primaryLabel + "」上踩过坑，这道题记得留个心眼哦～";
        }

        List<Map<String, Object>> weakKcs = (List<Map<String, Object>>) ctx.get("weak_kcs");
        if (weakKcs != null && !weakKcs.isEmpty()) {
            Map<String, Object> first = weakKcs.getFirst();
            String kcName = (String) first.get("name");
            Object masteryObj = first.get("mastery");
            double mastery = masteryObj instanceof Number n ? n.doubleValue() : 0.0;
            if (kcName == null || kcName.isBlank()) {
                return "准备好了吗？有任何疑问随时可以问我～";
            }
            return "这道题涉及「" + kcName + "」，你目前掌握度 " + Math.round(mastery * 100) + "%，需要先回顾一下吗？";
        }

        return "准备好了吗？有任何疑问随时可以问我～";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildStarterActions(Map<String, Object> ctx) {
        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> weakKcs = (List<Map<String, Object>>) ctx.get("weak_kcs");

        if (weakKcs != null && !weakKcs.isEmpty()) {
            actions.add(action("knowledge_review", "帮我回顾相关知识点", "KNOWLEDGE_REVIEW"));
        }
        actions.add(action("problem_guide", "分析这道题的思路", "READING"));

        String recentFailedSubmissionId = (String) ctx.get("recent_failed_submission_id");
        if (recentFailedSubmissionId != null) {
            Map<String, Object> errorAction = action("error_chain", "我遇到了错误，帮我看看", "ERROR_FEEDBACK");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("submission_id", recentFailedSubmissionId);
            errorAction.put("payload", payload);
            actions.add(errorAction);
        }
        return actions;
    }

    private static Map<String, Object> action(String key, String label, String event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("event", event);
        return item;
    }
}
