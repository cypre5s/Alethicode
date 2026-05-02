package com.alethicode.service.aitutor.policy;

import com.alethicode.service.aitutor.profile.LearnerState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TutorActionPolicy {

    public TutorActionDecision decide(String phase, String pending, LearnerState learnerState) {
        List<Map<String, Object>> actions = new ArrayList<>(baseActions(phase, pending, learnerState));
        String recommended = recommendAction(phase, learnerState, actions);
        reorder(actions, recommended);
        String confidence = learnerState == null ? "medium" : switch (learnerState.frustrationLevel()) {
            case "severe", "high" -> "high";
            case "medium" -> "medium";
            default -> "high";
        };
        String reason = switch (recommended) {
            case "re_ideate" -> "连续错误较多，优先重新梳理解题步骤";
            case "ac_review" -> "跨题画像显示当前知识点仍薄弱，先完成复盘巩固";
            case "transfer" -> "当前题已经完成，进入迁移练习";
            default -> "保持当前主线阶段的最小下一步动作";
        };
        return new TutorActionDecision(recommended, confidence, reason, actions);
    }

    private List<Map<String, Object>> baseActions(String phase, String pending, LearnerState learnerState) {
        List<Map<String, Object>> actions = new ArrayList<>();
        switch (phase == null ? "" : phase) {
            case "READING" -> {
                actions.add(action("problem_guide", "获取题目导读", 1, "READING"));
                actions.add(action("ideate", "思路分析", 2, "IDEATING"));
            }
            case "IDEATING" -> {
                actions.add(action("ideate", "继续思路分析", 2, "IDEATING"));
                actions.add(action("coding", "开始编码", null, "CODING"));
            }
            case "CODING" -> {}
            case "ERROR_FEEDBACK" -> {
                actions.add(action("error_chain", "错误诊断", 4, "ERROR_FEEDBACK"));
                actions.add(action("re_read", "重新审题", 1, "READING"));
                actions.add(action("re_ideate", "重新梳理思路", 2, "IDEATING"));
            }
            case "AC_REVIEW" -> {
                actions.add(action("ac_review", "AC 复盘", 5, "AC_REVIEW"));
                actions.add(action("metacognitive", "思维反思", 7, "METACOGNITIVE"));
                actions.add(action("transfer", "迁移练习", 6, "TRANSFER"));
            }
            case "TRANSFER" -> {
                actions.add(action("transfer", "重新生成迁移题", 6, "TRANSFER"));
                actions.add(action("coding", "返回编码", null, "CODING"));
            }
            default -> {
            }
        }
        if ("confirm_transfer".equals(pending)) {
            actions.removeIf(item -> "coding".equals(item.get("key")));
        }
        return actions;
    }

    private String recommendAction(String phase, LearnerState learnerState, List<Map<String, Object>> actions) {
        if (actions.isEmpty()) {
            return "";
        }
        if ("READING".equals(phase) && learnerState != null) {
            if ("low".equals(learnerState.confidenceProxy()) || hasMemoryKey(learnerState, "reading_pref")) {
                return "problem_guide";
            }
        }
        if ("ERROR_FEEDBACK".equals(phase) && learnerState != null
                && ("high".equals(learnerState.frustrationLevel()) || "severe".equals(learnerState.frustrationLevel()))) {
            return "re_ideate";
        }
        if ("AC_REVIEW".equals(phase)) {
            if (learnerState != null && (!learnerState.weakKcs().isEmpty() || hasCrossCourseWeakKcs(learnerState))) {
                return "ac_review";
            }
            return "transfer";
        }
        return actions.isEmpty() ? "" : String.valueOf(actions.getFirst().get("key"));
    }

    private void reorder(List<Map<String, Object>> actions, String recommended) {
        if (recommended == null || recommended.isBlank()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < actions.size(); i++) {
            if (recommended.equals(actions.get(i).get("key"))) {
                index = i;
                break;
            }
        }
        if (index > 0) {
            Map<String, Object> preferred = actions.remove(index);
            actions.addFirst(preferred);
        }
    }

    private Map<String, Object> action(String key, String label, Integer agentId, String event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        if (agentId != null) {
            item.put("agent_id", agentId);
        }
        item.put("event", event);
        return item;
    }

    private boolean hasCrossCourseWeakKcs(LearnerState learnerState) {
        Object raw = learnerState.recommendedActionBias().get("cross_course_weak_kcs");
        return raw instanceof List<?> list && !list.isEmpty();
    }

    private boolean hasMemoryKey(LearnerState learnerState, String keyPart) {
        for (Map<String, Object> memoryRef : learnerState.memoryRefs()) {
            String memoryKey = String.valueOf(memoryRef.getOrDefault("memory_key", ""));
            if (memoryKey.contains(keyPart)) {
                return true;
            }
        }
        return false;
    }
}
