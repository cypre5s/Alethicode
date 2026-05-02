package com.alethicode.dto.request.monitor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record JudgeHeartbeatV2Request(
        @NotBlank String hostname,
        @NotBlank String judgerVersion,
        @NotBlank String serviceUrl,
        @NotBlank @Pattern(regexp = "heartbeat") String action,
        @NotNull @Valid NodeInfo nodeInfo,
        @NotNull @Valid HostMetrics hostMetrics,
        @NotNull @Valid RuntimeMetrics runtimeMetrics,
        @Valid TaskMetrics taskMetrics,
        @Valid SecurityMetrics securityMetrics,
        List<@Valid EventPayload> events
) {
    public record NodeInfo(
            @NotNull Integer cpuCore,
            @NotNull Long memoryTotalBytes,
            @NotNull Long filesystemTotalBytes,
            String agentVersion
    ) {}

    public record HostMetrics(
            @NotNull Double cpuUsageRatio,
            @NotNull Double cpuLoad1,
            @NotNull Double cpuLoad5,
            @NotNull Double cpuLoad15,
            @NotNull Double cpuIowaitRatio,
            @NotNull Long memoryAvailableBytes,
            @NotNull Double memoryUsageRatio,
            @NotNull Long swapTotalBytes,
            @NotNull Long swapUsedBytes,
            @NotNull Double swapUsageRatio,
            @NotNull Long filesystemAvailableBytes,
            @NotNull Double filesystemUsageRatio,
            @NotNull Double filesystemInodeUsageRatio,
            @NotNull Double diskReadBytesPerSecond,
            @NotNull Double diskWriteBytesPerSecond,
            @NotNull Double diskReadIops,
            @NotNull Double diskWriteIops,
            @NotNull Double diskAwaitSeconds,
            @NotNull Double networkReceiveBytesPerSecond,
            @NotNull Double networkTransmitBytesPerSecond,
            @NotNull Double networkReceiveDropPerSecond,
            @NotNull Double networkTransmitDropPerSecond,
            Double pressureCpuWaitingRatio,
            Double pressureMemoryWaitingRatio,
            Double pressureIoWaitingRatio
    ) {}

    public record RuntimeMetrics(
            @NotNull Integer runningTasks,
            @NotNull Integer queuedTasks,
            @NotNull Integer availableSlots,
            Integer compileInProgress,
            Integer runInProgress,
            Integer spjInProgress,
            Integer cleanupInProgress
    ) {}

    public record TaskMetrics(
            Long tasksCompletedTotal,
            Double tasksCompletedPerMinute,
            Double queueWaitP50Seconds,
            Double queueWaitP95Seconds,
            Double queueWaitP99Seconds,
            Double compileP50Seconds,
            Double compileP95Seconds,
            Double runP50Seconds,
            Double runP95Seconds,
            Double endToEndP50Seconds,
            Double endToEndP95Seconds,
            Double endToEndP99Seconds,
            Double systemErrorRatio,
            Double timeoutRatio,
            Long memoryPeakP95Bytes,
            List<ResultCount> resultCounts,
            List<LanguageCount> languageCounts,
            List<LanguageLatencyItem> languageLatencyBreakdown
    ) {}

    public record ResultCount(String result, Long count) {}

    public record LanguageCount(String language, Long count) {}

    public record LanguageLatencyItem(
            String language,
            Double queueP95Seconds,
            Double compileP95Seconds,
            Double runP95Seconds
    ) {}

    public record SecurityMetrics(
            Double cgroupCpuUsageRatio,
            Double cgroupCpuThrottledRatio,
            Long cgroupMemoryWorkingSetBytes,
            Long cgroupMemoryRssBytes,
            Long cgroupMemoryCacheBytes,
            Integer cgroupPidsCurrent,
            Integer cgroupPidsLimit,
            Long cgroupOomTotal,
            Double cgroupFsReadsBytesPerSecond,
            Double cgroupFsWritesBytesPerSecond,
            Long heartbeatRejectTotal,
            Long authFailureTotal,
            Long restartTotal,
            Long seccompViolationTotal,
            Long outputLimitExceededTotal,
            Long cleanupFailureTotal,
            Integer workspaceLeakCount,
            Long workspaceUsageBytes
    ) {}

    public record EventPayload(
            @NotBlank String eventType,
            @NotBlank String severity,
            @NotBlank String occurredAt,
            String message,
            String detailsJson,
            String dedupKey
    ) {}
}
