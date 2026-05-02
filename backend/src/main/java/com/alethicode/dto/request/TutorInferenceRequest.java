package com.alethicode.dto.request;

public record TutorInferenceRequest(
        Long problemId,
        String submissionId,
        String language,
        String codeSnippet,
        String compilerOutput,
        Long assignmentId,
        Long classroomId
) {
}
