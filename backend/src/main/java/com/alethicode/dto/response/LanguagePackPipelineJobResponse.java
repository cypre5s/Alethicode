package com.alethicode.dto.response;

import java.time.Instant;

public record LanguagePackPipelineJobResponse(
        String jobId,
        Long taskId,
        String workflowId,
        String runId,
        String status,
        String currentStep,
        Instant startTime,
        Instant closeTime
) {
}
