package com.alethicode.service.career.lens;

import java.time.Instant;
import java.util.Map;

/**
 * 题面专业化变体投影（V85 表 problem_domain_variant 一行）。
 */
public record ProblemDomainVariant(
        long id,
        long problemId,
        String majorCode,
        String title,
        String descriptionMd,
        String sampleInputText,
        String sampleOutputText,
        Map<String, Object> domainMetaphor,
        Double semanticDriftScore,
        boolean reflectionPassed,
        boolean lockedForExam,
        Instant generatedAt,
        Long validatedBy
) {
}
