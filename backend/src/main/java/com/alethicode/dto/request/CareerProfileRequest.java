package com.alethicode.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Career Bridging「写专业 + 学习目标」入参（plan 3.5 节 PUT /api/career/profile）。
 *
 * <p>Jackson 全局 SNAKE_CASE 配置使 {@code majorCode}/{@code careerIntent}/
 * {@code autoGenerate} 自动映射到 JSON {@code major_code}/{@code career_intent}/
 * {@code auto_generate}。{@code autoGenerate} 默认 true：注册首次填专业时
 * 由后端立即触发一次 LLM 生成（A/B treatment 组才真生成；control 组只消费
 * milestone）；前端已经准备好独立 loading 时可设 false 以拆成两步走。
 */
public record CareerProfileRequest(
        @NotBlank(message = "major_code 不能为空")
        @Size(max = 64, message = "major_code 长度不超过 64")
        String majorCode,

        @Size(max = 2000, message = "career_intent 长度不超过 2000")
        String careerIntent,

        Boolean autoGenerate
) {

    public boolean shouldAutoGenerate() {
        return autoGenerate == null || autoGenerate;
    }
}
