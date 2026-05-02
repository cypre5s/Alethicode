package com.alethicode.service.ai;

import java.util.function.Supplier;

public final class AiTelemetryContextHolder {

    private static final ThreadLocal<AiTelemetryContext> CURRENT = new ThreadLocal<>();

    private AiTelemetryContextHolder() {
    }

    public static AiTelemetryContext current() {
        return CURRENT.get();
    }

    public static <T> T withContext(AiTelemetryContext context, Supplier<T> supplier) {
        AiTelemetryContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
