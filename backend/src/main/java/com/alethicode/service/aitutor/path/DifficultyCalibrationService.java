package com.alethicode.service.aitutor.path;

import com.alethicode.service.ai.AiModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DifficultyCalibrationService {

    private static final Logger log = LoggerFactory.getLogger(DifficultyCalibrationService.class);
    private static final double ALPHA_COLD_START = 0.7;
    private static final int MIN_SUBMISSIONS_FOR_DATA_DRIVEN = 5;

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;

    public DifficultyCalibrationService(JdbcTemplate jdbcTemplate, AiModelGateway aiModelGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
    }

    public Map<String, Object> calibrateByLanguagePack(Long languagePackId) {
        List<Map<String, Object>> problems = jdbcTemplate.queryForList("""
            SELECT p.id, p.title, p.description, p.difficulty_score
            FROM problem p
            JOIN language_pack_problem_mapping pm ON pm.problem_id = p.id
            WHERE pm.language_pack_id = ?
            ORDER BY p.id
            """, languagePackId);

        int llmEstimated = 0;
        int dataCalibrated = 0;

        for (Map<String, Object> problem : problems) {
            Long problemId = ((Number) problem.get("id")).longValue();
            Number existingScore = (Number) problem.get("difficulty_score");

            Map<String, Object> submissionStats = loadSubmissionStats(problemId);
            long totalSubmissions = ((Number) submissionStats.getOrDefault("total", 0)).longValue();

            if (totalSubmissions >= MIN_SUBMISSIONS_FOR_DATA_DRIVEN) {
                double acRate = ((Number) submissionStats.getOrDefault("ac_rate", 0.5)).doubleValue();
                double avgAttempts = ((Number) submissionStats.getOrDefault("avg_attempts", 1)).doubleValue();
                double dataDrivenScore = computeDataDrivenDifficulty(acRate, avgAttempts);

                double finalScore;
                if (existingScore != null) {
                    finalScore = (1 - ALPHA_COLD_START) * existingScore.doubleValue()
                               + ALPHA_COLD_START * dataDrivenScore;
                } else {
                    finalScore = dataDrivenScore;
                }

                updateDifficultyScore(problemId, round(finalScore));
                dataCalibrated++;
            } else if (existingScore == null) {
                double llmScore = estimateDifficultyViaLlm(problem);
                updateDifficultyScore(problemId, round(llmScore));
                llmEstimated++;
            }
        }

        log.info("Difficulty calibration completed: languagePackId={}, total={}, llmEstimated={}, dataCalibrated={}",
                languagePackId, problems.size(), llmEstimated, dataCalibrated);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("language_pack_id", languagePackId);
        result.put("total_problems", problems.size());
        result.put("llm_estimated", llmEstimated);
        result.put("data_calibrated", dataCalibrated);
        return result;
    }

    private Map<String, Object> loadSubmissionStats(Long problemId) {
        try {
            return jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       ROUND(COUNT(CASE WHEN result = 0 THEN 1 END)::numeric / NULLIF(COUNT(*), 0), 3) AS ac_rate,
                       ROUND(COUNT(*)::numeric / NULLIF(COUNT(DISTINCT user_id), 0), 1) AS avg_attempts
                FROM submission
                WHERE problem_id = ?
                """, problemId);
        } catch (Exception e) {
            return Map.of("total", 0, "ac_rate", 0.5, "avg_attempts", 1);
        }
    }

    private double computeDataDrivenDifficulty(double acRate, double avgAttempts) {
        double diffFromAcRate = 1.0 - acRate;
        double diffFromAttempts = Math.min(1.0, (avgAttempts - 1.0) / 9.0);
        return 0.6 * diffFromAcRate + 0.4 * diffFromAttempts;
    }

    private double estimateDifficultyViaLlm(Map<String, Object> problem) {
        String title = (String) problem.getOrDefault("title", "");
        String desc = problem.get("description") != null
                ? problem.get("description").toString() : "";
        if (desc.length() > 1000) desc = desc.substring(0, 1000) + "...";

        String systemPrompt = """
                你是编程教育专家。评估以下编程题对于非计算机专业的 Python 初学者的难度。
                输出 JSON：{"difficulty_score": 0.0到1.0的小数}
                0.0 = 最简单（直接 print），0.5 = 中等（需要循环+条件），1.0 = 最难（需要递归/复杂算法）
                """;

        String userPrompt = "题目标题：%s\n题目描述：%s".formatted(title, desc);

        try {
            Map<String, Object> result = aiModelGateway.callForJson(systemPrompt, userPrompt);
            Number score = (Number) result.get("difficulty_score");
            return score != null ? Math.max(0, Math.min(1, score.doubleValue())) : 0.5;
        } catch (Exception e) {
            log.warn("LLM difficulty estimation failed for problem {}: {}", problem.get("id"), e.getMessage());
            return 0.5;
        }
    }

    private void updateDifficultyScore(Long problemId, double score) {
        jdbcTemplate.update("UPDATE problem SET difficulty_score = ? WHERE id = ?", score, problemId);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
