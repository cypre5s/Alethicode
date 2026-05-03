package com.alethicode.service.twin.weekly;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TwinWeeklyDigestService {

    private final JdbcTemplate jdbcTemplate;

    public TwinWeeklyDigestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getOrGenerateWeeklyDigest(Long userId) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return getDigestForWeek(userId, weekStart);
    }

    public Map<String, Object> getDigestForWeek(Long userId, LocalDate weekStart) {
        Map<String, Object> metrics = computeWeeklyMetrics(userId, weekStart);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("week_start", weekStart.toString());
        result.put("submits", metrics.get("submits"));
        result.put("acs", metrics.get("acs"));
        result.put("new_kcs", metrics.get("new_kcs"));
        result.put("active_days", metrics.get("active_days"));
        result.put("frustration_moments", metrics.get("frustration_moments"));

        String digestText = generateDigestText(metrics);
        result.put("digest_text", digestText);

        return result;
    }

    public void submitReflection(Long userId, String reflectionText) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, Object> metrics = computeWeeklyMetrics(userId, weekStart);

        jdbcTemplate.update("""
            INSERT INTO twin_weekly_digest (user_id, week_start, digest_text, metrics, evidence_event_ids)
            VALUES (?, ?, ?, ?::JSONB, '[]'::JSONB)
            ON CONFLICT (user_id, week_start)
            DO UPDATE SET digest_text = EXCLUDED.digest_text, metrics = EXCLUDED.metrics
            """, userId, weekStart, reflectionText,
                "{\"submits\":" + metrics.get("submits") + ",\"acs\":" + metrics.get("acs") + "}");
    }

    Map<String, Object> computeWeeklyMetrics(Long userId, LocalDate weekStart) {
        Instant from = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = weekStart.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<String, Object> metrics = new LinkedHashMap<>();
        try {
            Map<String, Object> submissionStats = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS submits,
                       COUNT(*) FILTER (WHERE result = 0) AS acs,
                       COUNT(DISTINCT DATE(create_time)) AS active_days
                FROM submission
                WHERE user_id = ? AND create_time >= ? AND create_time < ?
                """, userId, Timestamp.from(from), Timestamp.from(to));
            metrics.put("submits", ((Number) submissionStats.get("submits")).intValue());
            metrics.put("acs", ((Number) submissionStats.get("acs")).intValue());
            metrics.put("active_days", ((Number) submissionStats.get("active_days")).intValue());
        } catch (Exception e) {
            metrics.put("submits", 0);
            metrics.put("acs", 0);
            metrics.put("active_days", 0);
        }

        try {
            Integer newKcs = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT kc_id)::INTEGER FROM learner_kc_mastery
                WHERE user_id = ? AND updated_at >= ? AND updated_at < ? AND attempt_count = 1
                """, Integer.class, userId, Timestamp.from(from), Timestamp.from(to));
            metrics.put("new_kcs", newKcs != null ? newKcs : 0);
        } catch (Exception e) {
            metrics.put("new_kcs", 0);
        }

        try {
            Integer frustrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)::INTEGER FROM ai_learning_event
                WHERE user_id = ? AND event_type = 'frustration_detected'
                  AND created_at >= ? AND created_at < ?
                """, Integer.class, userId, Timestamp.from(from), Timestamp.from(to));
            metrics.put("frustration_moments", frustrations != null ? frustrations : 0);
        } catch (Exception e) {
            metrics.put("frustration_moments", 0);
        }

        return metrics;
    }

    String generateDigestText(Map<String, Object> metrics) {
        int submits = (int) metrics.getOrDefault("submits", 0);
        int acs = (int) metrics.getOrDefault("acs", 0);
        int newKcs = (int) metrics.getOrDefault("new_kcs", 0);

        if (submits == 0) {
            return "这周比较安静，没有看到你的提交。没关系，下周继续加油。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("这周你提交了 ").append(submits).append(" 次代码");
        if (acs > 0) {
            sb.append("，其中 ").append(acs).append(" 次通过了");
        }
        sb.append("。");
        if (newKcs > 0) {
            sb.append("接触了 ").append(newKcs).append(" 个新知识点，学习面在扩展。");
        }
        if (acs > 0 && (double) acs / submits >= 0.7) {
            sb.append("正确率不错，保持节奏。");
        } else if (submits > 5 && (double) acs / submits < 0.3) {
            sb.append("遇到了不少挑战，但每次出错都是在学习。");
        }
        return sb.toString();
    }
}
