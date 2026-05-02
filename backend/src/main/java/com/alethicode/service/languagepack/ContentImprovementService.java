package com.alethicode.service.languagepack;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContentImprovementService {

    private final JdbcTemplate jdbcTemplate;

    public ContentImprovementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getHighFrequencyErrors(Long languagePackId) {
        return jdbcTemplate.queryForList("""
            SELECT ale.error_taxonomy, COUNT(*) AS frequency,
                   COUNT(DISTINCT ale.user_id) AS affected_students,
                   k.name AS kc_name, c.title AS chapter_title
            FROM ai_learning_event ale
            JOIN language_pack_problem_mapping pm ON pm.problem_id = ale.problem_id
            JOIN language_pack_kc k ON k.id = pm.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE pm.language_pack_id = ? AND ale.error_taxonomy IS NOT NULL
            GROUP BY ale.error_taxonomy, k.id, k.name, c.title
            ORDER BY frequency DESC
            LIMIT 20
            """, languagePackId);
    }

    public List<Map<String, Object>> getLowEfficiencyContent(Long languagePackId) {
        return jdbcTemplate.queryForList("""
            SELECT p.id AS problem_id, p.title,
                   COUNT(s.id) AS submission_count,
                   CASE WHEN COUNT(s.id) > 0
                     THEN ROUND(COUNT(CASE WHEN s.result = 0 THEN 1 END)::numeric / COUNT(s.id), 3)
                     ELSE 0 END AS ac_rate,
                   k.name AS kc_name, AVG(km.mastery) AS avg_kc_mastery
            FROM problem p
            JOIN language_pack_problem_mapping pm ON pm.problem_id = p.id
            JOIN language_pack_kc k ON k.id = pm.kc_id
            LEFT JOIN submission s ON s.problem_id = p.id
            LEFT JOIN learner_kc_mastery km ON km.kc_id = k.id
            WHERE pm.language_pack_id = ?
            GROUP BY p.id, p.title, k.id, k.name
            HAVING COUNT(s.id) >= 5
              AND ROUND(COUNT(CASE WHEN s.result = 0 THEN 1 END)::numeric / COUNT(s.id), 3) < 0.3
            ORDER BY ac_rate ASC
            """, languagePackId);
    }

    public Map<String, Object> getImprovementSuggestions(Long languagePackId) {
        List<Map<String, Object>> highFreqErrors = getHighFrequencyErrors(languagePackId);
        List<Map<String, Object>> lowEfficiency = getLowEfficiencyContent(languagePackId);

        Map<String, Object> suggestions = new LinkedHashMap<>();
        suggestions.put("language_pack_id", languagePackId);
        suggestions.put("high_frequency_errors", highFreqErrors);
        suggestions.put("low_efficiency_content", lowEfficiency);
        suggestions.put("suggestion_count", highFreqErrors.size() + lowEfficiency.size());
        return suggestions;
    }
}
