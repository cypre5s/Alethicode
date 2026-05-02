package com.alethicode.service.languagepack;

public record ProblemPackageWriteResult(
        Long problemId,
        String displayId,
        String testCaseId
) {
}
