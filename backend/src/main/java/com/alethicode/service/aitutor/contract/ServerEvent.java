package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum ServerEvent {
    TASK_QUEUED,
    TASK_STARTED,
    TASK_PROGRESS,
    TOOL_CALL_STARTED,
    TOOL_CALL_COMPLETED,
    CARD_GENERATED,
    APPROVAL_REQUESTED,
    APPROVAL_RESOLVED,
    TASK_INTERRUPTED,
    TASK_RESTORING,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_EXPIRED;

    public static Optional<ServerEvent> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
