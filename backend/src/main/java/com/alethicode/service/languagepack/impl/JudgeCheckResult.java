package com.alethicode.service.languagepack.impl;

import java.util.List;

public record JudgeCheckResult(
        boolean allPassed,
        List<CaseResult> caseResults,
        String compileError
) {

    public record CaseResult(
            int index,
            boolean passed,
            String actualOutput,
            String error,
            int resultCode
    ) {}

    public List<Integer> failedIndices() {
        return caseResults.stream()
                .filter(c -> !c.passed())
                .map(CaseResult::index)
                .toList();
    }

    public static JudgeCheckResult compileFailure(String compileError) {
        return new JudgeCheckResult(false, List.of(), compileError == null ? "" : compileError);
    }
}
