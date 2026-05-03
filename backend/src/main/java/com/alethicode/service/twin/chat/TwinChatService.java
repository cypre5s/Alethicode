package com.alethicode.service.twin.chat;

import com.alethicode.service.aitutor.profile.LearnerNarrativeSummaryService;
import com.alethicode.service.aitutor.profile.LearnerNarrativeSummaryService.NarrativeSummary;
import com.alethicode.service.twin.health.LearningHealthAggregator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 孪生对话服务：学生向孪生提问关于自己学习状态的元学习问题。
 * 不引入新 LLM 调用，直接基于学情数据生成结构化回答。
 */
@Service
public class TwinChatService {

    private final JdbcTemplate jdbcTemplate;
    private final LearnerNarrativeSummaryService summaryService;
    private final LearningHealthAggregator healthAggregator;

    public TwinChatService(JdbcTemplate jdbcTemplate,
                           LearnerNarrativeSummaryService summaryService,
                           LearningHealthAggregator healthAggregator) {
        this.jdbcTemplate = jdbcTemplate;
        this.summaryService = summaryService;
        this.healthAggregator = healthAggregator;
    }

    public Map<String, Object> askTwin(Long userId, String question) {
        Map<String, Object> response = new LinkedHashMap<>();
        String lowerQ = question.toLowerCase();

        if (containsAny(lowerQ, "最近", "近况", "状态", "怎么样")) {
            return buildSummaryResponse(userId);
        }
        if (containsAny(lowerQ, "弱", "薄弱", "不会", "卡在", "困难")) {
            return buildWeaknessResponse(userId);
        }
        if (containsAny(lowerQ, "下一步", "接下来", "该学什么", "推荐")) {
            return buildNextStepResponse(userId);
        }
        if (containsAny(lowerQ, "复习", "遗忘", "记忆")) {
            return buildReviewResponse(userId);
        }

        return buildSummaryResponse(userId);
    }

    public List<Map<String, Object>> getQuickQuestions() {
        return List.of(
                Map.of("id", "status", "text", "我最近学得怎么样？"),
                Map.of("id", "weakness", "text", "我最薄弱的知识点是什么？"),
                Map.of("id", "next", "text", "下一步该学什么？"),
                Map.of("id", "review", "text", "我有什么需要复习的？")
        );
    }

    private Map<String, Object> buildSummaryResponse(Long userId) {
        NarrativeSummary summary = summaryService.loadOrGenerate(userId);
        Map<String, Object> health = healthAggregator.aggregate(userId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("answer", summary.summaryText().isEmpty()
                ? "你还没有足够的学习数据，先做几道题让我了解你吧！"
                : summary.summaryText());
        resp.put("data_source", "narrative_summary + health");
        resp.put("health_snapshot", health.get("frequency"));
        return resp;
    }

    private Map<String, Object> buildWeaknessResponse(Long userId) {
        List<Map<String, Object>> weakKcs = jdbcTemplate.query("""
            SELECT kc.name, m.mastery
            FROM learner_kc_mastery m
            JOIN language_pack_kc kc ON kc.id = m.kc_id
            WHERE m.user_id = ? AND m.mastery < 0.5
            ORDER BY m.mastery ASC
            LIMIT 5
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", rs.getString("name"));
            row.put("mastery", rs.getDouble("mastery"));
            return row;
        }, userId);

        Map<String, Object> resp = new LinkedHashMap<>();
        if (weakKcs.isEmpty()) {
            resp.put("answer", "目前没有发现明显的薄弱知识点，继续保持！");
        } else {
            StringBuilder sb = new StringBuilder("你目前最薄弱的知识点是：\n");
            for (var kc : weakKcs) {
                sb.append("• ").append(kc.get("name")).append("（掌握度 ")
                        .append(Math.round((double) kc.get("mastery") * 100)).append("%）\n");
            }
            resp.put("answer", sb.toString().trim());
        }
        resp.put("data_source", "learner_kc_mastery");
        resp.put("weak_kcs", weakKcs);
        return resp;
    }

    private Map<String, Object> buildNextStepResponse(Long userId) {
        List<Map<String, Object>> nextKcs = jdbcTemplate.query("""
            SELECT kc.name, m.mastery
            FROM learner_kc_mastery m
            JOIN language_pack_kc kc ON kc.id = m.kc_id
            WHERE m.user_id = ? AND m.mastery >= 0.3 AND m.mastery < 0.7
            ORDER BY m.mastery ASC
            LIMIT 3
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", rs.getString("name"));
            row.put("mastery", rs.getDouble("mastery"));
            return row;
        }, userId);

        Map<String, Object> resp = new LinkedHashMap<>();
        if (nextKcs.isEmpty()) {
            resp.put("answer", "建议先做更多题目建立基础，然后我能给出更精准的推荐。");
        } else {
            StringBuilder sb = new StringBuilder("建议接下来重点突破：\n");
            for (var kc : nextKcs) {
                sb.append("• ").append(kc.get("name")).append("（当前 ")
                        .append(Math.round((double) kc.get("mastery") * 100)).append("%，潜力区间）\n");
            }
            resp.put("answer", sb.toString().trim());
        }
        resp.put("data_source", "learner_kc_mastery");
        resp.put("suggested_kcs", nextKcs);
        return resp;
    }

    private Map<String, Object> buildReviewResponse(Long userId) {
        Map<String, Object> health = healthAggregator.aggregate(userId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dueReviews = (List<Map<String, Object>>) health.get("due_reviews");

        Map<String, Object> resp = new LinkedHashMap<>();
        if (dueReviews == null || dueReviews.isEmpty()) {
            resp.put("answer", "目前没有待复习的内容，继续做新题吧！");
        } else {
            StringBuilder sb = new StringBuilder("你有 ").append(dueReviews.size()).append(" 个复习包待完成：\n");
            for (var r : dueReviews) {
                sb.append("• ").append(r.get("title")).append("\n");
            }
            resp.put("answer", sb.toString().trim());
        }
        resp.put("data_source", "fsrs_review_packages");
        resp.put("due_reviews", dueReviews);
        return resp;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
