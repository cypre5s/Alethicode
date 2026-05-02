package com.alethicode.service.usagestats;

import java.util.Map;

/**
 * 公测期使用统计聚合服务。
 *
 * <p>四个维度：
 * <ul>
 *   <li>{@code overview} - 注册数 / 活跃数 / 提交数 / AC 率 / 首次 AC 率 / 平均到 AC 时长</li>
 *   <li>{@code daily_active} - 每日活跃曲线（含提交数、AI 调用数）</li>
 *   <li>{@code ai_value} - AI 调用次数 / 学生覆盖率 / 卡片类型分布 / 错误诊断 hit 率</li>
 *   <li>{@code pain_points} - 高 WA 题目排行 / 高重试题目 / 反馈聚类</li>
 * </ul>
 *
 * <p>所有查询都基于已有表（submission / ai_tutor_workflow_session / ai_tutor_workflow_event /
 * language_pack_chat_session / beta_feedback_report），不依赖丢失的 page_view / feature_click 事件。
 */
public interface UsageStatsService {

    /**
     * @param range 统计窗口：{@code today} / {@code 7d} / {@code 30d}（其它值按 7d 处理）
     * @return 聚合统计结果，键见类注释
     */
    Map<String, Object> getStats(String range);
}
