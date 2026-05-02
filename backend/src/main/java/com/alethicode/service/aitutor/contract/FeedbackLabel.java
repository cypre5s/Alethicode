package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum FeedbackLabel {
    HELPFUL("helpful"),
    UNHELPFUL("unhelpful"),
    CONFUSING("confusing");

    private final String value;

    FeedbackLabel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<FeedbackLabel> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.value.equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
