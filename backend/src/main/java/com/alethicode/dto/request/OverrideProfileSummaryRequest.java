package com.alethicode.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 学生手动改写学习摘要的请求。
 *
 * 文本长度 ≤ 500 字（与 LearnerNarrativeSummaryService.MAX_SUMMARY_CHARS 保持一致）。
 */
public record OverrideProfileSummaryRequest(
        @NotBlank
        @Size(max = 500)
        String summaryText
) {
}
