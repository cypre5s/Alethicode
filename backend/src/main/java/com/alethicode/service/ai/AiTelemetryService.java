package com.alethicode.service.ai;

public interface AiTelemetryService {

    AiTelemetrySpan start(AiTelemetryRequest request);

    static AiTelemetryService noop() {
        return request -> new AiTelemetrySpan() {
            @Override
            public void recordResponseLength(int responseLength) {
            }

            @Override
            public void recordError(Throwable throwable) {
            }

            @Override
            public void close() {
            }
        };
    }
}
