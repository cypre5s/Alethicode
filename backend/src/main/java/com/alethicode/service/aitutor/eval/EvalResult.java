package com.alethicode.service.aitutor.eval;

import java.util.Map;

/**
 * Result of a single LLM-as-Judge evaluation on one (evidence, card) pair.
 *
 * @param cardType        type of the evaluated card
 * @param overallScore    0.0-1.0 aggregate score
 * @param dimensionScores per-dimension scores (0.0-1.0)
 * @param verdict         free-text summary from the judge
 * @param flags           issue flags (e.g. "answer_leakage", "factual_error")
 */
public record EvalResult(
        String cardType,
        double overallScore,
        Map<EvalDimension, Double> dimensionScores,
        String verdict,
        java.util.List<String> flags
) {}
