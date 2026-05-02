package com.alethicode.service.aitutor.path;

import com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于学习者掌握度（mastery）的自适应推荐题选择器。
 *
 * 内部完全委托 {@link BeginnerSupplementPlannerService}：
 * 1) 取该学生在该语言包内最薄弱的 KC
 * 2) 用 trigger="warmup" 调 buildPlan 拿到补给计划
 * 3) 从计划里抽出 coding_problem 卡（或 objective_problem 兜底）回组成 next-problem 接口约定的结构。
 */
@Service
public class MasteryAdaptiveProblemSelector {

    private static final int CARD_COUNT = 1;
    private static final String TRIGGER = "warmup";

    private final BeginnerSupplementPlannerService beginnerSupplementPlannerService;

    public MasteryAdaptiveProblemSelector(BeginnerSupplementPlannerService beginnerSupplementPlannerService) {
        this.beginnerSupplementPlannerService = beginnerSupplementPlannerService;
    }

    public Map<String, Object> selectNextProblem(Long userId, Long languagePackId) {
        if (userId == null) {
            throw new IllegalArgumentException("user_id is required");
        }
        if (languagePackId == null) {
            throw new IllegalArgumentException("language_pack_id is required");
        }

        Map<String, Object> plan = beginnerSupplementPlannerService.buildPlan(
                userId, TRIGGER, languagePackId, null, null, null, CARD_COUNT
        );

        List<?> cards = plan.get("cards") instanceof List<?> rawCards ? rawCards : List.of();
        Map<String, Object> codingCard = pickCardByType(cards, "coding_problem");
        Map<String, Object> selected = codingCard != null
                ? codingCard
                : pickCardByType(cards, "objective_problem");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("language_pack_id", languagePackId);
        Object targetKcs = plan.getOrDefault("target_kcs", List.of());
        result.put("target_kcs", targetKcs);
        if (targetKcs instanceof List<?> kcList && !kcList.isEmpty()) {
            result.put("weakest_kc", String.valueOf(kcList.get(0)));
        }

        if (selected == null) {
            result.put("status", "no_weak_kc");
            result.put("message", "暂无可推荐题目");
            result.put("recommended", null);
            result.put("candidates", List.of());
            result.put("selection_strategy", "adaptive_boundary");
            result.put("target_difficulty", null);
            return result;
        }

        Map<String, Object> payload = castMap(selected.get("payload"));
        Map<String, Object> recommended = new LinkedHashMap<>();
        recommended.put("problem_id", payload.get("problem_id"));
        recommended.put("problem_key", payload.get("problem_key"));
        recommended.put("title", payload.get("title"));
        recommended.put("difficulty", payload.get("difficulty"));
        recommended.put("question_type", payload.get("question_type"));
        recommended.put("card_type", selected.get("card_type"));
        recommended.put("why_this_now", selected.get("why_this_now"));

        result.put("status", "ok");
        result.put("selection_strategy", "adaptive_boundary");
        result.put("target_difficulty", payload.get("difficulty"));
        result.put("candidates", List.of(recommended));
        result.put("recommended", recommended);
        return result;
    }

    private Map<String, Object> pickCardByType(List<?> cards, String cardType) {
        for (Object card : cards) {
            if (card instanceof Map<?, ?> map && cardType.equals(map.get("card_type"))) {
                return castMap(map);
            }
        }
        return null;
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }
}
