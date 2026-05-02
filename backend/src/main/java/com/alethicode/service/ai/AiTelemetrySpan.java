package com.alethicode.service.ai;

public interface AiTelemetrySpan extends AutoCloseable {

    void recordResponseLength(int responseLength);

    void recordError(Throwable throwable);

    @Override
    void close();
}
