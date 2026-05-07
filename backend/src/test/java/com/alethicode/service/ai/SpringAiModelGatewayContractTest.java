package com.alethicode.service.ai;

import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiModelGatewayContractTest {

    private ChatModel chatModel;
    private AiModelProfileResolver profileResolver;
    private SpringAiModelGateway gateway;
    private RecordingAiTelemetryService telemetryService;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        AiResponseNormalizer normalizer = new AiResponseNormalizer(om);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        AiCircuitBreaker cb = new AiCircuitBreaker(
                CircuitBreakerRegistry.of(
                        CircuitBreakerConfig.custom()
                                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                .slidingWindowSize(10)
                                .minimumNumberOfCalls(2)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(java.time.Duration.ofMillis(100))
                                .build()
                ),
                RetryRegistry.of(
                        RetryConfig.custom()
                                .maxAttempts(2)
                                .waitDuration(java.time.Duration.ofMillis(5))
                                .retryExceptions(RuntimeException.class)
                                .build()
                ),
                BulkheadRegistry.of(
                        BulkheadConfig.custom()
                                .maxConcurrentCalls(4)
                                .maxWaitDuration(java.time.Duration.ZERO)
                                .build()
                ),
                TimeLimiterRegistry.of(
                        TimeLimiterConfig.custom()
                                .timeoutDuration(java.time.Duration.ofSeconds(2))
                                .cancelRunningFuture(true)
                                .build()
                ),
                scheduler
        );
        chatModel = mock(ChatModel.class);
        profileResolver = mock(AiModelProfileResolver.class);

        when(profileResolver.resolveChat(null)).thenReturn(
                new AiModelProfile("", "default-key", "https://d.example", "default-model", 30, 2));
        when(profileResolver.resolveChat("")).thenReturn(
                new AiModelProfile("", "default-key", "https://d.example", "default-model", 30, 2));
        when(profileResolver.resolveChat("INIT_LLM_")).thenReturn(
                new AiModelProfile("INIT_LLM_", "init-key", "https://init.example", "init-model", 30, 2));

        SpringAiToolLoopService toolLoop = new SpringAiToolLoopService(chatModel, normalizer, om);
        telemetryService = new RecordingAiTelemetryService();
        gateway = new SpringAiModelGateway(chatModel, normalizer, profileResolver, cb, toolLoop, telemetryService);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void callForJsonShouldForceJsonResponseFormatAndUseDefaultModel() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(finalResponse("{\"status\":\"ok\"}"));

        Map<String, Object> result = gateway.callForJson("sys", "user");
        assertThat(result).containsEntry("status", "ok");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        OpenAiChatOptions opts = (OpenAiChatOptions) prompt.getOptions();
        assertThat(opts.getResponseFormat()).isNotNull();
        assertThat(opts.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        assertThat(opts.getModel()).isEqualTo("default-model");

        List<Message> messages = prompt.getInstructions();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(telemetryService.observations()).hasSize(1);
        RecordingAiTelemetryService.Observation observation = telemetryService.observations().getFirst();
        assertThat(observation.attributes())
                .containsEntry("langfuse.trace.name", "ai.callForJson")
                .containsEntry("langfuse.observation.type", "generation")
                .containsEntry("langfuse.observation.model.name", "default-model")
                .containsEntry("langfuse.observation.metadata.service", "java-ai")
                .containsEntry("langfuse.observation.metadata.scene", "callForJson")
                .containsEntry("langfuse.observation.metadata.profile_prefix", "default")
                .containsEntry("langfuse.observation.metadata.prompt_length", 7)
                .containsEntry("langfuse.observation.metadata.response_length", 15);
        assertThat(observation.attributes()).containsKey("langfuse.observation.metadata.prompt_hash");
        assertThat(observation.attributes()).doesNotContainKeys(
                "langfuse.observation.input",
                "langfuse.observation.output",
                "gen_ai.prompt",
                "gen_ai.completion"
        );
        assertThat(observation.attributes().values()).doesNotContain("sys\nuser", "{\"status\":\"ok\"}");
    }

    @Test
    void callForJsonShouldHonorProfilePrefix() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("{\"ok\":true}"));

        gateway.callForJson("sys", "user", "INIT_LLM_");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        OpenAiChatOptions opts = (OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(opts.getModel())
                .as("profilePrefix must route to the profile-specific chat model")
                .isEqualTo("init-model");
    }

    @Test
    void callForJsonShouldFailFastOnBlankInputs() {
        assertThatThrownBy(() -> gateway.callForJson("", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("systemPrompt is required");
        assertThatThrownBy(() -> gateway.callForJson("sys", "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userPrompt is required");
    }

    @Test
    void callForJsonShouldFailFastWhenResponseNotJson() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("not json here"));

        assertThatThrownBy(() -> gateway.callForJson("sys", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid JSON object");
    }

    @Test
    void callForJsonShouldRetryTransientGatewayFailures() {
        AtomicInteger attempts = new AtomicInteger();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new RuntimeException("temporary upstream failure");
            }
            return finalResponse("{\"status\":\"ok\"}");
        });

        Map<String, Object> result = gateway.callForJson("sys", "user");

        assertThat(result).containsEntry("status", "ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void callForContentShouldReturnTrimmedText() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("  hello world  "));

        String result = gateway.callForContent("say hi");
        assertThat(result).isEqualTo("hello world");
        assertThat(telemetryService.last().attributes())
                .containsEntry("langfuse.trace.name", "ai.callForContent")
                .containsEntry("langfuse.observation.model.name", "default-model")
                .containsEntry("langfuse.observation.metadata.response_length", 11);
    }

    @Test
    void callForContentShouldFailFastOnBlankResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("   "));

        assertThatThrownBy(() -> gateway.callForContent("say hi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content is blank");
    }

    @Test
    void callForJsonCachedFallsThroughToDirectCallWhenInvokedOnGatewayDirectly() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("{\"k\":1}"));

        Map<String, Object> result = gateway.callForJsonCached("key", "sys", "user", null);
        assertThat(result).containsEntry("k", 1);
    }

    @Test
    void callWithToolsShouldRouteThroughToolLoopWithProfilePrefix() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("{\"done\":true}"));

        ReactResult result = gateway.callWithTools(
                "sys", List.of(Map.of("role", "user", "content", "go")),
                List.of(new ToolDefinition("echo", "echo", Map.of())),
                Map.of("echo", args -> Map.of()),
                3, null, StoppingCondition.defaults(), "INIT_LLM_");

        assertThat(result.result()).containsEntry("done", true);
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        OpenAiChatOptions opts = (OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(opts.getModel()).isEqualTo("init-model");
        assertThat(telemetryService.last().attributes())
                .containsEntry("langfuse.trace.name", "ai.callWithTools")
                .containsEntry("langfuse.observation.model.name", "init-model")
                .containsEntry("langfuse.observation.metadata.profile_prefix", "INIT_LLM_")
                .containsEntry("langfuse.observation.metadata.response_length", 11);
    }

    @Test
    void readRequiredConfigDelegatesToProfileResolver() {
        when(profileResolver.readRequired("CUSTOM_KEY")).thenReturn("value");
        assertThat(gateway.readRequiredConfig("CUSTOM_KEY")).isEqualTo("value");
    }

    @Test
    void readConfigOrDefaultDelegatesToProfileResolver() {
        when(profileResolver.readOrDefault("KEY", "def")).thenReturn("def");
        assertThat(gateway.readConfigOrDefault("KEY", "def")).isEqualTo("def");
    }

    private static ChatResponse finalResponse(String content) {
        AssistantMessage msg = new AssistantMessage(content);
        Generation gen = new Generation(msg);
        return new ChatResponse(new ArrayList<>(List.of(gen)));
    }

    private static final class RecordingAiTelemetryService implements AiTelemetryService {
        private final List<Observation> observations = new ArrayList<>();

        @Override
        public AiTelemetrySpan start(AiTelemetryRequest request) {
            Observation observation = new Observation(new java.util.LinkedHashMap<>(request.attributes()));
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

        Observation last() {
            return observations.getLast();
        }

        private record Observation(Map<String, Object> attributes) {
        }
    }
}
