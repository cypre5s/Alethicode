package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum RuntimeState {
    QUEUED,
    RUNNING,
    WAITING_TOOL,
    WAITING_HUMAN_APPROVAL,
    INTERRUPTED,
    RESTORING,
    FAILED,
    COMPLETED,
    EXPIRED;

    private static final Set<RuntimeState> TERMINAL = Set.of(FAILED, COMPLETED, EXPIRED);
    private static final Set<RuntimeState> ACTIVE = Set.of(RUNNING, WAITING_TOOL, WAITING_HUMAN_APPROVAL);

    public boolean terminal() {
        return TERMINAL.contains(this);
    }

    public boolean active() {
        return ACTIVE.contains(this);
    }

    public static Optional<RuntimeState> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
