package com.alethicode.service.usagestats.impl;

import com.alethicode.service.usagestats.UsageStatsService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>窗口字段（{@code interval}）由 {@link #resolveInterval(String)} 用白名单转换，再拼到
 * SQL 字面量 {@code NOW() - INTERVAL 'N days'} 中。NamedParameter 不支持参数化 INTERVAL，
 * 改 cast 字符串又会触发 PostgreSQL prepared statement 的类型推断失败（参考 4/30 admin
 * 反馈列表 500 修复），白名单拼接是当前最简且无注入风险的方案。
 */
@Service
public class UsageStatsServiceImpl implements UsageStatsService {

    private final NamedParameterJdbcTemplate jdbc;

    public UsageStatsServiceImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String resolveInterval(String range) {
        if (range == null) {
            return "7 days";
        }
        return switch (range) {
            case "today" -> "1 day";
            case "30d" -> "30 days";
            default -> "7 days";
        };
    }

    @Override
    public Map<String, Object> getStats(String range) {
        String interval = resolveInterval(range);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("range", range == null ? "7d" : range);
        out.put("interval", interval);
        out.put("overview", loadOverview(interval));
        out.put("daily_active", loadDailyActive(interval));
        out.put("ai_value", loadAiValue(interval));
        out.put("pain_points", loadPainPoints(interval));
        out.put("feedback_summary", loadFeedbackSummary(interval));
        return out;
    }

    private Map<String, Object> loadOverview(String interval) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM "user") AS total_users,
                  (SELECT COUNT(*) FROM "user" WHERE admin_type = 'Regular User') AS total_students,
                  (SELECT COUNT(DISTINCT user_id) FROM (
                      SELECT user_id FROM submission
                        WHERE create_time >= NOW() - INTERVAL '%s' AND user_id IS NOT NULL
                      UNION
                      SELECT user_id FROM ai_tutor_workflow_session
                        WHERE updated_at >= NOW() - INTERVAL '%s' AND user_id IS NOT NULL
                      UNION
                      SELECT user_id FROM language_pack_chat_session
                        WHERE update_time >= NOW() - INTERVAL '%s' AND user_id IS NOT NULL
                  ) t) AS active_users,
                  (SELECT COUNT(*) FROM submission) AS total_submissions,
                  (SELECT COUNT(*) FROM submission WHERE create_time >= NOW() - INTERVAL '%s') AS recent_submissions,
                  (SELECT COUNT(*) FILTER (WHERE result = 0) FROM submission
                    WHERE create_time >= NOW() - INTERVAL '%s') AS recent_ac
                """.formatted(interval, interval, interval, interval, interval);

        Map<String, Object> base = jdbc.queryForMap(sql, new MapSqlParameterSource());

        long recentSubs = asLong(base.get("recent_submissions"));
        long recentAc = asLong(base.get("recent_ac"));
        double acRate = recentSubs == 0 ? 0.0 : (double) recentAc / recentSubs;
        base.put("ac_rate", acRate);

        // 首次尝试就 AC 的比例（user × problem 的 MIN(create_time) 一行）
        String firstAcSql = """
                WITH first_attempt AS (
                  SELECT DISTINCT ON (user_id, problem_id) user_id, problem_id, result, create_time
                  FROM submission
                  WHERE user_id IS NOT NULL AND problem_id IS NOT NULL
                    AND create_time >= NOW() - INTERVAL '%s'
                  ORDER BY user_id, problem_id, create_time ASC
                )
                SELECT
                  COUNT(*) AS attempts,
                  COUNT(*) FILTER (WHERE result = 0) AS first_ac
                FROM first_attempt
                """.formatted(interval);
        Map<String, Object> firstAc = jdbc.queryForMap(firstAcSql, new MapSqlParameterSource());
        long firstAttempts = asLong(firstAc.get("attempts"));
        long firstAcCount = asLong(firstAc.get("first_ac"));
        base.put("first_attempt_total", firstAttempts);
        base.put("first_ac_count", firstAcCount);
        base.put("first_ac_rate", firstAttempts == 0 ? 0.0 : (double) firstAcCount / firstAttempts);

        // 平均到 AC 时长（秒）
        String avgToAcSql = """
                WITH first_sub AS (
                  SELECT user_id, problem_id, MIN(create_time) AS first_time
                  FROM submission
                  WHERE user_id IS NOT NULL AND problem_id IS NOT NULL
                    AND create_time >= NOW() - INTERVAL '%s'
                  GROUP BY user_id, problem_id
                ),
                ac_sub AS (
                  SELECT DISTINCT ON (user_id, problem_id) user_id, problem_id, create_time AS ac_time
                  FROM submission
                  WHERE result = 0 AND user_id IS NOT NULL AND problem_id IS NOT NULL
                  ORDER BY user_id, problem_id, create_time ASC
                )
                SELECT AVG(EXTRACT(EPOCH FROM (a.ac_time - f.first_time))) AS avg_seconds
                FROM first_sub f
                JOIN ac_sub a ON a.user_id = f.user_id AND a.problem_id = f.problem_id
                WHERE a.ac_time >= f.first_time
                """.formatted(interval);
        Map<String, Object> avgToAc = jdbc.queryForMap(avgToAcSql, new MapSqlParameterSource());
        Double avgSeconds = avgToAc.get("avg_seconds") == null ? null
                : ((Number) avgToAc.get("avg_seconds")).doubleValue();
        base.put("avg_to_ac_seconds", avgSeconds);

        return base;
    }

    private List<Map<String, Object>> loadDailyActive(String interval) {
        String sql = """
                WITH days AS (
                  SELECT generate_series(
                    DATE(NOW() - INTERVAL '%s'),
                    DATE(NOW()),
                    INTERVAL '1 day'
                  )::date AS day
                ),
                sub_daily AS (
                  SELECT DATE(create_time) AS day, COUNT(*) AS submissions,
                         COUNT(*) FILTER (WHERE result = 0) AS ac_count,
                         COUNT(DISTINCT user_id) AS sub_users
                  FROM submission
                  WHERE create_time >= NOW() - INTERVAL '%s'
                  GROUP BY day
                ),
                ai_daily AS (
                  SELECT DATE(updated_at) AS day, COUNT(*) AS ai_calls,
                         COUNT(DISTINCT user_id) AS ai_users
                  FROM ai_tutor_workflow_session
                  WHERE updated_at >= NOW() - INTERVAL '%s'
                  GROUP BY day
                )
                SELECT
                  d.day,
                  COALESCE(s.submissions, 0) AS submissions,
                  COALESCE(s.ac_count, 0) AS ac_count,
                  COALESCE(s.sub_users, 0) AS sub_users,
                  COALESCE(a.ai_calls, 0) AS ai_calls,
                  COALESCE(a.ai_users, 0) AS ai_users
                FROM days d
                LEFT JOIN sub_daily s ON s.day = d.day
                LEFT JOIN ai_daily a ON a.day = d.day
                ORDER BY d.day
                """.formatted(interval, interval, interval);
        return jdbc.queryForList(sql, new MapSqlParameterSource());
    }

    private Map<String, Object> loadAiValue(String interval) {
        Map<String, Object> out = new LinkedHashMap<>();

        String overviewSql = """
                SELECT
                  (SELECT COUNT(*) FROM ai_tutor_workflow_session
                    WHERE updated_at >= NOW() - INTERVAL '%s') AS total_calls,
                  (SELECT COUNT(DISTINCT user_id) FROM ai_tutor_workflow_session
                    WHERE updated_at >= NOW() - INTERVAL '%s' AND user_id IS NOT NULL) AS active_ai_users,
                  (SELECT COUNT(*) FROM "user" WHERE admin_type = 'Regular User') AS total_students
                """.formatted(interval, interval);
        Map<String, Object> ov = jdbc.queryForMap(overviewSql, new MapSqlParameterSource());
        long totalCalls = asLong(ov.get("total_calls"));
        long aiUsers = asLong(ov.get("active_ai_users"));
        long students = asLong(ov.get("total_students"));
        out.put("total_calls", totalCalls);
        out.put("ai_user_count", aiUsers);
        out.put("ai_user_coverage", students == 0 ? 0.0 : (double) aiUsers / students);

        // 卡片类型分布（7 类：reading / ideating / skeleton / error_diagnosis / post_ac / transfer / 其它）
        String cardSql = """
                SELECT card_type, COUNT(*) AS cnt
                FROM ai_tutor_workflow_event
                WHERE created_at >= NOW() - INTERVAL '%s'
                  AND card_type IS NOT NULL AND card_type <> ''
                GROUP BY card_type
                ORDER BY cnt DESC
                """.formatted(interval);
        out.put("card_distribution", jdbc.queryForList(cardSql, new MapSqlParameterSource()));

        // 错误诊断 hit 率：error_diagnosis 卡片下发后，session 关联的 user × problem 在该卡片创建之后的下一次提交是否 AC
        String hitSql = """
                WITH diag_events AS (
                  SELECT e.created_at AS diag_time, s.user_id, s.problem_id
                  FROM ai_tutor_workflow_event e
                  JOIN ai_tutor_workflow_session s ON s.session_id = e.session_id
                  WHERE e.card_type = 'error_diagnosis'
                    AND e.created_at >= NOW() - INTERVAL '%s'
                    AND s.user_id IS NOT NULL AND s.problem_id IS NOT NULL
                ),
                next_sub AS (
                  SELECT de.user_id, de.problem_id, de.diag_time,
                    (SELECT sb.result FROM submission sb
                      WHERE sb.user_id = de.user_id
                        AND sb.problem_id = de.problem_id
                        AND sb.create_time > de.diag_time
                      ORDER BY sb.create_time ASC LIMIT 1) AS next_result
                  FROM diag_events de
                )
                SELECT
                  COUNT(*) AS total_diag,
                  COUNT(*) FILTER (WHERE next_result IS NOT NULL) AS with_next,
                  COUNT(*) FILTER (WHERE next_result = 0) AS hit
                FROM next_sub
                """.formatted(interval);
        Map<String, Object> hit = jdbc.queryForMap(hitSql, new MapSqlParameterSource());
        long totalDiag = asLong(hit.get("total_diag"));
        long withNext = asLong(hit.get("with_next"));
        long hitCount = asLong(hit.get("hit"));
        out.put("error_diagnosis_total", totalDiag);
        out.put("error_diagnosis_with_next", withNext);
        out.put("error_diagnosis_hit", hitCount);
        out.put("error_diagnosis_hit_rate", withNext == 0 ? 0.0 : (double) hitCount / withNext);

        return out;
    }

    private Map<String, Object> loadPainPoints(String interval) {
        Map<String, Object> out = new LinkedHashMap<>();

        // 高 WA 题目排行（窗口期内尝试 ≥ 5 次且 AC 率最低的 10 道）
        String highWaSql = """
                SELECT
                  s.problem_id,
                  p._id AS display_id,
                  p.title,
                  COUNT(*) AS attempts,
                  COUNT(*) FILTER (WHERE s.result = 0) AS ac_count,
                  COUNT(DISTINCT s.user_id) AS user_count
                FROM submission s
                LEFT JOIN problem p ON p.id = s.problem_id
                WHERE s.create_time >= NOW() - INTERVAL '%s'
                  AND s.problem_id IS NOT NULL
                GROUP BY s.problem_id, p._id, p.title
                HAVING COUNT(*) >= 5
                ORDER BY (COUNT(*) FILTER (WHERE s.result = 0))::numeric / COUNT(*) ASC,
                         COUNT(*) DESC
                LIMIT 10
                """.formatted(interval);
        out.put("high_wa_problems", jdbc.queryForList(highWaSql, new MapSqlParameterSource()));

        // 高重试题目（人均尝试 ≥ 5 次）
        String stuckSql = """
                WITH per_user AS (
                  SELECT problem_id, user_id, COUNT(*) AS attempts,
                         BOOL_OR(result = 0) AS ever_ac
                  FROM submission
                  WHERE create_time >= NOW() - INTERVAL '%s'
                    AND problem_id IS NOT NULL AND user_id IS NOT NULL
                  GROUP BY problem_id, user_id
                )
                SELECT
                  pu.problem_id,
                  p._id AS display_id,
                  p.title,
                  COUNT(*) AS user_count,
                  ROUND(AVG(pu.attempts)::numeric, 2) AS avg_attempts,
                  COUNT(*) FILTER (WHERE NOT pu.ever_ac) AS stuck_users
                FROM per_user pu
                LEFT JOIN problem p ON p.id = pu.problem_id
                GROUP BY pu.problem_id, p._id, p.title
                HAVING AVG(pu.attempts) >= 5
                ORDER BY AVG(pu.attempts) DESC
                LIMIT 10
                """.formatted(interval);
        out.put("stuck_problems", jdbc.queryForList(stuckSql, new MapSqlParameterSource()));

        return out;
    }

    private Map<String, Object> loadFeedbackSummary(String interval) {
        Map<String, Object> out = new LinkedHashMap<>();

        String totalSql = """
                SELECT COUNT(*) AS total FROM beta_feedback_report
                WHERE created_at >= NOW() - INTERVAL '%s'
                """.formatted(interval);
        out.put("total", asLong(jdbc.queryForMap(totalSql, new MapSqlParameterSource()).get("total")));

        String bySevSql = """
                SELECT severity, COUNT(*) AS cnt FROM beta_feedback_report
                WHERE created_at >= NOW() - INTERVAL '%s'
                GROUP BY severity ORDER BY cnt DESC
                """.formatted(interval);
        out.put("by_severity", jdbc.queryForList(bySevSql, new MapSqlParameterSource()));

        String byTypeSql = """
                SELECT type, COUNT(*) AS cnt FROM beta_feedback_report
                WHERE created_at >= NOW() - INTERVAL '%s'
                GROUP BY type ORDER BY cnt DESC
                """.formatted(interval);
        out.put("by_type", jdbc.queryForList(byTypeSql, new MapSqlParameterSource()));

        return out;
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
