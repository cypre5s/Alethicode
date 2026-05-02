package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum RecoveryReason {
    PROCESS_RESTART,
    APPROVAL_RESUME,
    USER_CONTINUE,
    ADMIN_RESTORE,
    CHECKPOINT_RESTORE;

    public static Optional<RecoveryReason> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
