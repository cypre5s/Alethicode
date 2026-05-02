package com.alethicode.service.languagepack;

import java.util.List;
import java.util.Map;

public record ProblemPackageWriteOptions(
        Long createdById,
        boolean visible,
        boolean isPublic,
        boolean aiGenerated,
        boolean spj,
        String visibilityStatus,
        String source,
        Map<String, Object> statisticInfo,
        List<Map<String, Object>> testCaseScore,
        List<String> tags
) {
}
