package com.alethicode.service.aitutor.review;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复习包子题展示元信息（card_type / education_goal / why_this_now / target_kcs）的解析与默认值。
 * 抽离自 ErrorReviewPackageService（Phase 3），保持 service 主体专注于流程编排。
 */
@Component
class ReviewPackageProblemMetaResolver {

    @SuppressWarnings("unchecked")
    Map<Long, Map<String, Object>> extractFromSupplementPlan(Map<String, Object> supplementPlan) {
        if (supplementPlan == null || supplementPlan.isEmpty()) return Map.of();
        Object cardsRaw = supplementPlan.get("cards");
        if (!(cardsRaw instanceof List<?> cards) || cards.isEmpty()) return Map.of();
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        int sequence = 0;
        for (Object cardRaw : cards) {
            if (!(cardRaw instanceof Map<?, ?> cardMapRaw)) continue;
            sequence++;
            Map<String, Object> card = (Map<String, Object>) cardMapRaw;
            Map<String, Object> payload = card.get("payload") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            Long problemId = parseLong(payload.get("problem_id") == null ? null : payload.get("problem_id").toString());
            if (problemId == null) continue;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("problem_id", problemId);
            meta.put("sequence", sequence);
            meta.put("card_type", stringValue(card.get("card_type")));
            meta.put("education_goal", stringValue(card.get("education_goal")));
            meta.put("why_this_now", stringValue(card.get("why_this_now")));
            meta.put("target_kcs", toStringList(card.get("target_kcs")));
            result.put(problemId, meta);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> resolveFromEvidenceSummary(Map<String, Object> evidenceSummary, Long problemId) {
        if (evidenceSummary == null || evidenceSummary.isEmpty() || problemId == null) return Map.of();
        Object cardsRaw = evidenceSummary.get("planned_problem_cards");
        if (!(cardsRaw instanceof List<?> cards)) return Map.of();
        for (Object cardRaw : cards) {
            if (!(cardRaw instanceof Map<?, ?> mapRaw)) continue;
            Map<String, Object> card = (Map<String, Object>) mapRaw;
            Long candidateProblemId = parseLong(stringValue(card.get("problem_id")));
            if (candidateProblemId != null && candidateProblemId.equals(problemId)) return card;
        }
        return Map.of();
    }

    Map<String, Object> defaultMeta(int sequence, boolean isAiGenerated, Map<String, Object> problemInfo) {
        String questionType = stringValue(problemInfo.get("question_type"));
        String cardType = isAiGenerated ? "transfer_problem"
                : ("choice".equals(questionType) || "fill_blank".equals(questionType) ? "objective_problem" : "coding_problem");
        String educationGoal = switch (sequence) { case 1 -> "recall"; case 2 -> "apply"; default -> "transfer"; };
        String whyThisNow = switch (sequence) {
            case 1 -> "先纠偏，快速修正当前错误模式。";
            case 2 -> "再做标准题，确认你已能稳定应用。";
            default -> "最后做迁移，检查是否真正掌握。";
        };
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("card_type", cardType);
        meta.put("education_goal", educationGoal);
        meta.put("why_this_now", whyThisNow);
        meta.put("target_kcs", List.of());
        return meta;
    }

    List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            String normalized = stringValue(item);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return result;
    }

    String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Long.parseLong(raw.trim()); } catch (NumberFormatException ignore) { return null; }
    }
}
