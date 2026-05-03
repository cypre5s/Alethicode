package com.alethicode.service.twin.report;

import com.alethicode.service.twin.health.LearningHealthAggregator;
import com.alethicode.service.twin.museum.ErrorMuseumService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemesterReportService {

    private final JdbcTemplate jdbcTemplate;
    private final LearningHealthAggregator healthAggregator;
    private final ErrorMuseumService museumService;

    public SemesterReportService(JdbcTemplate jdbcTemplate,
                                  LearningHealthAggregator healthAggregator,
                                  ErrorMuseumService museumService) {
        this.jdbcTemplate = jdbcTemplate;
        this.healthAggregator = healthAggregator;
        this.museumService = museumService;
    }

    public Map<String, Object> generateReport(Long userId, String semesterLabel, String themeSkin) {
        Map<String, Object> health = healthAggregator.aggregate(userId);
        List<Map<String, Object>> museum = museumService.listPins(userId);

        String summaryText = buildSummaryText(health, museum.size());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("health", health);
        metrics.put("museum_count", museum.size());

        Long reportId = jdbcTemplate.queryForObject("""
            INSERT INTO semester_report (user_id, semester_label, theme_skin, summary_text, metrics)
            VALUES (?, ?, ?, ?, ?::JSONB)
            RETURNING id
            """, Long.class, userId, semesterLabel,
                themeSkin != null ? themeSkin : "default",
                summaryText, "{}");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report_id", reportId);
        result.put("semester_label", semesterLabel);
        result.put("summary_text", summaryText);
        result.put("theme_skin", themeSkin);
        return result;
    }

    @SuppressWarnings("unchecked")
    private String buildSummaryText(Map<String, Object> health, int museumCount) {
        Map<String, Object> freq = (Map<String, Object>) health.getOrDefault("frequency", Map.of());
        int submits = freq.get("submits_30d") instanceof Number ? ((Number) freq.get("submits_30d")).intValue() : 0;
        int activeDays = freq.get("active_days") instanceof Number ? ((Number) freq.get("active_days")).intValue() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("这个学期你累计提交了 ").append(submits).append(" 次代码，");
        sb.append("活跃了 ").append(activeDays).append(" 天。");
        if (museumCount > 0) {
            sb.append("你收藏了 ").append(museumCount).append(" 个印象深刻的错误，每一个都见证了你的成长。");
        }
        sb.append("继续保持这份学习的热情。");
        return sb.toString();
    }
}
