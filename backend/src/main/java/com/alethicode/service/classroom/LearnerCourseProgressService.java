package com.alethicode.service.classroom;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LearnerCourseProgressService {

    private final JdbcTemplate jdbcTemplate;

    public LearnerCourseProgressService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getOrCreateProgress(Long userId, Long languagePackId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT * FROM learner_course_progress
            WHERE user_id = ? AND language_pack_id = ?
            """, userId, languagePackId);

        if (!rows.isEmpty()) {
            return rows.getFirst();
        }

        jdbcTemplate.update("""
            INSERT INTO learner_course_progress (user_id, language_pack_id)
            VALUES (?, ?)
            ON CONFLICT (user_id, language_pack_id) DO NOTHING
            """, userId, languagePackId);

        return jdbcTemplate.queryForMap("""
            SELECT * FROM learner_course_progress
            WHERE user_id = ? AND language_pack_id = ?
            """, userId, languagePackId);
    }

    public void refreshProgress(Long userId, Long languagePackId) {
        jdbcTemplate.update("""
            UPDATE learner_course_progress SET
              overall_mastery = COALESCE((SELECT AVG(mastery) FROM learner_kc_mastery WHERE user_id = ? AND language_pack_id = ?), 0),
              problems_attempted = COALESCE((SELECT SUM(attempt_count) FROM learner_kc_mastery WHERE user_id = ? AND language_pack_id = ?), 0),
              problems_solved = COALESCE((SELECT SUM(correct_count) FROM learner_kc_mastery WHERE user_id = ? AND language_pack_id = ?), 0),
              last_activity_at = now(),
              updated_at = now()
            WHERE user_id = ? AND language_pack_id = ?
            """, userId, languagePackId, userId, languagePackId, userId, languagePackId, userId, languagePackId);
    }
}
