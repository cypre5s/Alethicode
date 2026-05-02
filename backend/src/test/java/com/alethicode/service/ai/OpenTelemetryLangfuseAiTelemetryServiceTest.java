package com.alethicode.service.ai;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryLangfuseAiTelemetryServiceTest {

    @Test
    void shouldExportLangfuseMetadataWithoutRawPromptOrResponse() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        OpenTelemetryLangfuseAiTelemetryService service =
                new OpenTelemetryLangfuseAiTelemetryService(openTelemetry, "staging");

        try (AiTelemetrySpan span = service.start(new AiTelemetryRequest(Map.of(
                "langfuse.trace.name", "ai.callForJson",
                "langfuse.observation.type", "generation",
                "langfuse.observation.model.name", "deepseek-chat",
                "langfuse.observation.metadata.service", "java-ai",
                "langfuse.observation.metadata.scene", "qa-harness",
                "langfuse.observation.metadata.profile_prefix", "QA_LLM_",
                "langfuse.observation.metadata.prompt_hash", AiTelemetrySupport.sha256("secret prompt"),
                "langfuse.observation.metadata.prompt_length", 13,
                "gen_ai.operation.name", "chat"
        )))) {
            span.recordResponseLength(42);
        }
        tracerProvider.forceFlush();

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        Map<String, Object> attributes = exporter.getFinishedSpanItems().getFirst().getAttributes().asMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().getKey(), Map.Entry::getValue));
        assertThat(attributes)
                .containsEntry("langfuse.trace.name", "ai.callForJson")
                .containsEntry("langfuse.environment", "staging")
                .containsEntry("langfuse.observation.type", "generation")
                .containsEntry("langfuse.observation.model.name", "deepseek-chat")
                .containsEntry("langfuse.observation.metadata.service", "java-ai")
                .containsEntry("langfuse.observation.metadata.scene", "qa-harness")
                .containsEntry("langfuse.observation.metadata.response_length", 42L)
                .containsEntry("gen_ai.operation.name", "chat");
        assertThat(attributes).doesNotContainKeys(
                "langfuse.observation.input",
                "langfuse.observation.output",
                "gen_ai.prompt",
                "gen_ai.completion"
        );
        assertThat(attributes.values()).doesNotContain("secret prompt", "secret response");
        tracerProvider.shutdown();
    }
}
