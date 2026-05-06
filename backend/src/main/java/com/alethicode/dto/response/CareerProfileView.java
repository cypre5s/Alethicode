package com.alethicode.dto.response;

import java.time.Instant;

/**
 * GET /api/career/profile 的响应体——学生当前 Career Bridging 档案视图。
 *
 * <p>{@code majorCode} 为 null 表示学生还未填写专业；前端据此在主页展示
 * 「填写你的专业」CTA。{@code careerProfileCompletedAt} 是首次填专业的时间，
 * 之后再次修改专业时不会被重置（语义：完成档案的时间戳）。
 */
public record CareerProfileView(
        String majorCode,
        String majorNameZh,
        String careerIntent,
        Instant careerProfileCompletedAt
) {
}
