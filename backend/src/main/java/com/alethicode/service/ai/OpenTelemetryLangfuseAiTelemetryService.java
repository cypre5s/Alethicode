package com.alethicode.service.ai;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.springframework.beans.factory.DisposableBean;

import java.util.Map;

public class OpenTelemetryLangfuseAiTelemetryService implements AiTelemetryService, DisposableBean {

    private final Tracer tracer;
    private final String environment;
    private final SdkTracerProvider tracerProvider;

    public OpenTelemetryLangfuseAiTelemetryService(OpenTelemetry openTelemetry, String environment) {
        this(openTelemetry, environment, null);
    }

    public OpenTelemetryLangfuseAiTelemetryService(
            OpenTelemetry openTelemetry,
            String environment,
            SdkTracerProvider tracerProvider
    ) {
        this.tracer = openTelemetry.getTracer("alethicode-java-ai");
        this.environment = environment == null || environment.isBlank() ? "production" : environment.strip();
        this.tracerProvider = tracerProvider;
    }

    @Override
    public AiTelemetrySpan start(AiTelemetryRequest request) {
        String spanName = String.valueOf(request.attributes().getOrDefault("langfuse.trace.name", "ai.call"));
        Span span = tracer.spanBuilder(spanName).startSpan();
        span.setAttribute("langfuse.environment", environment);
        for (Map.Entry<String, Object> entry : request.attributes().entrySet()) {
            setAttribute(span, entry.getKey(), entry.getValue());
        }
        return new AiTelemetrySpan() {
            @Override
            public void recordResponseLength(int responseLength) {
                span.setAttribute("langfuse.observation.metadata.response_length", Math.max(responseLength, 0));
            }

            @Override
            public void recordError(Throwable throwable) {
                span.setStatus(StatusCode.ERROR, throwable == null ? "AI call failed" : throwable.getMessage());
                span.setAttribute("langfuse.observation.level", "ERROR");
                if (throwable != null) {
                    span.recordException(throwable);
                }
            }

            @Override
            public void close() {
                span.end();
            }
        };
    }

    @Override
    public void destroy() {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }

    private void setAttribute(Span span, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean boolValue) {
            span.setAttribute(AttributeKey.booleanKey(key), boolValue);
            return;
        }
        if (value instanceof Integer intValue) {
            span.setAttribute(AttributeKey.longKey(key), intValue.longValue());
            return;
        }
        if (value instanceof Long longValue) {
            span.setAttribute(AttributeKey.longKey(key), longValue);
            return;
        }
        if (value instanceof Number numberValue) {
            span.setAttribute(AttributeKey.doubleKey(key), numberValue.doubleValue());
            return;
        }
        span.setAttribute(key, String.valueOf(value));
    }
}
