package com.alethicode.service.aitutor.profile;

import com.alethicode.service.rag.RagServiceClient;
import com.alethicode.service.rag.dto.RagMemoryQueryRequest;
import com.alethicode.service.rag.dto.RagQueryHits;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-K 语义召回 ai_learner_memory，对当前题 KC + 当前错误描述。
 *
 * <p>Phase 3 切流：原实现用 16 维 pgvector cosine（{@code memory_embedding <=> cast(? as vector)}）；
 * 现 100% 走 alethicode-rag 的 LightRAG mix-mode 检索，KG + 向量 + chunk 三路融合。
 * 返回行的 shape 与历史 API 保持兼容（{@code id / memory_key / memory_type /
 * memory_summary / confidence / source_problem_id / distance}），上游
 * Tutor / Agent / EvidencePackAssembler 不需调整。
 *
 * <p>核心权衡（与计划稿 § 现状审计一致）：
 * <ul>
 *   <li>{@code memory_id} 在 LightRAG 端用 {@code user_id:memory_key} 做反向锚点，
 *       回填脚本（Phase 2）已就此约定写入 metadata。Java 端再用 memory_key 反查
 *       业务表，拿回 confidence / source_problem_id 等业务字段。</li>
 *   <li>{@code distance} 已无 cosine 语义，改记 LightRAG 的 chunk score（若有），
 *       否则 0.0。Tutor 现有用法只是排序 + log，不做绝对阈值判定。</li>
 * </ul>
 */
@Service
public class LearnerMemorySemanticRetrievalService {

    private static final double MIN_CONFIDENCE = 0.5;
    private static final int DEFAULT_TOP_K = 5;

    private final JdbcTemplate jdbcTemplate;
    private final RagServiceClient ragServiceClient;
    private final ObjectMapper objectMapper;

    public LearnerMemorySemanticRetrievalService(JdbcTemplate jdbcTemplate,
                                                  RagServiceClient ragServiceClient,
                                                  ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragServiceClient = ragServiceClient;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> retrieveByContext(Long userId,
                                                       List<String> currentKcs,
                                                       String errorContext,
                                                       int topK) {
        if (userId == null) {
            return List.of();
        }
        String queryText = buildQueryText(currentKcs, errorContext);
        if (queryText.isBlank()) {
            return List.of();
        }
        int safeTopK = Math.max(1, Math.min(topK <= 0 ? DEFAULT_TOP_K : topK, DEFAULT_TOP_K));

        RagQueryHits hits = ragServiceClient.queryMemory(new RagMemoryQueryRequest(
                userId, currentKcs == null ? List.of() : currentKcs, errorContext, queryText, safeTopK
        ));

        List<RagQueryHits.RetrievedChunk> chunks = hits.chunks();
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (RagQueryHits.RetrievedChunk chunk : chunks) {
            Map<String, Object> meta = chunk.metadata() == null ? Map.of() : chunk.metadata();
            String memoryKey = stringField(meta, "memory_key");
            if (memoryKey == null || memoryKey.isBlank()) {
                continue;
            }
            Map<String, Object> bizRow = loadBusinessRow(userId, memoryKey);
            if (bizRow == null) {
                continue;
            }
            double confidence = toDouble(bizRow.get("confidence"));
            if (confidence < MIN_CONFIDENCE) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", bizRow.get("id"));
            row.put("memory_key", memoryKey);
            row.put("memory_type", bizRow.get("memory_type"));
            row.put("memory_summary", deriveSummary((String) bizRow.get("memory_value"), (String) bizRow.get("payload_json")));
            row.put("confidence", round(confidence));
            row.put("source_problem_id", bizRow.get("source_problem_id"));
            row.put("distance", round(chunk.score() == null ? 0.0 : Math.max(0.0, 1.0 - chunk.score())));
            rows.add(row);
            if (rows.size() >= safeTopK) {
                break;
            }
        }
        return rows;
    }

    private Map<String, Object> loadBusinessRow(Long userId, String memoryKey) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, memory_key, memory_type, memory_value,
                       memory_payload::text AS payload_json,
                       confidence, source_problem_id
                FROM ai_learner_memory
                WHERE user_id = ? AND memory_key = ? AND enabled = true
                  AND (expires_at IS NULL OR expires_at > now())
                LIMIT 1
                """,
                userId, memoryKey
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String buildQueryText(List<String> currentKcs, String errorContext) {
        StringBuilder sb = new StringBuilder();
        if (currentKcs != null && !currentKcs.isEmpty()) {
            sb.append("当前知识点：").append(String.join("、", currentKcs)).append('；');
        }
        if (errorContext != null && !errorContext.isBlank()) {
            String trimmed = errorContext.trim();
            if (trimmed.length() > 600) {
                trimmed = trimmed.substring(0, 600);
            }
            sb.append("当前错误描述：").append(trimmed);
        }
        return sb.toString();
    }

    private String deriveSummary(String memoryValue, String payloadJson) {
        if (payloadJson != null && !payloadJson.isBlank()) {
            try {
                Map<?, ?> payload = objectMapper.readValue(payloadJson, Map.class);
                Object summary = payload.get("summary");
                if (summary != null && !summary.toString().isBlank()) {
                    return summary.toString();
                }
            } catch (Exception ignored) {
                // fall through to memoryValue
            }
        }
        return memoryValue == null ? "" : memoryValue;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static String stringField(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        return value == null ? null : value.toString();
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
