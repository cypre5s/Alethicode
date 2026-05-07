package com.alethicode.service.aitutor.eval;

import java.util.Map;

/**
 * 单条证据与卡片组合的 LLM-as-Judge 评估结果。
 *
 * @param cardType 被评估卡片类型
 * @param overallScore 0.0-1.0 综合分
 * @param dimensionScores 各维度 0.0-1.0 分数
 * @param verdict 裁判模型的一句话结论
 * @param flags 问题标记，如 {@code answer_leakage}、{@code factual_error}
 */
public record EvalResult(
        String cardType,
        double overallScore,
        Map<EvalDimension, Double> dimensionScores,
        String verdict,
        java.util.List<String> flags
) {}
