package com.alethicode.dto.request;

public record CreateSubmissionRequest(
        Long problemId,
        String classroomSessionId,
        String language,
        String code,
        String captcha,
        String input,
        String objectiveAnswer,
        Object objectiveBlanks,
        String preflightDetector,
        String preflightMisconceptionId,
        Boolean preflightOverridden,
        String preflightQuestion,
        Integer preflightLineNumber,
        String preflightCodeSnippet
) {
}
