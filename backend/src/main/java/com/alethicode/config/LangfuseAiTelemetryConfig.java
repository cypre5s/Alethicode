package com.alethicode.config;

import com.alethicode.service.ai.AiTelemetryService;
import com.alethicode.service.ai.OpenTelemetryLangfuseAiTelemetryService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Configuration
public class LangfuseAiTelemetryConfig {

    @Bean
    @ConditionalOnMissingBean(AiTelemetryService.class)
    public AiTelemetryService aiTelemetryService(Environment environment) {
        String publicKey = read(environment, "LANGFUSE_PUBLIC_KEY", "alethicode.ai.langfuse.public-key");
        String secretKey = read(environment, "LANGFUSE_SECRET_KEY", "alethicode.ai.langfuse.secret-key");
        String baseUrl = read(environment, "LANGFUSE_BASE_URL", "alethicode.ai.langfuse.base-url");
        String tracingEnvironment = readOrDefault(
                environment,
                "production",
                "LANGFUSE_TRACING_ENVIRONMENT",
                "alethicode.ai.langfuse.tracing-environment"
        );

        boolean anyConfigured = !publicKey.isBlank() || !secretKey.isBlank() || !baseUrl.isBlank();
        boolean fullyConfigured = !publicKey.isBlank() && !secretKey.isBlank() && !baseUrl.isBlank();
        if (!anyConfigured) {
            return AiTelemetryService.noop();
        }
        if (!fullyConfigured) {
            throw new IllegalStateException("LANGFUSE_PUBLIC_KEY, LANGFUSE_SECRET_KEY and LANGFUSE_BASE_URL must be configured together");
        }

        String endpoint = baseUrl.strip().replaceAll("/+$", "") + "/api/public/otel/v1/traces";
        String auth = Base64.getEncoder().encodeToString((publicKey.strip() + ":" + secretKey.strip())
                .getBytes(StandardCharsets.UTF_8));
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("Authorization", "Basic " + auth)
                .addHeader("x-langfuse-ingestion-version", "4")
                .setTimeout(Duration.ofSeconds(10))
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        return new OpenTelemetryLangfuseAiTelemetryService(openTelemetry, tracingEnvironment, tracerProvider);
    }

    private String read(Environment environment, String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String readOrDefault(Environment environment, String fallback, String... keys) {
        String value = read(environment, keys);
        return value.isBlank() ? fallback : value;
    }
}
