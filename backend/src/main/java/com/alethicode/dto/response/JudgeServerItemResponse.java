package com.alethicode.dto.response;

import java.time.Instant;

public record JudgeServerItemResponse(
        Long id,
        String hostname,
        String ip,
        String judgerVersion,
        Integer cpuCore,
        Double memoryUsage,
        Double cpuUsage,
        Instant lastHeartbeat,
        Instant createTime,
        Integer taskNumber,
        String serviceUrl,
        boolean isDisabled,
        String status
) {
}
