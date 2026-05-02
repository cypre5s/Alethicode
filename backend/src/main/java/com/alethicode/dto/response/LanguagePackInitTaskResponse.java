package com.alethicode.dto.response;

import java.time.Instant;

public record LanguagePackInitTaskResponse(
        Long id,
        Long languagePackId,
        String stage,
        String activeStepKey,
        String activeStatus,
        String activeMessage,
        Integer progressCurrent,
        Integer progressTotal,
        Boolean enableObjectiveQuestions,
        String failureReason,
        Instant createTime,
        Instant updateTime,
        LanguagePackSummary languagePack
) {

    public record LanguagePackSummary(
            Long id,
            String slug,
            Integer version,
            String name,
            String primaryLanguage,
            String status,
            Integer documentCount,
            Integer pageCount,
            Integer chapterCount,
            Integer kcCount,
            Integer exampleCount,
            Integer problemCount,
            Long creatorId
    ) {
    }
}
