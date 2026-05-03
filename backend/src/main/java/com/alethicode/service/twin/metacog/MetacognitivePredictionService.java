package com.alethicode.service.twin.metacog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetacognitivePredictionService {

    private static final Logger log = LoggerFactory.getLogger(MetacognitivePredictionService.class);

    private final JdbcTemplate jdbcTemplate;

    public MetacognitivePredictionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long recordPrediction(Long userId, Long problemId, String predictedOutput,
                                  String predictedReason, String codeSnapshot, String sessionId) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO ai_metacognitive_event (user_id, problem_id, predicted_output, predicted_reason, code_snapshot, session_id)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class, userId, problemId, predictedOutput, predictedReason, codeSnapshot, sessionId);
    }

    public void verify(Long eventId, String actualOutput) {
        String predicted = jdbcTemplate.queryForObject(
                "SELECT predicted_output FROM ai_metacognitive_event WHERE id = ?",
                String.class, eventId);

        String diffKind = classifyDiff(predicted, actualOutput);

        jdbcTemplate.update("""
            UPDATE ai_metacognitive_event
            SET actual_output = ?, diff_kind = ?, verified_at = NOW()
            WHERE id = ?
            """, actualOutput, diffKind, eventId);
    }

    public Map<String, Object> getMetacognitiveMap(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        Integer totalPredicts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)::INTEGER FROM ai_metacognitive_event WHERE user_id = ?",
                Integer.class, userId);
        result.put("total_predicts", totalPredicts != null ? totalPredicts : 0);

        Integer exactMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)::INTEGER FROM ai_metacognitive_event WHERE user_id = ? AND diff_kind = 'exact_match'",
                Integer.class, userId);
        int total = totalPredicts != null ? totalPredicts : 0;
        int exact = exactMatches != null ? exactMatches : 0;
        result.put("exact_match_rate", total > 0 ? (double) exact / total : 0.0);

        List<Map<String, Object>> hotMisconceptions = jdbcTemplate.query("""
            SELECT diff_kind, COUNT(*)::INTEGER AS count
            FROM ai_metacognitive_event
            WHERE user_id = ? AND diff_kind IS NOT NULL AND diff_kind != 'exact_match'
            GROUP BY diff_kind
            ORDER BY count DESC
            LIMIT 10
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("diff_kind", rs.getString("diff_kind"));
            row.put("count", rs.getInt("count"));
            return row;
        }, userId);
        result.put("hot_misconceptions", hotMisconceptions);

        return result;
    }

    String classifyDiff(String predicted, String actual) {
        if (predicted == null || actual == null) return "wrong_value";
        String p = predicted.strip();
        String a = actual.strip();
        if (p.equals(a)) return "exact_match";
        if (a.contains("Error") || a.contains("Traceback")) return "crash";
        if (p.isEmpty()) return "wrong_value";
        if (a.contains(p) || p.contains(a)) return "partial";
        try {
            Double.parseDouble(p);
            Double.parseDouble(a);
            return "wrong_value";
        } catch (NumberFormatException ignored) {}
        return "wrong_value";
    }
}
