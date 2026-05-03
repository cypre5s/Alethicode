package com.alethicode.service.twin.teach;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生教 AI 会话管理。
 * AI 抛出典型 misconception，学生纠正并解释，系统评分教学清晰度。
 * 不调 LLM：misconception 直接从 ai_learner_memory 高频错误中取，
 * 评分使用规则引擎（字数 + 是否包含代码 + 是否提到关键词）。
 */
@Service
public class TeachAiSessionService {

    private static final Logger log = LoggerFactory.getLogger(TeachAiSessionService.class);
    private static final int MAX_ROUNDS = 3;
    private static final int MAX_ACTIVE_SESSIONS = 5;

    private final JdbcTemplate jdbcTemplate;

    public TeachAiSessionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> startSession(Long userId, Long targetKcId, Long problemId) {
        String misconception = findMisconceptionForKc(targetKcId);
        if (misconception == null) {
            misconception = generateDefaultMisconception(targetKcId);
        }

        Long sessionId = jdbcTemplate.queryForObject("""
            INSERT INTO teach_ai_session (user_id, problem_id, target_kc_id, misconception_text)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """, Long.class, userId, problemId, targetKcId, misconception);

        String kcName = getKcName(targetKcId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("ai_misconception", misconception);
        result.put("ai_persona", "我是刚学 " + kcName + " 的同学，有点搞不明白");
        result.put("kc_name", kcName);
        return result;
    }

    public Map<String, Object> submitExplanation(Long userId, Long sessionId, String explanation) {
        Map<String, Object> session = jdbcTemplate.queryForMap(
                "SELECT user_id, target_kc_id, misconception_text, round_count FROM teach_ai_session WHERE id = ?",
                sessionId);

        Long ownerId = ((Number) session.get("user_id")).longValue();
        if (!ownerId.equals(userId)) {
            throw new IllegalArgumentException("session-not-yours");
        }
        int roundCount = (int) session.get("round_count");
        if (roundCount >= MAX_ROUNDS) {
            throw new IllegalArgumentException("max-rounds-reached");
        }

        String misconception = (String) session.get("misconception_text");
        Map<String, Object> grading = gradeExplanation(explanation, misconception);

        int score = (int) grading.get("total_score");
        String feedback = (String) grading.get("feedback");

        jdbcTemplate.update("""
            UPDATE teach_ai_session
            SET student_explanation = ?, grader_score = ?, grader_feedback = ?,
                grader_metadata = ?::JSONB, round_count = round_count + 1,
                completed_at = CASE WHEN round_count + 1 >= ? THEN NOW() ELSE completed_at END
            WHERE id = ?
            """, explanation, score, feedback,
                "{}", MAX_ROUNDS, sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grader_score", score);
        result.put("grader_feedback", feedback);
        result.put("grader_dimensions", grading.get("dimensions"));
        result.put("round", roundCount + 1);
        result.put("can_continue", roundCount + 1 < MAX_ROUNDS);

        if (score < 60 && roundCount + 1 < MAX_ROUNDS) {
            result.put("ai_followup_question", generateFollowupQuestion(score));
        }
        return result;
    }

    public List<Map<String, Object>> listSessions(Long userId) {
        return jdbcTemplate.query("""
            SELECT s.id, s.target_kc_id, kc.name AS kc_name, s.grader_score,
                   s.round_count, s.created_at, s.completed_at
            FROM teach_ai_session s
            LEFT JOIN language_pack_kc kc ON kc.id = s.target_kc_id
            WHERE s.user_id = ?
            ORDER BY s.created_at DESC
            LIMIT 20
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("session_id", rs.getLong("id"));
            row.put("kc_name", rs.getString("kc_name"));
            row.put("grader_score", rs.getObject("grader_score"));
            row.put("round_count", rs.getInt("round_count"));
            row.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
            Timestamp ct = rs.getTimestamp("completed_at");
            row.put("completed_at", ct != null ? ct.toInstant().toString() : null);
            return row;
        }, userId);
    }

    Map<String, Object> gradeExplanation(String explanation, String misconception) {
        int clarity = 0, correctness = 0, useOfExample = 0, addressingMisconception = 0;

        if (explanation.length() >= 30) clarity += 10;
        if (explanation.length() >= 80) clarity += 10;
        if (explanation.length() >= 150) clarity += 5;

        if (explanation.contains("因为") || explanation.contains("所以") || explanation.contains("原因"))
            correctness += 10;
        if (explanation.contains("其实") || explanation.contains("正确") || explanation.contains("应该"))
            correctness += 10;
        if (!explanation.contains(misconception.substring(0, Math.min(10, misconception.length()))))
            correctness += 5;

        if (explanation.contains("比如") || explanation.contains("例如") || explanation.contains("举个例子"))
            useOfExample += 15;
        if (explanation.contains("```") || explanation.contains("print") || explanation.contains("range"))
            useOfExample += 10;

        if (explanation.toLowerCase().contains("不对") || explanation.toLowerCase().contains("错"))
            addressingMisconception += 10;
        if (explanation.length() >= 50) addressingMisconception += 5;
        if (explanation.contains("？") || explanation.contains("?"))
            addressingMisconception += 5;

        clarity = Math.min(25, clarity);
        correctness = Math.min(25, correctness);
        useOfExample = Math.min(25, useOfExample);
        addressingMisconception = Math.min(25, addressingMisconception);

        int total = clarity + correctness + useOfExample + addressingMisconception;

        String feedback;
        if (total >= 75) {
            feedback = "讲得很清楚！你对这个概念的理解很到位。";
        } else if (total >= 50) {
            feedback = "解释得不错，如果能再加一个具体的例子会更好。";
        } else if (total >= 25) {
            feedback = "有道理，不过能不能再详细说说为什么原来的理解是错的？";
        } else {
            feedback = "嗯...我好像还是不太明白，能不能换个方式解释？比如举一个实际的例子？";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_score", total);
        result.put("feedback", feedback);
        result.put("dimensions", Map.of(
                "clarity", clarity,
                "correctness", correctness,
                "use_of_example", useOfExample,
                "addressing_misconception", addressingMisconception
        ));
        return result;
    }

    private String findMisconceptionForKc(Long kcId) {
        try {
            return jdbcTemplate.queryForObject("""
                SELECT memory_value FROM ai_learner_memory
                WHERE memory_type = 'misconception' AND enabled = TRUE
                  AND memory_payload->>'kc_id' = ?::TEXT
                ORDER BY confidence DESC, updated_at DESC
                LIMIT 1
                """, String.class, kcId);
        } catch (Exception e) {
            return null;
        }
    }

    private String generateDefaultMisconception(Long kcId) {
        String kcName = getKcName(kcId);
        return "我觉得 " + kcName + " 应该是从 1 开始的吧？";
    }

    private String getKcName(Long kcId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM language_pack_kc WHERE id = ?", String.class, kcId);
        } catch (Exception e) {
            return "这个概念";
        }
    }

    private String generateFollowupQuestion(int score) {
        if (score < 15) return "能不能给我举一个简单的代码例子？我看例子可能更容易理解。";
        if (score < 30) return "你说的我有点懂了，但为什么原来的想法是错的呢？";
        return "差不多理解了！如果遇到类似的情况，我该怎么避免犯同样的错？";
    }
}
