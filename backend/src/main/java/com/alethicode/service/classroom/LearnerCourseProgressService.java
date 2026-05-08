package com.alethicode.service.classroom;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearnerCourseProgressService {

    private static final double EMA_ALPHA = 0.7;

    private final JdbcTemplate jdbcTemplate;

    public LearnerCourseProgressService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getOrCreateProgress(Long userId, Long languagePackId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT * FROM learner_course_progress
            WHERE user_id = ? AND language_pack_id = ?
            """, userId, languagePackId);
        Map<String, Object> existingProgress = rows.isEmpty() ? null : rows.getFirst();
        if (existingProgress == null) {
            ensureProgressRowExists(userId, languagePackId);
        }

        Integer masteryCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM learner_kc_mastery
            WHERE user_id = ? AND language_pack_id = ?
            """, Integer.class, userId, languagePackId);
        if (masteryCount != null && masteryCount > 0) {
            if (existingProgress == null || isUninitializedProgress(existingProgress)) {
                refreshProgress(userId, languagePackId);
            }
        } else {
            bootstrapMasteryFromSubmissionHistoryIfNeeded(userId, languagePackId);
        }
        return jdbcTemplate.queryForMap("""
            SELECT * FROM learner_course_progress
            WHERE user_id = ? AND language_pack_id = ?
            """, userId, languagePackId);
    }

    public void refreshProgress(Long userId, Long languagePackId) {
        jdbcTemplate.update("""
            INSERT INTO learner_course_progress (
              user_id, language_pack_id, overall_mastery, problems_attempted, problems_solved, last_activity_at, updated_at
            )
            SELECT ?, ?,
                   COALESCE(AVG(mastery), 0),
                   COALESCE(SUM(attempt_count), 0),
                   COALESCE(SUM(correct_count), 0),
                   MAX(last_attempt_at),
                   now()
            FROM learner_kc_mastery
            WHERE user_id = ? AND language_pack_id = ?
            ON CONFLICT (user_id, language_pack_id)
            DO UPDATE SET
              overall_mastery = EXCLUDED.overall_mastery,
              problems_attempted = EXCLUDED.problems_attempted,
              problems_solved = EXCLUDED.problems_solved,
              last_activity_at = EXCLUDED.last_activity_at,
              updated_at = now()
            """, userId, languagePackId, userId, languagePackId);
    }

    private void ensureProgressRowExists(Long userId, Long languagePackId) {
        jdbcTemplate.update("""
            INSERT INTO learner_course_progress (user_id, language_pack_id)
            VALUES (?, ?)
            ON CONFLICT (user_id, language_pack_id) DO NOTHING
            """, userId, languagePackId);
    }

    private void bootstrapMasteryFromSubmissionHistoryIfNeeded(Long userId, Long languagePackId) {
        List<Map<String, Object>> submissionRows = jdbcTemplate.queryForList("""
            SELECT kc.value::bigint AS kc_id, s.result, s.create_time
            FROM submission s
            JOIN problem p ON p.id = s.problem_id
            CROSS JOIN LATERAL jsonb_array_elements(
                p.statistic_info->'language_pack_teaching'->'related_kc_ids'
            ) AS kc(value)
            WHERE s.user_id = ?
              AND EXISTS (
                SELECT 1
                FROM language_pack_problem_mapping lpm
                WHERE lpm.problem_id = s.problem_id
                  AND lpm.language_pack_id = ?
              )
              AND p.statistic_info->'language_pack_teaching' IS NOT NULL
            ORDER BY s.create_time ASC, s.id ASC, kc.value::bigint ASC
            """, userId, languagePackId);

        if (submissionRows.isEmpty()) {
            return;
        }

        Map<Long, MasteryAccumulator> masteryByKc = new LinkedHashMap<>();
        for (Map<String, Object> row : submissionRows) {
            Long kcId = ((Number) row.get("kc_id")).longValue();
            boolean isCorrect = ((Number) row.get("result")).intValue() == 0;
            Instant attemptAt = asInstant(row.get("create_time"));
            masteryByKc.computeIfAbsent(kcId, unused -> new MasteryAccumulator())
                    .record(isCorrect, attemptAt);
        }

        masteryByKc.forEach((kcId, mastery) -> jdbcTemplate.update("""
            INSERT INTO learner_kc_mastery (
              user_id, language_pack_id, kc_id, mastery, attempt_count, correct_count, error_count, last_attempt_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (user_id, language_pack_id, kc_id)
            DO UPDATE SET
              mastery = EXCLUDED.mastery,
              attempt_count = EXCLUDED.attempt_count,
              correct_count = EXCLUDED.correct_count,
              error_count = EXCLUDED.error_count,
              last_attempt_at = EXCLUDED.last_attempt_at,
              updated_at = now()
            """,
            userId, languagePackId, kcId, mastery.mastery, mastery.attemptCount,
            mastery.correctCount, mastery.errorCount, Timestamp.from(mastery.lastAttemptAt)));

        refreshProgress(userId, languagePackId);
    }

    private boolean isUninitializedProgress(Map<String, Object> progressRow) {
        return asDouble(progressRow.get("overall_mastery")) == 0.0
                && asInt(progressRow.get("problems_attempted")) == 0
                && asInt(progressRow.get("problems_solved")) == 0
                && progressRow.get("last_activity_at") == null;
    }

    private Instant asInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return Instant.parse(String.valueOf(value));
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static final class MasteryAccumulator {
        private double mastery;
        private int attemptCount;
        private int correctCount;
        private int errorCount;
        private Instant lastAttemptAt;

        private void record(boolean isCorrect, Instant attemptAt) {
            mastery = attemptCount == 0
                    ? (isCorrect ? 1.0 : 0.0)
                    : EMA_ALPHA * (isCorrect ? 1.0 : 0.0) + (1 - EMA_ALPHA) * mastery;
            attemptCount++;
            if (isCorrect) {
                correctCount++;
            } else {
                errorCount++;
            }
            lastAttemptAt = attemptAt;
        }
    }
}
