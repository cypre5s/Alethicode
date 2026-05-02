package com.alethicode.service.aitutor.retrieval;

import com.alethicode.service.rag.RagServiceClient;
import com.alethicode.service.rag.dto.RagQueryHits;
import com.alethicode.service.rag.dto.RagSimilarErrorQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-K 召回相似错误：notebook 与 memory 两条线。
 *
 * <p>Phase 3 切流：原 {@code notebook_embedding <=> cast(? as vector)} +
 * {@code memory_embedding <=> cast(? as vector)} 全部废弃，统一走
 * alethicode-rag 的 LightRAG mix-mode。LightRAG 端 entity_type 把两条线分别索引为
 * {@code notebook} 与 {@code memory}（Phase 1 写入侧 + Phase 2 全量回填已就位），
 * 这里发两次 query 各取自己 namespace 的 hits，再用业务表反查 row shape。
 *
 * <p>API 与历史一致：返回 {@code Map<String, List<Map<String, Object>>>} 含
 * {@code similar_notebook_hits} / {@code similar_memory_hits} 两个 list；
 * row shape 字段（source_id / problem_id / error_taxonomy / summary / score 等）
 * 与历史一致。
 */
@Service
public class SimilarErrorRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(SimilarErrorRetrievalService.class);

    private static final int DEFAULT_LIMIT = 3;

    private final JdbcTemplate jdbcTemplate;
    private final RagServiceClient ragServiceClient;

    public SimilarErrorRetrievalService(JdbcTemplate jdbcTemplate, RagServiceClient ragServiceClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragServiceClient = ragServiceClient;
    }

    public Map<String, List<Map<String, Object>>> retrieve(
            Long userId,
            Long currentProblemId,
            String language,
            String errorTaxonomy,
            String queryText
    ) {
        if (userId == null) {
            return Map.of("similar_notebook_hits", List.of(), "similar_memory_hits", List.of());
        }
        String normalizedQuery = queryText == null ? "" : queryText.trim();
        if (normalizedQuery.isBlank()) {
            return Map.of("similar_notebook_hits", List.of(), "similar_memory_hits", List.of());
        }

        List<Map<String, Object>> notebookHits = retrieveNotebookHits(
                userId, currentProblemId, language, errorTaxonomy, normalizedQuery
        );
        List<Map<String, Object>> memoryHits = retrieveMemoryHits(
                userId, errorTaxonomy, normalizedQuery
        );
        return Map.of(
                "similar_notebook_hits", notebookHits,
                "similar_memory_hits", memoryHits
        );
    }

    private List<Map<String, Object>> retrieveNotebookHits(
            Long userId, Long currentProblemId, String language, String errorTaxonomy, String queryText
    ) {
        // 检索 notebook namespace。LightRAG 在 metadata 里携带 user_id / problem_id /
        // error_taxonomy / notebook_id，Java 端再去业务表反查 root_cause / fix_outcome /
        // student_reflection 等 prompt 用字段。
        RagQueryHits hits = ragServiceClient.querySimilarError(new RagSimilarErrorQueryRequest(
                userId, currentProblemId, errorTaxonomy, queryText, DEFAULT_LIMIT * 2
        ));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RagQueryHits.RetrievedChunk chunk : hits.chunks()) {
            Map<String, Object> meta = chunk.metadata() == null ? Map.of() : chunk.metadata();
            String entityTypeMeta = stringField(meta, "entity_type");
            if (entityTypeMeta != null && !"notebook".equalsIgnoreCase(entityTypeMeta)) {
                continue;
            }
            String notebookId = stringField(meta, "notebook_id");
            if (notebookId == null || notebookId.isBlank()) {
                continue;
            }
            Object problemMeta = meta.get("problem_id");
            if (currentProblemId != null && problemMeta != null
                    && currentProblemId.equals(toLong(problemMeta))) {
                continue;
            }
            Map<String, Object> bizRow = loadNotebookRow(notebookId, userId, language);
            if (bizRow == null) {
                continue;
            }
            String taxonomyHit = stringField(bizRow, "error_taxonomy");
            double score = scoreOf(chunk, taxonomyHit, errorTaxonomy);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source_type", "similar_notebook");
            row.put("source_id", notebookId);
            row.put("problem_id", bizRow.get("problem_id"));
            row.put("error_taxonomy", taxonomyHit);
            row.put("summary", bizRow.get("root_cause"));
            row.put("student_reflection", bizRow.get("student_reflection"));
            row.put("fix_outcome", bizRow.get("fix_outcome"));
            row.put("distance", chunk.score() == null ? 0.0 : Math.max(0.0, 1.0 - chunk.score()));
            row.put("score", round(score));
            rows.add(row);
        }
        rows.sort((l, r) -> Double.compare(toDouble(r.get("score")), toDouble(l.get("score"))));
        return rows.size() > DEFAULT_LIMIT ? new ArrayList<>(rows.subList(0, DEFAULT_LIMIT)) : rows;
    }

    private List<Map<String, Object>> retrieveMemoryHits(
            Long userId, String errorTaxonomy, String queryText
    ) {
        // memory namespace 的相似错误检索：LightRAG metadata 里有 memory_type，
        // Java 端只挑 error_pattern / learning_signal 这两类（与历史 SQL 过滤一致）。
        RagQueryHits hits = ragServiceClient.queryMemory(new com.alethicode.service.rag.dto.RagMemoryQueryRequest(
                userId, List.of(), errorTaxonomy, queryText, DEFAULT_LIMIT * 2
        ));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RagQueryHits.RetrievedChunk chunk : hits.chunks()) {
            Map<String, Object> meta = chunk.metadata() == null ? Map.of() : chunk.metadata();
            String memoryKey = stringField(meta, "memory_key");
            if (memoryKey == null || memoryKey.isBlank()) {
                continue;
            }
            Map<String, Object> bizRow = loadMemoryRow(userId, memoryKey);
            if (bizRow == null) {
                continue;
            }
            String memoryType = stringField(bizRow, "memory_type");
            if (memoryType == null
                    || !(memoryType.equals("error_pattern") || memoryType.equals("learning_signal"))) {
                continue;
            }
            Map<String, Object> payload = JsonHelper.parseJsonMap((String) bizRow.get("payload_json"));
            String taxonomyHit = String.valueOf(
                    payload.getOrDefault("error_taxonomy",
                            payload.getOrDefault("error_category", "")));
            double score = scoreOf(chunk, taxonomyHit, errorTaxonomy);
            double confidence = toDouble(bizRow.get("confidence"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source_type", "similar_memory");
            row.put("source_id", memoryKey);
            row.put("problem_id", bizRow.get("source_problem_id"));
            row.put("memory_type", memoryType);
            row.put("error_taxonomy", taxonomyHit);
            row.put("summary", String.valueOf(payload.getOrDefault("summary", payload.getOrDefault("memory_summary", ""))));
            row.put("same_detector", String.valueOf(payload.getOrDefault("detector_name", "")));
            row.put("distance", chunk.score() == null ? 0.0 : Math.max(0.0, 1.0 - chunk.score()));
            row.put("score", round(score + Math.max(0.0, confidence)));
            rows.add(row);
        }
        rows.sort((l, r) -> Double.compare(toDouble(r.get("score")), toDouble(l.get("score"))));
        return rows.size() > DEFAULT_LIMIT ? new ArrayList<>(rows.subList(0, DEFAULT_LIMIT)) : rows;
    }

    private Map<String, Object> loadNotebookRow(String notebookId, Long userId, String language) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id::text AS id, problem_id, error_taxonomy,
                       coalesce(root_cause, '') AS root_cause,
                       coalesce(student_reflection, '') AS student_reflection,
                       coalesce(fix_outcome, '') AS fix_outcome
                FROM ai_learner_notebook
                WHERE id::text = ? AND user_id = ? AND is_deleted = false
                  AND (? = '' OR language = ?)
                LIMIT 1
                """,
                notebookId, userId, blankToEmpty(language), blankToEmpty(language)
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> loadMemoryRow(Long userId, String memoryKey) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, memory_key, memory_type, source_problem_id, confidence,
                       memory_payload::text AS payload_json
                FROM ai_learner_memory
                WHERE user_id = ? AND memory_key = ? AND enabled = true
                  AND (expires_at IS NULL OR expires_at > now())
                LIMIT 1
                """,
                userId, memoryKey
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private double scoreOf(RagQueryHits.RetrievedChunk chunk, String taxonomyHit, String errorTaxonomy) {
        double base = chunk.score() == null ? 0.5 : Math.max(0.0, chunk.score());
        if (errorTaxonomy != null && !errorTaxonomy.isBlank()
                && taxonomyHit != null && taxonomyHit.equals(errorTaxonomy)) {
            base += 0.2;
        }
        return base;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringField(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        return value == null ? null : value.toString();
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static final class JsonHelper {
        private static Map<String, Object> parseJsonMap(String raw) {
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
            } catch (Exception e) {
                log.debug("parseJsonMap: failed for raw length={}", raw.length(), e);
                return Map.of();
            }
        }
    }
}
