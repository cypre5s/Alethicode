package com.alethicode.service.aitutor.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 导学卡片的离线评估工具，使用八维教学 Rubric 进行 LLM-as-Judge。
 */
@Service
public class TutorEvalHarness {

    private static final Logger log = LoggerFactory.getLogger(TutorEvalHarness.class);

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final RolloutPolicyService rolloutPolicyService;

    public TutorEvalHarness(JdbcTemplate jdbcTemplate, AiModelGateway aiModelGateway, ObjectMapper objectMapper,
                            RolloutPolicyService rolloutPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.rolloutPolicyService = rolloutPolicyService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledEvaluation() {
        log.info("TutorEvalHarness: starting scheduled evaluation");
        try {
            Map<String, Object> report = evaluateBatch(null, 20);
            int sampleCount = ((Number) report.getOrDefault("sample_count", 0)).intValue();
            if (sampleCount > 0) {
                persistCurrentScore(toDouble(report.get("avg_overall_score"), 0.0), sampleCount);
            }
            rolloutPolicyService.evaluateHarnessGate("tutor", "scheduled", report);
            log.info("TutorEvalHarness: scheduled evaluation complete, sample_count={}", report.get("sample_count"));
            checkQualityTrend(report);
        } catch (Exception e) {
            log.error("TutorEvalHarness: scheduled evaluation failed: {}", e.getMessage());
        }
    }

    /**
     * 对比近期评估分与历史基线，发现质量退化后触发回滚门禁。
     */
    private void checkQualityTrend(Map<String, Object> currentReport) {
        try {
            double currentAvg = toDouble(currentReport.get("avg_overall_score"), 0.0);
            int currentSampleCount = ((Number) currentReport.getOrDefault("sample_count", 0)).intValue();
            if (currentSampleCount < 5) {
                log.debug("EntropyControl: insufficient samples for trend check ({})", currentSampleCount);
                return;
            }

            Double historicalBaseline = loadHistoricalBaseline();

            if (historicalBaseline == null) {
                log.info("EntropyControl: no historical baseline yet, current_avg={}", currentAvg);
                return;
            }

            double degradation = historicalBaseline - currentAvg;
            double degradationPct = historicalBaseline > 0 ? degradation / historicalBaseline : 0.0;

            if (degradationPct > 0.15) {
                log.error("EntropyControl: SEVERE quality degradation detected! " +
                        "current={}, baseline={}, degradation={} ({}%)",
                        round3(currentAvg), round3(historicalBaseline),
                        round3(degradation), round3(degradationPct * 100));
                rolloutPolicyService.evaluateHarnessGate("tutor", "entropy_control",
                        Map.of("avg_overall_score", currentAvg, "grounding_accuracy", 0.0,
                                "refusal_accuracy", 0.0, "sample_count", currentSampleCount));
            } else if (degradationPct > 0.08) {
                log.warn("EntropyControl: moderate quality decline. current={}, baseline={}, decline={}%",
                        round3(currentAvg), round3(historicalBaseline), round3(degradationPct * 100));
            } else {
                log.info("EntropyControl: quality stable. current={}, baseline={}", round3(currentAvg), round3(historicalBaseline));
            }
        } catch (Exception e) {
            log.warn("EntropyControl: trend check failed: {}", e.getMessage());
        }
    }

    private Double loadHistoricalBaseline() {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select avg(score) from (
                        select (event_data->>'avg_overall_score')::double precision as score
                        from ai_tutor_workflow_event
                        where event_type = 'quality_trend_score'
                          and created_at > now() - interval '14 day'
                          and created_at < now() - interval '1 day'
                        order by created_at desc
                        limit 7
                    ) recent_scores
                    """,
                    Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void persistCurrentScore(double avgScore, int sampleCount) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("avg_overall_score", round3(avgScore));
            payload.put("sample_count", sampleCount);
            payload.put("quality_source", "llm_judge");
            payload.put("judge", "TutorEvalHarness");
            jdbcTemplate.update(
                    """
                    insert into ai_tutor_workflow_event(
                        session_id, run_id, thread_id, event_type,
                        runtime_state, server_event, event_data, created_at
                    )
                    values (
                        'system:llm_judge',
                        concat('run:llm_judge:', extract(epoch from now())::bigint),
                        'thread:llm_judge',
                        'quality_trend_score',
                        'COMPLETED',
                        'QUALITY_TREND_SCORE',
                        cast(? as jsonb),
                        now()
                    )
                    """,
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("EntropyControl: failed to persist score: {}", e.getMessage());
        }
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * 从生成日志中批量评估历史证据和卡片样本。
     *
     * @param cardType 卡片类型过滤条件，null 表示全部
     * @param limit 最大评估样本数
     * @return 聚合评估报告
     */
    public Map<String, Object> evaluateBatch(String cardType, int limit) {
        List<Map<String, Object>> samples = loadSamples(cardType, limit);
        if (samples.isEmpty()) {
            return Map.of("sample_count", 0, "message", "no samples found");
        }

        List<EvalResult> results = new ArrayList<>();
        Map<String, Long> failureBuckets = new LinkedHashMap<>();
        for (Map<String, Object> sample : samples) {
            try {
                EvalResult result = evaluateSingle(sample);
                results.add(result);
                String bucket = classifyTutorFailureBucket(result);
                if (bucket != null) {
                    failureBuckets.merge(bucket, 1L, Long::sum);
                }
            } catch (Exception e) {
                log.warn("Eval failed for sample {}: {}", sample.get("log_id"), e.getMessage());
                failureBuckets.merge("eval_error", 1L, Long::sum);
            }
        }

        Map<String, Object> report = buildReport(results, cardType);
        report.put("failure_buckets", failureBuckets);
        return report;
    }

    public Map<String, Object> replaySample(Long logId) {
        List<Map<String, Object>> samples = jdbcTemplate.query(
                """
                SELECT id AS log_id, card_type,
                       request_summary::text AS evidence,
                       response_summary::text AS generated_card
                FROM ai_tutor_generation_log WHERE id = ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("log_id", rs.getLong("log_id"));
                    row.put("card_type", rs.getString("card_type"));
                    row.put("evidence", rs.getString("evidence"));
                    row.put("generated_card", rs.getString("generated_card"));
                    return row;
                },
                logId
        );
        if (samples.isEmpty()) {
            throw new IllegalStateException("Tutor sample not found: " + logId);
        }
        EvalResult result = evaluateSingle(samples.getFirst());
        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("log_id", logId);
        replay.put("eval_result", Map.of(
                "overall_score", result.overallScore(),
                "verdict", result.verdict(),
                "flags", result.flags()
        ));
        replay.put("failure_bucket", classifyTutorFailureBucket(result));
        return replay;
    }

    private String classifyTutorFailureBucket(EvalResult result) {
        if (result.flags().contains("answer_leakage")) {
            return "answer_leakage";
        }
        if (result.flags().contains("factual_error")) {
            return "factual_error";
        }
        if (result.overallScore() < 0.4) {
            return "low_quality";
        }
        Double pedagogyScore = result.dimensionScores().get(EvalDimension.PEDAGOGICAL_FIT);
        if (pedagogyScore != null && pedagogyScore < 0.5) {
            return "pedagogy_mismatch";
        }
        return null;
    }

    /**
     * 使用 LLM 裁判评估单个证据和卡片样本。
     */
    public EvalResult evaluateSingle(Map<String, Object> sample) {
        String sampleCardType = String.valueOf(sample.getOrDefault("card_type", "unknown"));
        String evidence = String.valueOf(sample.getOrDefault("evidence", ""));
        String generatedCard = String.valueOf(sample.getOrDefault("generated_card", ""));

        String rubricText = Arrays.stream(EvalDimension.values())
                .map(d -> "- " + d.label() + "（" + d.name() + "）: " + d.description())
                .collect(Collectors.joining("\n"));

        Map<String, Object> judgeResult = aiModelGateway.callForJson(
                """
                你是一名教学内容质量评估专家（LLM-as-Judge）。
                请根据以下评估维度，对一份 AI 生成的教学卡片进行打分。
                
                评估维度：
                %s
                
                输出 JSON：
                {
                  "overall_score": 0.0~1.0,
                  "dimension_scores": {"FACTUAL_CORRECTNESS": 0.0~1.0, "PEDAGOGICAL_FIT": 0.0~1.0, ...},
                  "verdict": "一句话总结",
                  "flags": ["如有问题标签，如 answer_leakage, factual_error 等"]
                }
                """.formatted(rubricText),
                """
                卡片类型：%s
                
                原始证据：
                %s
                
                生成的卡片：
                %s
                """.formatted(sampleCardType, abbreviate(evidence, 3000), abbreviate(generatedCard, 3000))
        );

        double overallScore = toDouble(judgeResult.get("overall_score"), 0.5);
        Map<EvalDimension, Double> dimensionScores = parseDimensionScores(judgeResult.get("dimension_scores"));
        String verdict = String.valueOf(judgeResult.getOrDefault("verdict", ""));
        List<String> flags = parseStringList(judgeResult.get("flags"));

        return new EvalResult(sampleCardType, overallScore, dimensionScores, verdict, flags);
    }

    private List<Map<String, Object>> loadSamples(String cardType, int limit) {
        String sql = """
                SELECT id AS log_id,
                       card_type,
                       request_summary::text AS evidence,
                       response_summary::text AS generated_card
                FROM ai_tutor_generation_log
                WHERE (?::text IS NULL OR card_type = ?)
                  AND schema_pass = true
                ORDER BY created_at DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("log_id", rs.getLong("log_id"));
            row.put("card_type", rs.getString("card_type"));
            row.put("evidence", rs.getString("evidence"));
            row.put("generated_card", rs.getString("generated_card"));
            return row;
        }, cardType, cardType, limit);
    }

    private Map<String, Object> buildReport(List<EvalResult> results, String cardType) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("card_type_filter", cardType == null ? "all" : cardType);
        report.put("sample_count", results.size());

        double avgOverall = results.stream().mapToDouble(EvalResult::overallScore).average().orElse(0.0);
        report.put("avg_overall_score", Math.round(avgOverall * 1000.0) / 1000.0);

        Map<String, Double> avgDimensions = new LinkedHashMap<>();
        for (EvalDimension dim : EvalDimension.values()) {
            double avg = results.stream()
                    .mapToDouble(r -> r.dimensionScores().getOrDefault(dim, 0.5))
                    .average().orElse(0.5);
            avgDimensions.put(dim.name(), Math.round(avg * 1000.0) / 1000.0);
        }
        report.put("avg_dimension_scores", avgDimensions);

        Map<String, Long> flagCounts = new LinkedHashMap<>();
        results.stream()
                .flatMap(r -> r.flags().stream())
                .forEach(flag -> flagCounts.merge(flag, 1L, Long::sum));
        report.put("flag_distribution", flagCounts);

        return report;
    }

    @SuppressWarnings("unchecked")
    private Map<EvalDimension, Double> parseDimensionScores(Object scoresObj) {
        Map<EvalDimension, Double> scores = new LinkedHashMap<>();
        if (!(scoresObj instanceof Map<?, ?> map)) {
            return scores;
        }
        for (EvalDimension dim : EvalDimension.values()) {
            Object val = map.get(dim.name());
            scores.put(dim, toDouble(val, 0.5));
        }
        return scores;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) result.add(String.valueOf(item));
        }
        return result;
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
