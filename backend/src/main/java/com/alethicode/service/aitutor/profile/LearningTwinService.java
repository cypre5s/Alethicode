package com.alethicode.service.aitutor.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningTwinService {

    private static final double WEAK_THRESHOLD = 0.4;

    private final JdbcTemplate jdbcTemplate;

    public LearningTwinService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getLearningTwin(Long userId, Long languagePackId, Long problemId) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> progress = queryCourseProgress(userId, languagePackId);
        result.put("overall_mastery", progress.getOrDefault("overall_mastery", 0.0));
        result.put("problems_attempted", progress.getOrDefault("problems_attempted", 0));
        result.put("problems_solved", progress.getOrDefault("problems_solved", 0));

        List<Map<String, Object>> problemKcs = queryProblemKcs(userId, languagePackId, problemId);
        enrichKcsWithReviewActions(problemKcs, userId, languagePackId);
        result.put("current_problem_kcs", problemKcs);

        List<Map<String, Object>> memories = queryActiveMemories(userId);
        result.put("active_memories", memories);

        List<Map<String, Object>> recentSubs = queryRecentSubmissions(userId, problemId);
        result.put("recent_submissions", recentSubs);

        List<String> blockers = new ArrayList<>();
        for (Map<String, Object> kc : problemKcs) {
            Object masteryObj = kc.get("mastery");
            double mastery = masteryObj instanceof Number n ? n.doubleValue() : 0.0;
            if (mastery < WEAK_THRESHOLD) {
                Object nameObj = kc.get("name");
                String name = nameObj == null ? "未知知识点" : String.valueOf(nameObj);
                blockers.add(name + " (掌握度 " + Math.round(mastery * 100) + "%)");
            }
        }
        result.put("predicted_blockers", blockers);

        List<String> actions = new ArrayList<>();
        if (!blockers.isEmpty()) {
            actions.add("复习薄弱知识点");
        }
        if (!recentSubs.isEmpty()) {
            actions.add("查看相似错误的解题思路");
        }
        actions.add("向 AI 助教提问");
        result.put("recommended_actions", actions);

        return result;
    }

    private Map<String, Object> queryCourseProgress(Long userId, Long languagePackId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT overall_mastery, problems_attempted, problems_solved
            FROM learner_course_progress
            WHERE user_id = ? AND language_pack_id = ?
            """, userId, languagePackId);

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("overall_mastery", 0.0);
        fallback.put("problems_attempted", 0);
        fallback.put("problems_solved", 0);
        if (rows.isEmpty()) {
            return fallback;
        }
        Map<String, Object> row = rows.getFirst();
        Map<String, Object> coalesced = new LinkedHashMap<>(fallback);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() != null) {
                coalesced.put(entry.getKey(), entry.getValue());
            }
        }
        return coalesced;
    }

    private List<Map<String, Object>> queryProblemKcs(Long userId, Long languagePackId, Long problemId) {
        return jdbcTemplate.queryForList("""
            SELECT k.id AS kc_id, k.name, COALESCE(km.mastery, 0) AS mastery,
                   CASE WHEN COALESCE(km.mastery, 0) < 0.4 THEN 'weak'
                        WHEN COALESCE(km.mastery, 0) < 0.7 THEN 'medium'
                        ELSE 'strong' END AS level
            FROM ai_problem_kc_mapping m
            JOIN language_pack_kc k ON k.synced_ai_kc_id = m.kc_id
                  AND k.language_pack_id = ?
            LEFT JOIN learner_kc_mastery km ON km.kc_id = k.id
                  AND km.user_id = ? AND km.language_pack_id = ?
            WHERE m.problem_id = ?
            ORDER BY m.weight DESC
            """, languagePackId, userId, languagePackId, problemId);
    }

    private List<Map<String, Object>> queryActiveMemories(Long userId) {
        return jdbcTemplate.queryForList("""
            SELECT memory_key, memory_type, memory_value, confidence
            FROM ai_learner_memory
            WHERE user_id = ? AND enabled = true AND confidence > 0.5
              AND memory_type = 'error_pattern'
            ORDER BY confidence DESC
            LIMIT 5
            """, userId);
    }

    private List<Map<String, Object>> queryRecentSubmissions(Long userId, Long problemId) {
        return jdbcTemplate.queryForList("""
            SELECT id, result, language, create_time
            FROM submission
            WHERE user_id = ? AND problem_id = ?
            ORDER BY create_time DESC
            LIMIT 5
            """, userId, problemId);
    }

    /**
     * 对 mastery 低于 {@link KcReviewActionBuilder#WEAK_THRESHOLD} 的 KC 追加 {@code recommended_review_actions}。
     *
     * 为了避免一次多 N 次 SQL，先一次性统计每个 KC 对应题目的 submission/accepted/problem 计数，再逐个组装。
     */
    private void enrichKcsWithReviewActions(List<Map<String, Object>> kcs, Long userId, Long languagePackId) {
        if (kcs == null || kcs.isEmpty()) {
            return;
        }
        List<Long> weakKcIds = new ArrayList<>();
        for (Map<String, Object> kc : kcs) {
            double mastery = asDouble(kc.get("mastery"));
            Object kcIdObj = kc.get("kc_id");
            if (mastery < KcReviewActionBuilder.WEAK_THRESHOLD && kcIdObj instanceof Number n) {
                weakKcIds.add(n.longValue());
            }
        }
        if (weakKcIds.isEmpty()) {
            return;
        }

        Map<Long, long[]> stats = queryKcAggregateStats(userId, languagePackId, weakKcIds);
        for (Map<String, Object> kc : kcs) {
            double mastery = asDouble(kc.get("mastery"));
            if (mastery >= KcReviewActionBuilder.WEAK_THRESHOLD) {
                continue;
            }
            Object kcIdObj = kc.get("kc_id");
            if (!(kcIdObj instanceof Number n)) {
                continue;
            }
            long kcId = n.longValue();
            long[] stat = stats.getOrDefault(kcId, new long[]{0L, 0L, 0L});
            List<Map<String, Object>> actions = KcReviewActionBuilder.buildForWeakKc(
                    String.valueOf(kc.getOrDefault("name", "")),
                    mastery,
                    stat[0],
                    stat[1],
                    stat[2]
            );
            kc.put("recommended_review_actions", actions);
        }
    }

    private Map<Long, long[]> queryKcAggregateStats(Long userId, Long languagePackId, List<Long> kcIds) {
        if (kcIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(kcIds.size(), "?"));
        Object[] args = new Object[kcIds.size() + 2];
        args[0] = languagePackId;
        args[1] = userId;
        for (int i = 0; i < kcIds.size(); i++) {
            args[i + 2] = kcIds.get(i);
        }
        String sql = """
            SELECT m.kc_id,
                   COUNT(DISTINCT m.problem_id) AS problem_count,
                   COUNT(s.id) AS submission_count,
                   COALESCE(SUM(CASE WHEN s.result = 0 THEN 1 ELSE 0 END), 0) AS accepted_count
            FROM ai_problem_kc_mapping m
            LEFT JOIN submission s ON s.problem_id = m.problem_id AND s.user_id = ?
            WHERE m.language_pack_id = ? AND m.kc_id IN (""" + placeholders + """
            )
            GROUP BY m.kc_id
            """;
        // userId 与 languagePackId 的顺序要和 SQL 里的 ? 对齐
        Object[] reorderedArgs = new Object[kcIds.size() + 2];
        reorderedArgs[0] = userId;
        reorderedArgs[1] = languagePackId;
        for (int i = 0; i < kcIds.size(); i++) {
            reorderedArgs[i + 2] = kcIds.get(i);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, reorderedArgs);
        Map<Long, long[]> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long kcId = asLong(row.get("kc_id"));
            long submissionCount = asLong(row.get("submission_count"));
            long acceptedCount = asLong(row.get("accepted_count"));
            long problemCount = asLong(row.get("problem_count"));
            result.put(kcId, new long[]{submissionCount, acceptedCount, problemCount});
        }
        return result;
    }

    private static double asDouble(Object raw) {
        return raw instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static long asLong(Object raw) {
        return raw instanceof Number n ? n.longValue() : 0L;
    }
}
