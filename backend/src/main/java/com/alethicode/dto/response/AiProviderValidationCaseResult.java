package com.alethicode.dto.response;

import java.util.Map;

/**
 * 单个 AI Provider 验证用例结果。
 *
 * {@code shapeMatched} 是判定是否通过的权威信号；{@code summary} 只允许包含脱敏摘要。
 */
public record AiProviderValidationCaseResult(
        String caseName,
        boolean passed,
        boolean shapeMatched,
        String failureMessage,
        Map<String, Object> summary
) {
}
