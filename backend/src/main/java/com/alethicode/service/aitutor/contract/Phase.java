package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum Phase {
    READING,
    IDEATING,
    CODING,
    ERROR_FEEDBACK,
    AC_REVIEW,
    TRANSFER;

    public static Optional<Phase> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
