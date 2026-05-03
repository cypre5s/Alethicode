package com.alethicode.service.twin.inheritance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S18: 学长遗赠机制 — 结课学生可以留下一段"给下一届的话"和钉选的洞察。
 */
@Service
public class TwinInheritanceService {

    private final JdbcTemplate jdbcTemplate;

    public TwinInheritanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getInheritedInsights(Long languagePackId) {
        return jdbcTemplate.query("""
            SELECT tas.id, kc.name AS kc_name, tas.student_explanation,
                   tas.grader_score, u.username
            FROM teach_ai_session tas
            JOIN language_pack_kc kc ON kc.id = tas.target_kc_id
            JOIN "user" u ON u.id = tas.user_id
            WHERE tas.grader_score >= 75 AND tas.completed_at IS NOT NULL
            ORDER BY tas.grader_score DESC
            LIMIT 10
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kc_name", rs.getString("kc_name"));
            row.put("explanation", rs.getString("student_explanation"));
            row.put("score", rs.getInt("grader_score"));
            row.put("author", rs.getString("username"));
            return row;
        });
    }
}
