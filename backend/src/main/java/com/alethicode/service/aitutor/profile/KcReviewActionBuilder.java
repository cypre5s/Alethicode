package com.alethicode.service.aitutor.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为单个薄弱知识点（Knowledge Component）组装「推荐复习动作」列表。
 *
 * 使用约束：
 * - 只在 {@code mastery < WEAK_THRESHOLD} 时调用；掌握度已经不错的 KC 不需要专门复习入口
 * - 返回结构面向展示层（学习画像 tooltip、Star Map tooltip、AI 学习助手侧栏等）
 *
 * 每一项均为 {@code {key, label, hint}} 结构：
 * - {@code key}：动作类型标识（前端可以按 key 决定跳转行为，例如打开 AI 讲解、跳错题本等）
 * - {@code label}：学生看得见的主文案
 * - {@code hint}：辅助说明（tooltip 次行）
 */
public final class KcReviewActionBuilder {

    public static final double WEAK_THRESHOLD = 0.4;
    private static final double VERY_LOW_MASTERY = 0.3;

    private KcReviewActionBuilder() {
    }

    /**
     * 对弱 KC 组装推荐动作。
     *
     * @param kcName          知识点中文名（必填，禁止为空）
     * @param mastery         当前掌握度（0.0 ~ 1.0）
     * @param submissionCount 该 KC 相关题目的学生提交总次数（可为 null）
     * @param acceptedCount   该 KC 相关题目的学生 AC 次数（可为 null）
     * @param problemCount    该 KC 在当前课程包下关联的题目总数（可为 null）
     * @return 动作列表；如果不构成"弱 KC"条件则返回空列表
     */
    public static List<Map<String, Object>> buildForWeakKc(
            String kcName,
            double mastery,
            Long submissionCount,
            Long acceptedCount,
            Long problemCount
    ) {
        if (kcName == null || kcName.isBlank()) {
            throw new IllegalArgumentException("kcName is required");
        }
        if (mastery >= WEAK_THRESHOLD) {
            return List.of();
        }
        List<Map<String, Object>> actions = new ArrayList<>();

        actions.add(action(
                "ai_explain",
                "请 AI 讲解「" + kcName + "」",
                "AI 会结合你的掌握度用你能听懂的话再讲一遍"
        ));

        long submits = toLong(submissionCount);
        long accepts = toLong(acceptedCount);
        if (submits > 0 && accepts < submits) {
            actions.add(action(
                    "review_notebook",
                    "翻看相关错题",
                    "回错题本对一下你上次在这个知识点踩的坑"
            ));
        }

        long probs = toLong(problemCount);
        if (mastery < VERY_LOW_MASTERY && probs > 0) {
            actions.add(action(
                    "beginner_problems",
                    "从基础题入手",
                    "掌握度偏低，建议先练几道标签为基础的题目再回来"
            ));
        }

        return actions;
    }

    private static Map<String, Object> action(String key, String label, String hint) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("hint", hint);
        return item;
    }

    private static long toLong(Long value) {
        return value == null ? 0L : value;
    }
}
