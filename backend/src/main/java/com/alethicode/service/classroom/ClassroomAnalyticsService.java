package com.alethicode.service.classroom;

import com.alethicode.service.ai.AiModelGateway;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ClassroomAnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;

    public ClassroomAnalyticsService(JdbcTemplate jdbcTemplate, AiModelGateway aiModelGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
    }

    public Map<String, Object> getWeeklyPulse(String classroomId, String range, Authentication authentication) {
        requireStaff(classroomId, authentication);

        int days = "month".equals(range) ? 29 : 6;

        List<Map<String, Object>> dailyTrend = jdbcTemplate.queryForList("""
            SELECT d.day,
                   COALESCE(sub.submission_count, 0) AS submission_count,
                   COALESCE(sub.ac_count, 0)         AS ac_count,
                   COALESCE(sub.active_students, 0)  AS active_students
            FROM generate_series(
                     current_date - make_interval(days => ?), current_date, '1 day'
                 ) AS d(day)
            LEFT JOIN LATERAL (
                SELECT COUNT(*)                                  AS submission_count,
                       COUNT(CASE WHEN s.result = 0 THEN 1 END) AS ac_count,
                       COUNT(DISTINCT s.user_id)                 AS active_students
                FROM submission s
                WHERE s.user_id IN (
                    SELECT user_id FROM classroom_member WHERE classroom_id = ?
                )
                AND s.problem_id IN (
                    SELECT problem_id FROM classroom_problem WHERE classroom_id = ?
                    UNION SELECT pm.problem_id FROM language_pack_problem_mapping pm
                          JOIN classroom_language_pack clp ON clp.language_pack_id = pm.language_pack_id
                          WHERE clp.classroom_id = ?
                )
                AND s.create_time >= d.day
                AND s.create_time < d.day + interval '1 day'
            ) sub ON true
            ORDER BY d.day
            """, days, classroomId, classroomId, classroomId);

        List<Map<String, Object>> kcActivity = jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, COUNT(s.id) AS submission_count,
                   COUNT(CASE WHEN s.result = 0 THEN 1 END) AS ac_count
            FROM submission s
            JOIN language_pack_problem_mapping lpm ON lpm.problem_id = s.problem_id
            JOIN language_pack_problem_generation_log g ON g.id = lpm.generation_log_id
            JOIN language_pack_kc k ON k.id = g.kc_id
            WHERE s.user_id IN (
                SELECT user_id FROM classroom_member WHERE classroom_id = ?
            )
            AND s.create_time >= current_date - make_interval(days => ?)
            GROUP BY k.id, k.name
            ORDER BY submission_count DESC
            LIMIT 10
            """, classroomId, days);

        return Map.of("daily_trend", dailyTrend, "top_active_kcs", kcActivity);
    }

    public Map<String, Object> getKcMasteryHeatmap(String classroomId, Authentication authentication) {
        requireStaff(classroomId, authentication);

        List<Map<String, Object>> matrix = jdbcTemplate.queryForList("""
            SELECT u.id AS user_id, u.username,
                   k.id AS kc_id, k.name AS kc_name,
                   c.chapter_index,
                   COALESCE(km.mastery, 0) AS mastery
            FROM classroom_member cm
            JOIN "user" u ON u.id = cm.user_id
            CROSS JOIN (
                SELECT DISTINCT k2.id, k2.name, k2.chapter_id
                FROM language_pack_kc k2
                JOIN classroom_language_pack clp ON clp.language_pack_id = k2.language_pack_id
                WHERE clp.classroom_id = ?
            ) k
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            LEFT JOIN learner_kc_mastery km ON km.user_id = u.id AND km.kc_id = k.id
            WHERE cm.classroom_id = ? AND cm.role = 'student'
            ORDER BY c.chapter_index, k.id, u.username
            """, classroomId, classroomId);

        List<String> students = new ArrayList<>();
        List<String> kcs = new ArrayList<>();
        List<List<Object>> data = new ArrayList<>();
        Map<String, Integer> studentIdx = new LinkedHashMap<>();
        Map<Long, Integer> kcIdx = new LinkedHashMap<>();

        for (Map<String, Object> row : matrix) {
            String username = (String) row.get("username");
            Long kcId = ((Number) row.get("kc_id")).longValue();
            String kcName = (String) row.get("kc_name");

            studentIdx.computeIfAbsent(username, k -> { students.add(k); return students.size() - 1; });
            kcIdx.computeIfAbsent(kcId, k -> { kcs.add(kcName); return kcs.size() - 1; });

            double mastery = ((Number) row.get("mastery")).doubleValue();
            data.add(List.of(kcIdx.get(kcId), studentIdx.get(username), Math.round(mastery * 100.0) / 100.0));
        }

        return Map.of("students", students, "kcs", kcs, "data", data);
    }

    public Map<String, Object> getWeakKcSuggestions(String classroomId, Authentication authentication) {
        requireStaff(classroomId, authentication);

        List<Map<String, Object>> weakKcs = jdbcTemplate.queryForList("""
            SELECT k.id AS kc_id, k.name AS kc_name, c.title AS chapter_title,
                   AVG(km.mastery) AS avg_mastery,
                   COUNT(CASE WHEN km.mastery < 0.6 THEN 1 END) AS weak_count,
                   COUNT(km.user_id) AS total_students
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE km.user_id IN (
                SELECT user_id FROM classroom_member WHERE classroom_id = ?
            )
            GROUP BY k.id, k.name, c.title
            ORDER BY AVG(km.mastery) ASC
            LIMIT 3
            """, classroomId);

        if (weakKcs.isEmpty()) {
            return Map.of("weak_kcs", List.of(), "suggestions", List.of());
        }

        StringBuilder kcSummary = new StringBuilder();
        for (Map<String, Object> kc : weakKcs) {
            kcSummary.append(String.format("- %s（章节：%s，平均掌握度 %.0f%%，薄弱人数 %s/%s）\n",
                    kc.get("kc_name"), kc.get("chapter_title"),
                    ((Number) kc.get("avg_mastery")).doubleValue() * 100,
                    kc.get("weak_count"), kc.get("total_students")));
        }

        String systemPrompt = "你是一位面向非计算机专业Python初学者的教学顾问。根据班级薄弱知识点数据给出简洁的教学改进建议（3-5条，每条1-2句话）。";
        String userPrompt = "以下是班级最薄弱的知识点：\n" + kcSummary
                + "\n请给出针对性的教学建议，格式为JSON：{\"suggestions\": [\"建议1\", \"建议2\", ...]}";
        Map<String, Object> llmResult = aiModelGateway.callForJson(systemPrompt, userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weak_kcs", weakKcs);
        result.put("suggestions", llmResult.get("suggestions"));
        return result;
    }

    public Map<String, Object> getCoursewareUsage(String classroomId, Authentication authentication) {
        requireStaff(classroomId, authentication);

        List<Map<String, Object>> qaFrequency = jdbcTemplate.queryForList("""
            SELECT p.document_id,
                   d.original_filename AS document_title,
                   p.page_no,
                   COUNT(*) AS query_count
            FROM language_pack_chat_retrieval_log lr
            JOIN language_pack_chat_session cs ON cs.id = lr.session_id
            JOIN classroom_language_pack clp ON clp.language_pack_id = cs.language_pack_id
            CROSS JOIN LATERAL jsonb_array_elements(lr.page_hit_json) AS elem
            JOIN language_pack_page p
                 ON p.id = CASE
                     WHEN jsonb_typeof(elem) = 'number' THEN elem::text::bigint
                     ELSE (elem ->> 'page_id')::bigint
                 END
            JOIN language_pack_document d ON d.id = p.document_id
            WHERE clp.classroom_id = ?
              AND lr.create_time >= now() - interval '30 days'
              AND lr.page_hit_json IS NOT NULL
              AND lr.page_hit_json != '[]'::jsonb
            GROUP BY p.document_id, d.original_filename, p.page_no
            ORDER BY query_count DESC
            LIMIT 20
            """, classroomId);

        String qaLanguagePackId = null;
        try {
            qaLanguagePackId = jdbcTemplate.queryForObject(
                    "SELECT language_pack_id::text FROM classroom_language_pack WHERE classroom_id = ? LIMIT 1",
                    String.class, classroomId);
        } catch (Exception ignored) {}

        List<Map<String, Object>> submissionByChapter = jdbcTemplate.queryForList("""
            SELECT c.title AS chapter_title, c.chapter_index,
                   COUNT(s.id) AS submission_count,
                   COUNT(CASE WHEN s.result = 0 THEN 1 END) AS ac_count,
                   COUNT(DISTINCT s.user_id) AS active_students
            FROM submission s
            JOIN language_pack_problem_mapping lpm ON lpm.problem_id = s.problem_id
            JOIN language_pack_problem_generation_log g ON g.id = lpm.generation_log_id
            JOIN language_pack_kc k ON k.id = g.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            JOIN classroom_language_pack clp ON clp.language_pack_id = lpm.language_pack_id
            WHERE clp.classroom_id = ?
              AND s.user_id IN (SELECT user_id FROM classroom_member WHERE classroom_id = ?)
              AND s.create_time >= now() - interval '30 days'
            GROUP BY c.id, c.title, c.chapter_index
            ORDER BY c.chapter_index
            """, classroomId, classroomId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("qa_frequency", qaFrequency);
        result.put("submission_by_chapter", submissionByChapter);
        if (qaLanguagePackId != null) {
            result.put("language_pack_id", qaLanguagePackId);
        }
        return result;
    }

    public Map<String, Object> generateWeeklyReport(String classroomId, Authentication authentication) {
        requireStaff(classroomId, authentication);

        int totalStudents = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM classroom_member WHERE classroom_id = ? AND role = 'student'
            """, Integer.class, classroomId);

        Map<String, Object> thisWeek = jdbcTemplate.queryForMap("""
            SELECT COUNT(DISTINCT s.user_id) AS active_students,
                   COUNT(s.id) AS total_submissions,
                   COUNT(CASE WHEN s.result = 0 THEN 1 END) AS ac_count
            FROM submission s
            WHERE s.user_id IN (SELECT user_id FROM classroom_member WHERE classroom_id = ? AND role = 'student')
              AND s.problem_id IN (SELECT problem_id FROM classroom_problem WHERE classroom_id = ?
                                   UNION SELECT pm.problem_id FROM language_pack_problem_mapping pm
                                         JOIN classroom_language_pack clp ON clp.language_pack_id = pm.language_pack_id
                                         WHERE clp.classroom_id = ?)
              AND s.create_time >= now() - interval '7 days'
            """, classroomId, classroomId, classroomId);

        List<Map<String, Object>> weakKcs = jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, AVG(km.mastery) AS avg_mastery,
                   COUNT(CASE WHEN km.mastery < 0.6 THEN 1 END) AS weak_count,
                   COUNT(*) AS total
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            WHERE km.user_id IN (SELECT user_id FROM classroom_member WHERE classroom_id = ? AND role = 'student')
            GROUP BY k.id, k.name
            ORDER BY AVG(km.mastery) ASC
            LIMIT 3
            """, classroomId);

        int riskCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM (
                SELECT cm.user_id, COALESCE(lcp.overall_mastery, 0) AS m
                FROM classroom_member cm
                LEFT JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
                LEFT JOIN learner_course_progress lcp ON lcp.user_id = cm.user_id AND lcp.language_pack_id = clp.language_pack_id
                WHERE cm.classroom_id = ? AND cm.role = 'student' AND COALESCE(lcp.overall_mastery, 0) < 0.5
            ) sub
            """, Integer.class, classroomId);

        Double avgMasteryNow = jdbcTemplate.queryForObject("""
            SELECT COALESCE(AVG(lcp.overall_mastery), 0)
            FROM learner_course_progress lcp
            JOIN classroom_member cm ON cm.user_id = lcp.user_id
            JOIN classroom_language_pack clp ON clp.language_pack_id = lcp.language_pack_id AND clp.classroom_id = cm.classroom_id
            WHERE cm.classroom_id = ? AND cm.role = 'student'
            """, Double.class, classroomId);

        int totalSubmissions = ((Number) thisWeek.get("total_submissions")).intValue();
        int acCount = ((Number) thisWeek.get("ac_count")).intValue();
        int activeStudents = ((Number) thisWeek.get("active_students")).intValue();
        double acRate = totalSubmissions > 0 ? (double) acCount / totalSubmissions * 100 : 0;

        StringBuilder dataContext = new StringBuilder();
        dataContext.append("班级基本情况：\n");
        dataContext.append("- 总学生数：").append(totalStudents).append("\n");
        dataContext.append("- 本周活跃学生：").append(activeStudents).append("/").append(totalStudents).append("\n");
        dataContext.append("- 本周提交总量：").append(totalSubmissions).append("，AC率：").append(String.format("%.0f%%", acRate)).append("\n");
        dataContext.append("- 班级平均掌握度：").append(String.format("%.0f%%", (avgMasteryNow != null ? avgMasteryNow : 0) * 100)).append("\n");
        dataContext.append("- 掌握度<50%的风险学生：").append(riskCount).append(" 人\n\n");

        if (!weakKcs.isEmpty()) {
            dataContext.append("最薄弱知识点：\n");
            for (Map<String, Object> kc : weakKcs) {
                dataContext.append("- ").append(kc.get("kc_name"))
                        .append("（平均掌握度 ").append(String.format("%.0f%%", ((Number) kc.get("avg_mastery")).doubleValue() * 100))
                        .append("，薄弱学生 ").append(kc.get("weak_count")).append("/").append(kc.get("total")).append("）\n");
            }
        }

        String systemPrompt = """
                你是一位面向非计算机专业Python初学者的教学分析顾问。根据班级数据生成一份简洁的周报。
                要求：
                - 分为 3-4 个段落，每段有标题
                - 包含：活跃度分析、知识掌握进展、需关注的风险点、教学建议
                - 数据引用必须与输入一致，不得虚构
                - 语气专业、简洁
                返回JSON: {"report_title": "标题", "sections": [{"heading": "段落标题", "content": "段落内容"}, ...]}
                """;
        Map<String, Object> llmResult = aiModelGateway.callForJson(systemPrompt, dataContext.toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report_title", llmResult.get("report_title"));
        result.put("sections", llmResult.get("sections"));
        result.put("raw_data", Map.of(
                "total_students", totalStudents,
                "active_students", activeStudents,
                "total_submissions", totalSubmissions,
                "ac_rate", Math.round(acRate),
                "risk_count", riskCount,
                "avg_mastery", Math.round((avgMasteryNow != null ? avgMasteryNow : 0) * 100)
        ));
        return result;
    }

    public Map<String, Object> getStudentProfile(String classroomId, Long studentUserId, Authentication authentication) {
        requireStaff(classroomId, authentication);

        Map<String, Object> basic = jdbcTemplate.queryForMap("""
            SELECT u.username,
                   COALESCE(lcp.overall_mastery, 0) AS overall_mastery,
                   COALESCE(lcp.problems_attempted, 0) AS problems_attempted,
                   COALESCE(lcp.problems_solved, 0) AS problems_solved,
                   lcp.last_activity_at
            FROM "user" u
            LEFT JOIN classroom_language_pack clp ON clp.classroom_id = ?
            LEFT JOIN learner_course_progress lcp ON lcp.user_id = u.id AND lcp.language_pack_id = clp.language_pack_id
            WHERE u.id = ?
            """, classroomId, studentUserId);

        List<Map<String, Object>> kcMastery = jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, c.title AS chapter_title, c.chapter_index,
                   km.mastery, km.attempt_count, km.correct_count, km.error_count
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            JOIN classroom_language_pack clp ON clp.language_pack_id = km.language_pack_id AND clp.classroom_id = ?
            WHERE km.user_id = ?
            ORDER BY c.chapter_index, k.id
            """, classroomId, studentUserId);

        List<Map<String, Object>> dailyTimeline = jdbcTemplate.queryForList("""
            SELECT d.day::date AS day,
                   COALESCE(sub.submission_count, 0) AS submission_count,
                   COALESCE(sub.ac_count, 0) AS ac_count
            FROM generate_series(current_date - interval '29 days', current_date, '1 day') AS d(day)
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS submission_count,
                       COUNT(CASE WHEN s.result = 0 THEN 1 END) AS ac_count
                FROM submission s
                WHERE s.user_id = ? AND s.create_time >= d.day AND s.create_time < d.day + interval '1 day'
            ) sub ON true
            ORDER BY d.day
            """, studentUserId);

        List<Map<String, Object>> errorDistribution = jdbcTemplate.queryForList("""
            SELECT n.error_taxonomy, COUNT(*) AS count
            FROM ai_learner_notebook n
            WHERE n.user_id = ? AND n.is_deleted = false
              AND n.error_taxonomy IS NOT NULL AND n.error_taxonomy <> 'unknown'
            GROUP BY n.error_taxonomy
            ORDER BY count DESC
            """, studentUserId);

        List<Map<String, Object>> recentSubmissions = jdbcTemplate.queryForList("""
            SELECT s.id, p.title, p._id AS problem_key, s.result, s.language, s.create_time
            FROM submission s
            LEFT JOIN problem p ON p.id = s.problem_id
            WHERE s.user_id = ?
            ORDER BY s.create_time DESC
            LIMIT 5
            """, studentUserId);

        int streak = 0;
        for (int i = dailyTimeline.size() - 1; i >= 0; i--) {
            int count = ((Number) dailyTimeline.get(i).get("submission_count")).intValue();
            if (count > 0) streak++;
            else break;
        }

        StringBuilder profileContext = new StringBuilder();
        profileContext.append("学生：").append(basic.get("username")).append("\n");
        profileContext.append("整体掌握度：").append(Math.round(((Number) basic.get("overall_mastery")).doubleValue() * 100)).append("%\n");
        profileContext.append("已做题/已通过：").append(basic.get("problems_attempted")).append("/").append(basic.get("problems_solved")).append("\n");
        profileContext.append("连续做题天数：").append(streak).append("\n");
        if (!kcMastery.isEmpty()) {
            profileContext.append("\nKC掌握情况：\n");
            for (Map<String, Object> kc : kcMastery) {
                profileContext.append("- ").append(kc.get("kc_name"))
                        .append("（").append(Math.round(((Number) kc.get("mastery")).doubleValue() * 100)).append("%）\n");
            }
        }
        if (!errorDistribution.isEmpty()) {
            profileContext.append("\n错题类型分布：\n");
            for (Map<String, Object> err : errorDistribution) {
                String taxonomy = String.valueOf(err.get("error_taxonomy"));
                profileContext.append("- ").append(com.alethicode.service.aitutor.contract.ErrorTaxonomy.label(taxonomy))
                        .append("：").append(err.get("count")).append("次\n");
            }
        }

        String systemPrompt = """
                你是一位教学分析师。根据学生学习数据，用 2-3 句话总结该学生的学习状况和改进建议。
                要求：指出优势和不足，给出具体方向性建议，语气温和专业。
                只返回 JSON: {"summary": "总结文本"}
                """;
        Map<String, Object> llmResult = aiModelGateway.callForJson(systemPrompt, profileContext.toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("basic", basic);
        result.put("kc_mastery", kcMastery);
        result.put("daily_timeline", dailyTimeline);
        result.put("error_distribution", errorDistribution);
        result.put("recent_submissions", recentSubmissions);
        result.put("streak", streak);
        result.put("llm_summary", llmResult.get("summary"));
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
