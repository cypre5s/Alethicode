package com.alethicode.service.twin.arena;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S15: AI 对手代码生成 — 根据学生 mastery 调整 AI 写代码的风格。
 * 高 mastery：AI 写优雅但有 subtle bug 的代码。
 * 低 mastery：AI 写正确但啰嗦的代码。
 */
@Service
public class AiAdversaryService {

    private final JdbcTemplate jdbcTemplate;

    public AiAdversaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> generateAdversaryCode(Long userId, Long problemId) {
        double mastery = estimateUserMasteryForProblem(userId, problemId);
        String difficultyLevel = selectDifficultyLevel(mastery);
        String aiCode = generateCodeByDifficulty(problemId, difficultyLevel);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ai_code", aiCode);
        result.put("difficulty_level", difficultyLevel);
        result.put("mastery_estimate", mastery);
        return result;
    }

    String selectDifficultyLevel(double mastery) {
        if (mastery >= 0.8) return "expert_with_subtle_bug";
        if (mastery >= 0.5) return "competent";
        return "verbose_but_correct";
    }

    private double estimateUserMasteryForProblem(Long userId, Long problemId) {
        try {
            Double avg = jdbcTemplate.queryForObject("""
                SELECT AVG(m.mastery)::DOUBLE PRECISION
                FROM learner_kc_mastery m
                JOIN ai_problem_kc_mapping pkm ON pkm.kc_id = m.kc_id
                WHERE m.user_id = ? AND pkm.problem_id = ?
                """, Double.class, userId, problemId);
            return avg != null ? avg : 0.3;
        } catch (Exception e) {
            return 0.3;
        }
    }

    private String generateCodeByDifficulty(Long problemId, String level) {
        return switch (level) {
            case "expert_with_subtle_bug" -> """
                    # AI 的解答（看起来很优雅，但有一个小问题）
                    def solve(n):
                        return [i * 2 for i in range(1, n)]  # 注意这里从 1 开始
                    print(solve(int(input())))
                    """;
            case "competent" -> """
                    # AI 的解答
                    n = int(input())
                    result = []
                    for i in range(n):
                        result.append(i * 2)
                    print(result)
                    """;
            default -> """
                    # AI 的解答（能用，但可以更简洁）
                    n = int(input())
                    result = list()
                    i = 0
                    while i < n:
                        value = i * 2
                        result.append(value)
                        i = i + 1
                    output = str(result)
                    print(output)
                    """;
        };
    }
}
