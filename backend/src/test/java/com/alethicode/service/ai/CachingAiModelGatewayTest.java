package com.alethicode.service.ai;

import com.alethicode.service.aitutor.LlmResponseCacheService;
import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CachingAiModelGatewayTest {

    @Test
    void callForJsonCachedShouldRecordCacheMissAndCacheHitWithoutRawPrompt() {
        TestSpringGateway delegate = mock(TestSpringGateway.class);
        RecordingAiTelemetryService telemetryService = new RecordingAiTelemetryService();
        CachingAiModelGateway gateway = new CachingAiModelGateway(delegate, telemetryService);
        gateway.setCacheService(new LlmResponseCacheService());
        when(delegate.callForJson("sys", "user", "INIT_LLM_")).thenReturn(Map.of("ok", true));

        assertThat(gateway.callForJsonCached("cache-key", "sys", "user", "INIT_LLM_"))
                .containsEntry("ok", true);
        assertThat(gateway.callForJsonCached("cache-key", "sys", "user", "INIT_LLM_"))
                .containsEntry("ok", true);

        assertThat(telemetryService.observations()).hasSize(2);
        assertThat(telemetryService.observations().get(0).attributes())
                .containsEntry("langfuse.trace.name", "ai.cache.callForJsonCached")
                .containsEntry("langfuse.observation.metadata.cache_hit", false)
                .containsEntry("langfuse.observation.metadata.cache_key_hash", AiTelemetrySupport.sha256("cache-key"))
                .containsEntry("langfuse.observation.metadata.profile_prefix", "INIT_LLM_");
        assertThat(telemetryService.observations().get(1).attributes())
                .containsEntry("langfuse.observation.metadata.cache_hit", true);
        assertThat(telemetryService.observations().get(1).attributes())
                .doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output");
        assertThat(telemetryService.observations().get(1).attributes().values())
                .doesNotContain("sys", "user", "cache-key");
    }

    private abstract static class TestSpringGateway extends SpringAiModelGateway {
        TestSpringGateway() {
            super(null, null, null, null, null, AiTelemetryService.noop());
        }
    }

    private static final class RecordingAiTelemetryService implements AiTelemetryService {
        private final List<Observation> observations = new ArrayList<>();

        @Override
        public AiTelemetrySpan start(AiTelemetryRequest request) {
            Observation observation = new Observation(new LinkedHashMap<>(request.attributes()));
            observations.add(observation);
            return new AiTelemetrySpan() {
                @Override
                public void recordResponseLength(int responseLength) {
                    observation.attributes().put("langfuse.observation.metadata.response_length", responseLength);
                }

                @Override
                public void recordError(Throwable throwable) {
                    observation.attributes().put("langfuse.observation.level", "ERROR");
                }

                @Override
                public void close() {
                }
            };
        }

        List<Observation> observations() {
            return observations;
        }

        private record Observation(Map<String, Object> attributes) {
        }
    }
}
