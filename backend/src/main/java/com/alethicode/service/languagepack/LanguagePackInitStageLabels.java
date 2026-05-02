package com.alethicode.service.languagepack;

import java.util.Map;

/**
 * 课程包初始化任务阶段在日志与界面中的中文展示名（与 admin 端 STAGE_LABELS 语义对齐）。
 */
public final class LanguagePackInitStageLabels {

    private static final Map<String, String> ZH = Map.ofEntries(
            Map.entry("created", "已创建"),
            Map.entry("normalizing", "规范化中"),
            Map.entry("parsing", "解析中"),
            Map.entry("kc_extraction", "知识点提取"),
            Map.entry("kc_ready", "知识点就绪"),
            Map.entry("segments_ready", "分段就绪"),
            Map.entry("units_ready", "教学单元就绪"),
            Map.entry("oj_candidates_ready", "OJ 候选就绪"),
            Map.entry("problem_gen", "练习题生成中"),
            Map.entry("problem_packages_ready", "题包就绪"),
            Map.entry("problems_validated", "已验证"),
            Map.entry("published", "已发布"),
            Map.entry("failed", "失败")
    );

    private LanguagePackInitStageLabels() {
    }

    /**
     * @param stage 阶段键；空串返回空串；未知键原样返回便于排查
     */
    public static String labelZh(String stage) {
        if (stage == null || stage.isEmpty()) {
            return "";
        }
        return ZH.getOrDefault(stage, stage);
    }

    public static String formatTaskCreated() {
        return "任务已创建";
    }

    public static String formatTaskCreatedViaImport() {
        return "已通过课程包导入创建任务";
    }

    public static String formatAdvance(String fromStage, String toStage) {
        return "阶段推进：" + labelZh(fromStage) + " → " + labelZh(toStage);
    }

    public static String formatRestoreDefault(String fromStage, String toStage) {
        return "阶段已恢复：" + labelZh(fromStage) + " → " + labelZh(toStage);
    }
}
