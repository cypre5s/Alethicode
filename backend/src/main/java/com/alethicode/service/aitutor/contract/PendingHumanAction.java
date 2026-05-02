package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum PendingHumanAction {
    NONE(""),
    CONFIRM_TRANSFER("confirm_transfer"),
    CONFIRM_MEMORY_SAVE("confirm_memory_save"),
    CONFIRM_HIGH_RISK_TOOL_USE("confirm_high_risk_tool_use"),
    CONFIRM_RETRIEVAL_OVERRIDE("confirm_retrieval_override");

    private final String value;

    PendingHumanAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<PendingHumanAction> from(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        return Arrays.stream(values())
                .filter(item -> item.value.equals(normalized))
                .findFirst();
    }
}
