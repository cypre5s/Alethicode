package com.alethicode.dto.response;

/**
 * 专业字典下拉项。
 * GET /api/career/majors 返回 {@code List<CareerMajorOption>}，仅暴露
 * 前端需要的 4 个字段，不暴露 seed_keywords / seed_use_cases / seed_kcs
 * 等内部 evidence（这些只供 LLM 生成 Why 报告时拼装 prompt 使用）。
 */
public record CareerMajorOption(
        String code,
        String nameZh,
        String nameEn,
        String discipline
) {
}
