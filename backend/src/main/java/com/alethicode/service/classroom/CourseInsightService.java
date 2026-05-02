package com.alethicode.service.classroom;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseInsightService {

    private final JdbcTemplate jdbcTemplate;

    public CourseInsightService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getClassMasteryDistribution(String classroomId) {
        return jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, c.title AS chapter_title, c.chapter_index,
                   AVG(km.mastery) AS avg_mastery,
                   COUNT(km.user_id) AS student_count,
                   MIN(km.mastery) AS min_mastery,
                   MAX(km.mastery) AS max_mastery
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE km.user_id IN (
                SELECT user_id FROM classroom_member WHERE classroom_id = ?
            )
            GROUP BY k.id, k.name, c.title, c.chapter_index
            ORDER BY c.chapter_index, k.id
            """, classroomId);
    }

    public List<Map<String, Object>> getCommonWeakPoints(String classroomId) {
        return jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, c.title AS chapter_title,
                   AVG(km.mastery) AS avg_mastery,
                   COUNT(CASE WHEN km.mastery < 0.6 THEN 1 END) AS weak_student_count,
                   COUNT(km.user_id) AS total_students
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE km.user_id IN (
                SELECT user_id FROM classroom_member WHERE classroom_id = ?
            )
            GROUP BY k.id, k.name, c.title
            HAVING AVG(km.mastery) < 0.6
            ORDER BY AVG(km.mastery) ASC
            """, classroomId);
    }

    public List<Map<String, Object>> getStudentRiskList(String classroomId) {
        return jdbcTemplate.queryForList("""
            SELECT u.id AS user_id, u.username,
                   COALESCE(lcp.overall_mastery, 0) AS overall_mastery,
                   COALESCE(lcp.problems_attempted, 0) AS problems_attempted,
                   COALESCE(lcp.problems_solved, 0) AS problems_solved,
                   lcp.last_activity_at,
                   CASE
                     WHEN COALESCE(lcp.overall_mastery, 0) < 0.5 THEN 'high'
                     WHEN COALESCE(lcp.overall_mastery, 0) < 0.7 THEN 'medium'
                     ELSE 'low'
                   END AS risk_level
            FROM classroom_member cm
            JOIN "user" u ON u.id = cm.user_id
            LEFT JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
            LEFT JOIN learner_course_progress lcp
              ON lcp.user_id = cm.user_id AND lcp.language_pack_id = clp.language_pack_id
            WHERE cm.classroom_id = ?
            ORDER BY COALESCE(lcp.overall_mastery, 0) ASC
            """, classroomId);
    }

    public List<Map<String, Object>> getStudentKcMasteryMatrix(String classroomId, Long languagePackId) {
        return jdbcTemplate.queryForList("""
            SELECT u.username, k.name AS kc_name, km.mastery AS mastery_value,
                   k.id AS kc_id, c.chapter_index
            FROM learner_kc_mastery km
            JOIN "user" u ON u.id = km.user_id
            JOIN language_pack_kc k ON k.id = km.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            JOIN classroom_member cm ON cm.user_id = km.user_id
            WHERE cm.classroom_id = ? AND km.language_pack_id = ?
            ORDER BY u.username, c.chapter_index, k.id
            """, classroomId, languagePackId);
    }

    public List<Map<String, Object>> getErrorPatternRanking(String classroomId, int days) {
        return jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name,
                   COALESCE(ale.extra_data->>'detector_name', 'unknown') AS error_pattern,
                   COUNT(*) AS frequency
            FROM ai_learning_event ale
            JOIN classroom_member cm ON cm.user_id = ale.user_id
            JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
            JOIN ai_problem_kc_mapping apkm ON apkm.problem_id = ale.problem_id
            JOIN language_pack_kc k
              ON k.language_pack_id = clp.language_pack_id
             AND k.synced_ai_kc_id = apkm.kc_id
            WHERE ale.event_type IN ('misconception_detected_ast', 'frustration_detected')
              AND ale.created_at > now() - make_interval(days => ?)
              AND apkm.language_pack_id = clp.language_pack_id
              AND cm.classroom_id = ?
            GROUP BY k.name, COALESCE(ale.extra_data->>'detector_name', 'unknown')
            ORDER BY frequency DESC
            LIMIT 30
            """, days, classroomId);
    }

    public Map<String, Object> getInterventionEffect(int days) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            WITH interventions AS (
                SELECT awe.session_id, aws.user_id, aws.problem_id,
                       awe.created_at AS intervention_time
                FROM ai_workflow_event awe
                JOIN ai_workflow_session aws ON aws.session_id = awe.session_id
                WHERE awe.event_type = 'phase_output'
                  AND awe.event_data->>'phase' = 'ERROR_FEEDBACK'
                  AND awe.created_at > now() - make_interval(days => ?)
            )
            SELECT i.user_id, u.username,
                   COUNT(DISTINCT i.session_id) AS intervention_count,
                   COUNT(CASE WHEN s.result = 0 AND s.create_time > i.intervention_time THEN 1 END) AS ac_after,
                   COUNT(CASE WHEN s.result != 0 AND s.create_time < i.intervention_time THEN 1 END) AS fail_before,
                   COUNT(CASE WHEN s.result = 0 THEN 1 END) AS total_ac
            FROM interventions i
            JOIN "user" u ON u.id = i.user_id
            LEFT JOIN submission s ON s.user_id = i.user_id AND s.problem_id = i.problem_id
            GROUP BY i.user_id, u.username
            ORDER BY intervention_count DESC
            """, days);

        long totalInterventions = rows.stream()
                .mapToLong(r -> ((Number) r.getOrDefault("intervention_count", 0)).longValue())
                .sum();
        long totalAcAfter = rows.stream()
                .mapToLong(r -> ((Number) r.getOrDefault("ac_after", 0)).longValue())
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_interventions", totalInterventions);
        result.put("total_ac_after_intervention", totalAcAfter);
        result.put("effectiveness_rate", totalInterventions > 0
                ? Math.round((double) totalAcAfter / totalInterventions * 1000.0) / 1000.0 : 0);
        result.put("per_student", rows);
        return result;
    }

    public Map<String, Object> getContentEffectiveness(Long languagePackId) {
        List<Map<String, Object>> problemEffectiveness = jdbcTemplate.queryForList("""
            SELECT p.id AS problem_id, p.title,
                   COUNT(s.id) AS submission_count,
                   COUNT(CASE WHEN s.result = 0 THEN 1 END) AS ac_count,
                   CASE WHEN COUNT(s.id) > 0
                     THEN ROUND(COUNT(CASE WHEN s.result = 0 THEN 1 END)::numeric / COUNT(s.id), 3)
                     ELSE 0 END AS ac_rate
            FROM problem p
            JOIN language_pack_problem_mapping pm ON pm.problem_id = p.id
            LEFT JOIN submission s ON s.problem_id = p.id
            WHERE pm.language_pack_id = ?
            GROUP BY p.id, p.title
            ORDER BY ac_rate ASC
            """, languagePackId);

        List<Map<String, Object>> chapterEffectiveness = jdbcTemplate.queryForList("""
            SELECT c.title AS chapter_title, c.chapter_index,
                   AVG(km.mastery) AS avg_mastery,
                   COUNT(DISTINCT km.user_id) AS learner_count
            FROM language_pack_chapter c
            JOIN language_pack_kc k ON k.chapter_id = c.id
            LEFT JOIN learner_kc_mastery km ON km.kc_id = k.id
            WHERE c.language_pack_id = ?
            GROUP BY c.id, c.title, c.chapter_index
            ORDER BY c.chapter_index
            """, languagePackId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("language_pack_id", languagePackId);
        result.put("problem_effectiveness", problemEffectiveness);
        result.put("chapter_effectiveness", chapterEffectiveness);
        return result;
    }
}
