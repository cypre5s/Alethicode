package com.alethicode.service.monitor;

import com.alethicode.service.ai.AiModelGateway;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StudentRiskDetectionService {

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;

    public StudentRiskDetectionService(JdbcTemplate jdbcTemplate, AiModelGateway aiModelGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
    }

    public List<Map<String, Object>> getEnhancedRiskList(String classroomId, Authentication authentication) {
        requireStaff(classroomId, authentication);

        return jdbcTemplate.queryForList("""
            WITH student_base AS (
                SELECT cm.user_id, u.username,
                       COALESCE(lcp.overall_mastery, 0)    AS overall_mastery,
                       COALESCE(lcp.problems_attempted, 0) AS problems_attempted,
                       COALESCE(lcp.problems_solved, 0)    AS problems_solved,
                       lcp.last_activity_at
                FROM classroom_member cm
                JOIN "user" u ON u.id = cm.user_id
                LEFT JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
                LEFT JOIN learner_course_progress lcp
                  ON lcp.user_id = cm.user_id AND lcp.language_pack_id = clp.language_pack_id
                WHERE cm.classroom_id = ? AND cm.role = 'student'
            ),
            recent_activity AS (
                SELECT s.user_id,
                       COUNT(*)                                  AS recent_submissions,
                       COUNT(CASE WHEN s.result = 0 THEN 1 END) AS recent_ac,
                       MAX(s.create_time)                        AS last_submission_time
                FROM submission s
                WHERE s.user_id IN (SELECT user_id FROM student_base)
                  AND s.create_time >= now() - interval '7 days'
                GROUP BY s.user_id
            ),
            error_streak AS (
                SELECT sub.user_id, COUNT(*) AS streak
                FROM (
                    SELECT s2.user_id, s2.result,
                           ROW_NUMBER() OVER (PARTITION BY s2.user_id ORDER BY s2.create_time DESC) AS rn
                    FROM submission s2
                    WHERE s2.user_id IN (SELECT user_id FROM student_base)
                ) sub
                WHERE sub.rn <= 10 AND sub.result != 0
                GROUP BY sub.user_id
            )
            SELECT sb.user_id, sb.username, sb.overall_mastery,
                   sb.problems_attempted, sb.problems_solved, sb.last_activity_at,
                   COALESCE(ra.recent_submissions, 0) AS recent_submissions,
                   COALESCE(ra.recent_ac, 0)          AS recent_ac,
                   ra.last_submission_time,
                   COALESCE(es.streak, 0)             AS error_streak,
                   CASE
                     WHEN sb.overall_mastery < 0.3 OR COALESCE(es.streak, 0) >= 8
                       THEN 'critical'
                     WHEN sb.overall_mastery < 0.5
                       OR (ra.last_submission_time IS NULL AND sb.last_activity_at < now() - interval '3 days')
                       OR COALESCE(es.streak, 0) >= 5
                       THEN 'high'
                     WHEN sb.overall_mastery < 0.7
                       OR COALESCE(ra.recent_submissions, 0) < 3
                       THEN 'medium'
                     ELSE 'low'
                   END AS risk_level,
                   CASE
                     WHEN COALESCE(es.streak, 0) >= 5          THEN '连续错误过多，可能受挫'
                     WHEN ra.last_submission_time IS NULL
                      AND sb.last_activity_at < now() - interval '3 days'
                                                                THEN '长时间未活跃'
                     WHEN sb.overall_mastery < 0.5              THEN '整体掌握度偏低'
                     WHEN COALESCE(ra.recent_submissions, 0) < 3 THEN '近期练习量不足'
                     ELSE ''
                   END AS risk_reason
            FROM student_base sb
            LEFT JOIN recent_activity ra ON ra.user_id = sb.user_id
            LEFT JOIN error_streak es    ON es.user_id = sb.user_id
            ORDER BY
              CASE
                WHEN sb.overall_mastery < 0.3 OR COALESCE(es.streak, 0) >= 8 THEN 0
                WHEN sb.overall_mastery < 0.5 THEN 1
                WHEN sb.overall_mastery < 0.7 THEN 2
                ELSE 3
              END,
              sb.overall_mastery ASC
            """, classroomId);
    }

    public Map<String, Object> generateInterventionAdvice(String classroomId, Long studentUserId, Authentication authentication) {
        requireStaff(classroomId, authentication);

        Map<String, Object> studentData = jdbcTemplate.queryForMap("""
            SELECT u.username,
                   COALESCE(lcp.overall_mastery, 0) AS overall_mastery,
                   COALESCE(lcp.problems_attempted, 0) AS problems_attempted,
                   COALESCE(lcp.problems_solved, 0) AS problems_solved
            FROM "user" u
            LEFT JOIN classroom_language_pack clp ON clp.classroom_id = ?
            LEFT JOIN learner_course_progress lcp ON lcp.user_id = u.id AND lcp.language_pack_id = clp.language_pack_id
            WHERE u.id = ?
            """, classroomId, studentUserId);

        List<Map<String, Object>> weakKcs = jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, COALESCE(km.mastery, 0) AS mastery
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            WHERE km.user_id = ?
            ORDER BY km.mastery ASC
            LIMIT 3
            """, studentUserId);

        int errorStreak = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM (
                SELECT result, ROW_NUMBER() OVER (ORDER BY create_time DESC) AS rn
                FROM submission WHERE user_id = ?
            ) sub WHERE rn <= 10 AND result != 0
            """, Integer.class, studentUserId);

        StringBuilder context = new StringBuilder();
        context.append("学生：").append(studentData.get("username")).append("\n");
        context.append("整体掌握度：").append(Math.round(((Number) studentData.get("overall_mastery")).doubleValue() * 100)).append("%\n");
        context.append("已做题/已通过：").append(studentData.get("problems_attempted")).append("/").append(studentData.get("problems_solved")).append("\n");
        context.append("近期连续错误：").append(errorStreak).append(" 次\n");
        if (!weakKcs.isEmpty()) {
            context.append("最薄弱知识点：\n");
            for (Map<String, Object> kc : weakKcs) {
                context.append("  - ").append(kc.get("kc_name"))
                        .append("（掌握度 ").append(Math.round(((Number) kc.get("mastery")).doubleValue() * 100)).append("%）\n");
            }
        }

        String systemPrompt = """
                你是一位面向非计算机专业Python初学者的教学顾问。根据学生的学习数据，给出 2-3 条具体的教师干预建议。
                要求：
                - 每条建议具体可执行（如"单独辅导XX知识点"、"推送XX类型的基础练习"）
                - 语气专业、温和
                - 只返回 JSON: {"advice": ["建议1", "建议2", "建议3"]}
                """;
        Map<String, Object> llmResult = aiModelGateway.callForJson(systemPrompt, context.toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("student", studentData);
        result.put("weak_kcs", weakKcs);
        result.put("error_streak", errorStreak);
        result.put("advice", llmResult.get("advice"));
        return result;
    }

    private void requireStaff(String classroomId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long userId = resolveUserId(authentication);
        Integer staffCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM classroom_member WHERE classroom_id = ? AND user_id = ? AND role IN ('owner','ta')",
                Integer.class, classroomId, userId);
        if (staffCount == null || staffCount == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可访问");
        }
    }

    private Long resolveUserId(Authentication authentication) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM \"user\" WHERE lower(username) = ?",
                    Long.class, authentication.getName().toLowerCase(Locale.ROOT));
        } catch (EmptyResultDataAccessException e) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "用户不存在");
        }
    }
}
