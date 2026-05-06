package com.alethicode.service.career.bridging;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Career Bridging Why 报告投影。
 *
 * <p>对应 V84 表 {@code career_bridging_report} 一行；字段 1:1 映射列名（含
 * snake_case 列 → camelCase Java 字段，由 Spring Jackson 全局 SNAKE_CASE
 * 命名策略对外序列化时再翻译回 snake_case JSON）。
 *
 * <p>{@code citations} 是 LLM 输出 + critic 校验后的来源数组，每条形如
 * {@code {"source": "major_dictionary"|"learner_state"|"learning_pack",
 * "ref": <code-or-id>}}。前端渲染时按 source 切换不同样式。
 */
public record CareerBridgingReport(
        long id,
        long userId,
        Long milestoneId,
        String majorCode,
        String reportKind,
        String title,
        String contentMd,
        List<Map<String, Object>> citations,
        String rolloutMode,
        boolean reflectionPassed,
        String traceId,
        Instant createdAt
) {
}
