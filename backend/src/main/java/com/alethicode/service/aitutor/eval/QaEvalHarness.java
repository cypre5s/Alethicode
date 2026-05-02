package com.alethicode.service.aitutor.eval;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline evaluation harness for QA (Language Pack Question-Answering).
 * Evaluates (question, retrieved_pages, generated_answer) triples for
 * grounding accuracy, citation coverage, answer completeness, and refusal appropriateness.
 */
@Service
public class QaEvalHarness {

    private static final Logger log = LoggerFactory.getLogger(QaEvalHarness.class);

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;
    private final RolloutPolicyService rolloutPolicyService;

    public QaEvalHarness(JdbcTemplate jdbcTemplate, AiModelGateway aiModelGateway,
                         RolloutPolicyService rolloutPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
        this.rolloutPolicyService = rolloutPolicyService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledEvaluation() {
        log.info("QaEvalHarness: starting scheduled evaluation");
        try {
            Map<String, Object> report = evaluateBatch(20);
            rolloutPolicyService.evaluateHarnessGate("qa", "scheduled", report);
            log.info("QaEvalHarness: scheduled evaluation complete, sample_count={}", report.get("sample_count"));
        } catch (Exception e) {
            log.error("QaEvalHarness: scheduled evaluation failed: {}", e.getMessage());
        }
    }

    public Map<String, Object> evaluateBatch(int limit) {
        List<Map<String, Object>> samples = loadSamples(limit);
        if (samples.isEmpty()) {
            return Map.of("sample_count", 0, "message", "no QA samples found");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int groundedCorrect = 0;
        int refusalCorrect = 0;
        double totalCitationCoverage = 0.0;
        Map<String, Long> failureBuckets = new LinkedHashMap<>();

        for (Map<String, Object> sample : samples) {
            try {
                Map<String, Object> evalResult = evaluateSingle(sample);
                results.add(evalResult);

                if (extractBoolean(evalResult, "grounding_correct")) groundedCorrect++;
                if (extractBoolean(evalResult, "refusal_correct")) refusalCorrect++;
                totalCitationCoverage += toDouble(evalResult.get("citation_coverage"), 0.0);

                String bucket = classifyFailureBucket(evalResult);
                if (bucket != null) {
                    failureBuckets.merge(bucket, 1L, Long::sum);
                }
            } catch (Exception e) {
                log.warn("QA eval failed for message {}: {}", sample.get("message_id"), e.getMessage());
                failureBuckets.merge("eval_error", 1L, Long::sum);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sample_count", results.size());
        report.put("grounding_accuracy", results.isEmpty() ? 0.0 :
                Math.round((double) groundedCorrect / results.size() * 1000.0) / 1000.0);
        report.put("refusal_accuracy", results.isEmpty() ? 0.0 :
                Math.round((double) refusalCorrect / results.size() * 1000.0) / 1000.0);
        report.put("avg_citation_coverage", results.isEmpty() ? 0.0 :
                Math.round(totalCitationCoverage / results.size() * 1000.0) / 1000.0);
        report.put("failure_buckets", failureBuckets);
        return report;
    }

    public Map<String, Object> replaySample(Long messageId) {
        Map<String, Object> sample = loadSampleById(messageId);
        if (sample == null) {
            throw new IllegalStateException("QA sample not found: " + messageId);
        }
        Map<String, Object> evalResult = evaluateSingle(sample);
        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("message_id", messageId);
        replay.put("question", sample.get("question"));
        replay.put("retrieved_pages", sample.get("retrieved_pages"));
        replay.put("answer", sample.get("answer"));
        replay.put("eval_result", evalResult);
        replay.put("failure_bucket", classifyFailureBucket(evalResult));
        return replay;
    }

    private String classifyFailureBucket(Map<String, Object> evalResult) {
        if (!extractBoolean(evalResult, "grounding_correct")) {
            return "grounding_failure";
        }
        if (!extractBoolean(evalResult, "refusal_correct")) {
            return "refusal_failure";
        }
        double coverage = toDouble(evalResult.get("citation_coverage"), 0.0);
        if (coverage < 0.3) {
            return "citation_mismatch";
        }
        double completeness = toDouble(evalResult.get("completeness"), 0.0);
        if (completeness < 0.5) {
            return "incomplete_answer";
        }
        return null;
    }

    private Map<String, Object> loadSampleById(Long messageId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT a.id                          AS message_id,
                       q.content                     AS question,
                       a.answer_json::text           AS answer,
                       coalesce(r.page_hit_json::text, '[]') AS retrieved_pages,
                       CASE WHEN a.answer_json::text LIKE '%%"grounded": true%%'
                            THEN true ELSE false END AS grounded
                FROM language_pack_chat_message a
                JOIN language_pack_chat_message q
                     ON q.session_id = a.session_id AND q.id = a.id - 1 AND q.role = 'user'
                LEFT JOIN LATERAL (
                    SELECT page_hit_json
                    FROM language_pack_chat_retrieval_log
                    WHERE session_id = a.session_id
                    ORDER BY id DESC LIMIT 1
                ) r ON true
                WHERE a.id = ? AND a.role = 'assistant'
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("message_id", rs.getLong("message_id"));
                    row.put("question", rs.getString("question"));
                    row.put("answer", rs.getString("answer"));
                    row.put("retrieved_pages", rs.getString("retrieved_pages"));
                    row.put("grounded", rs.getBoolean("grounded"));
                    return row;
                },
                messageId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> evaluateSingle(Map<String, Object> sample) {
        String question = String.valueOf(sample.getOrDefault("question", ""));
        String retrievedPages = String.valueOf(sample.getOrDefault("retrieved_pages", "[]"));
        String answer = String.valueOf(sample.getOrDefault("answer", ""));
        boolean wasGrounded = extractBoolean(sample, "grounded");

        Map<String, Object> judgeResult = aiModelGateway.callForJson(
                """
                你是一名 QA 质量评估专家。请评估：
                1. grounding_correct (bool): 答案是否真正基于引用的证据页内容
                2. refusal_correct (bool): 如果是拒答，拒答是否合理
                3. citation_coverage (0.0-1.0): 答案中引用的内容占证据页相关内容的比例
                4. completeness (0.0-1.0): 答案是否完整回答了问题
                5. verdict: 一句话总结
                输出 JSON。
                """,
                """
                问题：%s
                
                检索到的证据页：%s
                
                生成的答案：%s
                
                原始标记 grounded=%s
                """.formatted(
                        abbreviate(question, 500),
                        abbreviate(retrievedPages, 3000),
                        abbreviate(answer, 2000),
                        wasGrounded
                )
        );

        return judgeResult;
    }

    private List<Map<String, Object>> loadSamples(int limit) {
        return jdbcTemplate.query(
                """
                SELECT a.id                          AS message_id,
                       q.content                     AS question,
                       a.answer_json::text           AS answer,
                       coalesce(r.page_hit_json::text, '[]') AS retrieved_pages,
                       CASE WHEN a.answer_json::text LIKE '%%"grounded": true%%'
                            THEN true ELSE false END AS grounded
                FROM language_pack_chat_message a
                JOIN language_pack_chat_message q
                     ON q.session_id = a.session_id AND q.id = a.id - 1 AND q.role = 'user'
                LEFT JOIN LATERAL (
                    SELECT page_hit_json
                    FROM language_pack_chat_retrieval_log
                    WHERE session_id = a.session_id
                    ORDER BY id DESC LIMIT 1
                ) r ON true
                WHERE a.role = 'assistant'
                  AND a.answer_json IS NOT NULL
                ORDER BY a.create_time DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("message_id", rs.getLong("message_id"));
                    row.put("question", rs.getString("question"));
                    row.put("answer", rs.getString("answer"));
                    row.put("retrieved_pages", rs.getString("retrieved_pages"));
                    row.put("grounded", rs.getBoolean("grounded"));
                    return row;
                },
                limit
        );
    }

    private boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return fallback;
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
