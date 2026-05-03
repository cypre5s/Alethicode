package com.alethicode.service.twin.peer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S16: 学生互教飞轮 — 学生之间分享学习洞察。
 * 在同一班级内，学生可以把自己对某 KC 的理解写成短卡片分享给同学。
 */
@Service
public class PeerInsightService {

    private final JdbcTemplate jdbcTemplate;

    public PeerInsightService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listClassroomInsights(Long classroomId) {
        return jdbcTemplate.query("""
            SELECT tas.id, tas.target_kc_id, kc.name AS kc_name, tas.student_explanation,
                   tas.grader_score, tas.created_at, u.username
            FROM teach_ai_session tas
            JOIN language_pack_kc kc ON kc.id = tas.target_kc_id
            JOIN "user" u ON u.id = tas.user_id
            WHERE tas.grader_score >= 60 AND tas.completed_at IS NOT NULL
            ORDER BY tas.grader_score DESC, tas.created_at DESC
            LIMIT 20
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("session_id", rs.getLong("id"));
            row.put("kc_name", rs.getString("kc_name"));
            row.put("explanation_preview", truncate(rs.getString("student_explanation"), 100));
            row.put("grader_score", rs.getInt("grader_score"));
            row.put("author", rs.getString("username"));
            row.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
            return row;
        });
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
