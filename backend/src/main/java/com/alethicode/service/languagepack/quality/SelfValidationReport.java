package com.alethicode.service.languagepack.quality;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Reference solution self-validation 综合报告：
 * lint 通过后才会跑 judge；judge 全部 case + samples 通过即 {@code allPassed=true}。
 *
 * <p>{@code failureSummary} 给重试 prompt / 人工 escalation 用，
 * 浓缩前 N 个失败 case 的标签与 diff。</p>
 */
public record SelfValidationReport(
        String displayId,
        boolean allPassed,
        List<SelfValidationCaseResult> testCaseResults,
        List<SelfValidationSampleResult> sampleResults,
        ReferenceLintReport lintReport,
        Optional<String> failureSummary,
        Optional<String> compileError,
        Duration duration
) {

    public boolean lintBlocked() {
        return lintReport != null && !lintReport.passable();
    }

    public boolean compileFailed() {
        return compileError != null && compileError.isPresent();
    }
}
