package com.alethicode.service.aitutor.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearnerMasteryServiceUnified {

    private static final Logger log = LoggerFactory.getLogger(LearnerMasteryServiceUnified.class);
    private static final double EMA_ALPHA = 0.7;

    private final JdbcTemplate jdbcTemplate;

    public LearnerMasteryServiceUnified(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateMastery(Long userId, Long languagePackId, Long kcId, boolean isCorrect) {
        jdbcTemplate.update("""
            INSERT INTO learner_kc_mastery (user_id, language_pack_id, kc_id, mastery, attempt_count, correct_count, error_count, last_attempt_at, updated_at)
            VALUES (?, ?, ?, ?, 1, ?, ?, now(), now())
            ON CONFLICT (user_id, language_pack_id, kc_id)
            DO UPDATE SET
              mastery = ? * (CASE WHEN ? THEN 1.0 ELSE 0.0 END) + (1 - ?) * learner_kc_mastery.mastery,
              attempt_count = learner_kc_mastery.attempt_count + 1,
              correct_count = learner_kc_mastery.correct_count + CASE WHEN ? THEN 1 ELSE 0 END,
              error_count = learner_kc_mastery.error_count + CASE WHEN ? THEN 0 ELSE 1 END,
              last_attempt_at = now(),
              updated_at = now()
            """,
            userId, languagePackId, kcId,
            isCorrect ? 1.0 : 0.0, isCorrect ? 1 : 0, isCorrect ? 0 : 1,
            EMA_ALPHA, isCorrect, EMA_ALPHA,
            isCorrect, isCorrect);

        refreshCourseProgress(userId, languagePackId);
    }

    public Map<String, Object> getCourseMastery(Long userId, Long languagePackId) {
        List<Map<String, Object>> kcMasteries = jdbcTemplate.queryForList("""
            SELECT km.kc_id, km.mastery, km.attempt_count, km.correct_count, km.error_count,
                   k.name AS kc_name, c.chapter_index, c.title AS chapter_title, c.id AS chapter_id
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE km.user_id = ? AND km.language_pack_id = ?
            ORDER BY c.chapter_index, k.id
            """, userId, languagePackId);

        Map<Long, List<Map<String, Object>>> byChapter = new LinkedHashMap<>();
        double totalMastery = 0;
        int count = 0;
        for (Map<String, Object> kc : kcMasteries) {
            Long chapterId = ((Number) kc.get("chapter_id")).longValue();
            byChapter.computeIfAbsent(chapterId, k -> new java.util.ArrayList<>()).add(kc);
            totalMastery += ((Number) kc.get("mastery")).doubleValue();
            count++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall_mastery", count > 0 ? totalMastery / count : 0.0);
        result.put("kc_count", count);
        result.put("kc_masteries", kcMasteries);
        result.put("chapters", byChapter);
        return result;
    }

    public List<Map<String, Object>> getWeakKcs(Long userId, Long languagePackId, double threshold) {
        return jdbcTemplate.queryForList("""
            SELECT km.kc_id, km.mastery, km.attempt_count, km.error_count,
                   k.name AS kc_name, c.chapter_index, c.title AS chapter_title
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE km.user_id = ? AND km.language_pack_id = ? AND km.mastery < ?
            ORDER BY km.mastery ASC
            """, userId, languagePackId, threshold);
    }

    private void refreshCourseProgress(Long userId, Long languagePackId) {
        jdbcTemplate.update("""
            INSERT INTO learner_course_progress (user_id, language_pack_id, overall_mastery, problems_attempted, problems_solved, last_activity_at, updated_at)
            SELECT ?, ?,
                   COALESCE(AVG(mastery), 0),
                   COALESCE(SUM(attempt_count), 0),
                   COALESCE(SUM(correct_count), 0),
                   now(), now()
            FROM learner_kc_mastery
            WHERE user_id = ? AND language_pack_id = ?
            ON CONFLICT (user_id, language_pack_id)
            DO UPDATE SET
              overall_mastery = EXCLUDED.overall_mastery,
              problems_attempted = EXCLUDED.problems_attempted,
              problems_solved = EXCLUDED.problems_solved,
              last_activity_at = now(),
              updated_at = now()
            """, userId, languagePackId, userId, languagePackId);
    }
}
