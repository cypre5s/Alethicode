package com.alethicode.service.twin.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningHealthAggregator {

    private final JdbcTemplate jdbcTemplate;

    public LearningHealthAggregator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> aggregate(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mastery", aggregateMastery(userId));
        result.put("frequency", aggregateFrequency(userId));
        result.put("difficulty_curve", aggregateDifficultyCurve(userId));
        result.put("due_reviews", aggregateDueReviews(userId));
        return result;
    }

    private Map<String, Object> aggregateMastery(Long userId) {
        Map<String, Object> mastery = new LinkedHashMap<>();
        try {
            Double overall = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(mastery), 0) FROM learner_kc_mastery WHERE user_id = ?",
                    Double.class, userId);
            mastery.put("overall", overall != null ? overall : 0.0);
        } catch (Exception e) {
            mastery.put("overall", 0.0);
        }

        List<Map<String, Object>> topKcs = jdbcTemplate.query("""
            SELECT kc.name, m.mastery
            FROM learner_kc_mastery m
            JOIN language_pack_kc kc ON kc.id = m.kc_id
            WHERE m.user_id = ?
            ORDER BY m.mastery DESC
            LIMIT 5
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", rs.getString("name"));
            row.put("mastery", rs.getDouble("mastery"));
            return row;
        }, userId);
        mastery.put("by_kc_top5", topKcs);
        return mastery;
    }

    private Map<String, Object> aggregateFrequency(Long userId) {
        Map<String, Object> freq = new LinkedHashMap<>();
        try {
            Map<String, Object> stats = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(submit_count, 0) AS submits_30d, COALESCE(active_days_30d, 0) AS active_days FROM v_learner_health_summary WHERE user_id = ?",
                    userId);
            freq.put("submits_30d", stats.get("submits_30d"));
            freq.put("active_days", stats.get("active_days"));
        } catch (Exception e) {
            freq.put("submits_30d", 0);
            freq.put("active_days", 0);
        }

        try {
            Integer streak = jdbcTemplate.queryForObject("""
                WITH dates AS (
                  SELECT DISTINCT DATE(create_time) AS d FROM submission WHERE user_id = ? ORDER BY d DESC
                ),
                numbered AS (
                  SELECT d, d - (ROW_NUMBER() OVER (ORDER BY d DESC) * INTERVAL '1 day') AS grp FROM dates
                )
                SELECT COUNT(*)::INTEGER FROM numbered WHERE grp = (SELECT grp FROM numbered ORDER BY d DESC LIMIT 1)
                """, Integer.class, userId);
            freq.put("streak_days", streak != null ? streak : 0);
        } catch (Exception e) {
            freq.put("streak_days", 0);
        }
        return freq;
    }

    private List<Map<String, Object>> aggregateDifficultyCurve(Long userId) {
        return jdbcTemplate.query("""
            SELECT DATE_TRUNC('week', create_time)::DATE AS week,
                   ROUND(AVG(CASE WHEN result = 0 THEN 0.0 ELSE 1.0 END)::NUMERIC, 2) AS avg_diff
            FROM submission
            WHERE user_id = ? AND create_time >= NOW() - INTERVAL '90 days'
            GROUP BY DATE_TRUNC('week', create_time)
            ORDER BY week ASC
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("week", rs.getString("week"));
            row.put("avg_diff", rs.getDouble("avg_diff"));
            return row;
        }, userId);
    }

    private List<Map<String, Object>> aggregateDueReviews(Long userId) {
        return jdbcTemplate.query("""
            SELECT rp.id AS package_id, rp.error_taxonomy, rp.fsrs_due_at, rp.fsrs_state
            FROM ai_error_review_package rp
            WHERE rp.user_id = ? AND rp.fsrs_due_at <= NOW() + INTERVAL '7 days'
              AND rp.fsrs_state IS NOT NULL
            ORDER BY rp.fsrs_due_at ASC
            LIMIT 5
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("package_id", rs.getString("package_id"));
            row.put("title", rs.getString("error_taxonomy"));
            Timestamp ts = rs.getTimestamp("fsrs_due_at");
            row.put("fsrs_due_at", ts != null ? ts.toInstant().toString() : null);
            row.put("fsrs_state", rs.getString("fsrs_state"));
            return row;
        }, userId);
    }
}
