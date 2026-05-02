package com.alethicode.service.aitutor.rlhf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 变体 RLHF 选择器：
 *
 * <p>把每个 agent 的若干 system_prompt 变体视作 MAB 的 arm；按 UCB1 选择当前应下发的变体，
 * 学生通过 {@code POST /api/ai-tutor/strategy-feedback} 产生 positive/negative 反馈后更新 ELO。
 *
 * <p>数据持久化：
 * <ul>
 *   <li>状态写入 {@code ai_learner_memory}（user_id=-1 表示系统账号），
 *       {@code memory_type='prompt_variant_score'}，
 *       {@code memory_key='prompt_variant::{agentKey}::{variantId}'}；</li>
 *   <li>{@code memory_payload} 存 ELO / 命中次数 / positive 次数等 JSON 字段。</li>
 * </ul>
 *
 * <p>不依赖任何业务表，仅复用 {@code ai_learner_memory} JSONB（与计划"不新建业务表"约束一致）。
 */
@Service
public class PromptVariantSelector {

    private static final Logger log = LoggerFactory.getLogger(PromptVariantSelector.class);

    /** 系统账号 user_id，用于把 prompt_variant_score 与真实用户 memory 隔离。 */
    public static final long SYSTEM_USER_ID = -1L;

    private static final double ELO_INITIAL = 1000.0;
    private static final double ELO_K = 32.0;
    private static final double ELO_EXPECT_PIVOT = 1000.0;
    private static final double UCB_EXPLORATION = Math.sqrt(2.0);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PromptVariantSelector(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 从给定候选中选出当前轮要使用的 variantId。变体不存在 → 返回第一个候选。
     */
    public String selectVariant(String agentKey, List<String> candidateVariantIds) {
        if (candidateVariantIds == null || candidateVariantIds.isEmpty()) {
            throw new IllegalArgumentException("candidateVariantIds is required");
        }
        Map<String, VariantStats> stats = loadStats(agentKey, candidateVariantIds);
        long totalPulls = 0;
        for (VariantStats s : stats.values()) {
            totalPulls += s.pulls;
        }
        double logN = Math.log(Math.max(1L, totalPulls));
        String winner = candidateVariantIds.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String variantId : candidateVariantIds) {
            VariantStats s = stats.get(variantId);
            double score;
            if (s == null || s.pulls == 0) {
                // 每个新变体至少跑一次
                score = Double.POSITIVE_INFINITY;
            } else {
                double mean = s.positive / (double) s.pulls;
                score = mean + UCB_EXPLORATION * Math.sqrt(logN / s.pulls);
            }
            if (score > bestScore) {
                bestScore = score;
                winner = variantId;
            }
        }
        return winner;
    }

    /**
     * 对一次选中的 variant 产生反馈。相当于把 winner 与 "field 平均对手" 做一次 ELO 对弈：
     * <ul>
     *   <li>{@code positive=true} → {@code actual_score = 1.0}；</li>
     *   <li>{@code positive=false} → {@code actual_score = 0.0}。</li>
     * </ul>
     */
    @Transactional
    public void recordOutcome(String agentKey, String variantId, boolean positive) {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey is required");
        }
        if (variantId == null || variantId.isBlank()) {
            throw new IllegalArgumentException("variantId is required");
        }
        ensureStatsRow(agentKey, variantId);
        VariantStats current = loadOneStatForUpdate(agentKey, variantId);
        double actual = positive ? 1.0 : 0.0;
        double expected = 1.0 / (1.0 + Math.pow(10.0, (ELO_EXPECT_PIVOT - current.elo) / 400.0));
        double newElo = current.elo + ELO_K * (actual - expected);

        VariantStats updated = new VariantStats(
                current.pulls + 1,
                current.positive + (positive ? 1 : 0),
                current.negative + (positive ? 0 : 1),
                newElo
        );
        persistStats(agentKey, variantId, updated);
    }

    /**
     * 管理端对比面板用：返回指定 agent 所有已记录 variant 的完整状态。
     */
    public List<Map<String, Object>> listVariants(String agentKey) {
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey is required");
        }
        String keyPrefix = variantKeyPrefix(agentKey);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT memory_key,
                       memory_payload::text AS payload_text,
                       updated_at
                FROM ai_learner_memory
                WHERE user_id = ?
                  AND memory_type = 'prompt_variant_score'
                  AND memory_key LIKE ? ESCAPE '!'
                ORDER BY memory_key ASC
                """,
                SYSTEM_USER_ID, escapeLikeLiteral(keyPrefix) + "%"
        );
        List<Map<String, Object>> results = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("memory_key"));
            String variantId = key.startsWith(keyPrefix) ? key.substring(keyPrefix.length()) : key;
            Map<String, Object> payload = parseJson(row.get("payload_text"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("variant_id", variantId);
            entry.put("pulls", payload.getOrDefault("pulls", 0));
            entry.put("positive", payload.getOrDefault("positive", 0));
            entry.put("negative", payload.getOrDefault("negative", 0));
            entry.put("elo", payload.getOrDefault("elo", ELO_INITIAL));
            entry.put("updated_at", row.get("updated_at"));
            results.add(entry);
        }
        return results;
    }

    private VariantStats loadOneStatForUpdate(String agentKey, String variantId) {
        String memoryKey = variantMemoryKey(agentKey, variantId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT memory_key, memory_payload::text AS payload_text
                FROM ai_learner_memory
                WHERE user_id = ?
                  AND memory_type = 'prompt_variant_score'
                  AND memory_key = ?
                FOR UPDATE
                """,
                SYSTEM_USER_ID, memoryKey
        );
        if (rows.isEmpty()) {
            return new VariantStats(0, 0, 0, ELO_INITIAL);
        }
        return parseStats(rows.get(0));
    }

    private Map<String, VariantStats> loadStats(String agentKey, List<String> variantIds) {
        Map<String, VariantStats> result = new LinkedHashMap<>();
        for (String v : variantIds) {
            result.put(v, new VariantStats(0, 0, 0, ELO_INITIAL));
        }
        String keyPrefix = variantKeyPrefix(agentKey);
        List<String> memoryKeys = variantIds.stream().map(v -> keyPrefix + v).toList();
        if (memoryKeys.isEmpty()) return result;

        String placeholders = String.join(",", memoryKeys.stream().map(k -> "?").toList());
        String sql = """
                SELECT memory_key, memory_payload::text AS payload_text
                FROM ai_learner_memory
                WHERE user_id = ?
                  AND memory_type = 'prompt_variant_score'
                  AND memory_key IN (""" + placeholders + """
                )
                """;
        Object[] args = new Object[1 + memoryKeys.size()];
        args[0] = SYSTEM_USER_ID;
        for (int i = 0; i < memoryKeys.size(); i++) {
            args[i + 1] = memoryKeys.get(i);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("memory_key"));
            String variantId = key.startsWith(keyPrefix) ? key.substring(keyPrefix.length()) : key;
            result.put(variantId, parseStats(row));
        }
        return result;
    }

    private void ensureStatsRow(String agentKey, String variantId) {
        String memoryKey = variantMemoryKey(agentKey, variantId);
        Map<String, Object> payload = buildPayload(agentKey, variantId,
                new VariantStats(0, 0, 0, ELO_INITIAL));
        jdbcTemplate.update(
                """
                INSERT INTO ai_learner_memory(
                    user_id, memory_key, memory_type, memory_value, confidence,
                    enabled, created_at, updated_at,
                    memory_payload, source_type
                ) VALUES (
                    ?, ?, 'prompt_variant_score', ?, 0.5,
                    true, now(), now(),
                    cast(? as jsonb), 'rlhf_prompt'
                )
                ON CONFLICT (user_id, memory_key) DO NOTHING
                """,
                SYSTEM_USER_ID, memoryKey,
                "pulls=0,elo=" + round4(ELO_INITIAL),
                toJson(payload)
        );
    }

    private void persistStats(String agentKey, String variantId, VariantStats stats) {
        String memoryKey = variantMemoryKey(agentKey, variantId);
        Map<String, Object> payload = buildPayload(agentKey, variantId, stats);
        jdbcTemplate.update(
                """
                INSERT INTO ai_learner_memory(
                    user_id, memory_key, memory_type, memory_value, confidence,
                    enabled, created_at, updated_at,
                    memory_payload, source_type
                ) VALUES (
                    ?, ?, 'prompt_variant_score', ?, 0.5,
                    true, now(), now(),
                    cast(? as jsonb), 'rlhf_prompt'
                )
                ON CONFLICT (user_id, memory_key) DO UPDATE SET
                    memory_value = EXCLUDED.memory_value,
                    memory_payload = EXCLUDED.memory_payload,
                    enabled = true,
                    updated_at = now()
                """,
                SYSTEM_USER_ID, memoryKey,
                "pulls=" + stats.pulls + ",elo=" + round4(stats.elo),
                toJson(payload)
        );
    }

    private Map<String, Object> buildPayload(String agentKey, String variantId, VariantStats stats) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pulls", stats.pulls);
        payload.put("positive", stats.positive);
        payload.put("negative", stats.negative);
        payload.put("elo", round4(stats.elo));
        payload.put("agent_key", agentKey);
        payload.put("variant_id", variantId);
        return payload;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize prompt variant payload", e);
        }
    }

    private VariantStats parseStats(Map<String, Object> row) {
        Map<String, Object> payload = parseJson(row.get("payload_text"));
        long pulls = ((Number) payload.getOrDefault("pulls", 0)).longValue();
        long positive = ((Number) payload.getOrDefault("positive", 0)).longValue();
        long negative = ((Number) payload.getOrDefault("negative", 0)).longValue();
        double elo = ((Number) payload.getOrDefault("elo", ELO_INITIAL)).doubleValue();
        return new VariantStats(pulls, positive, negative, elo);
    }

    private static String variantKeyPrefix(String agentKey) {
        return "prompt_variant::" + agentKey + "::";
    }

    private static String variantMemoryKey(String agentKey, String variantId) {
        return variantKeyPrefix(agentKey) + variantId;
    }

    private static String escapeLikeLiteral(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private Map<String, Object> parseJson(Object raw) {
        if (raw == null) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse prompt variant payload: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record VariantStats(long pulls, long positive, long negative, double elo) {
    }
}
