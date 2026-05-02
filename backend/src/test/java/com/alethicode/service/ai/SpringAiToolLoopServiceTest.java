package com.alethicode.service.ai;

import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiToolLoopServiceTest {

    private ObjectMapper objectMapper;
    private AiResponseNormalizer normalizer;
    private ChatModel chatModel;
    private SpringAiToolLoopService service;

    private final AiModelProfile profile = new AiModelProfile(
            "", "test-key", "https://test.example", "test-model", 30, 2);
    private final StoppingCondition stoppingCondition = new StoppingCondition(5, 3, 3, 60);
    private final ToolDefinition echoTool = new ToolDefinition(
            "echo",
            "Echo message",
            Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string")))
    );

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        normalizer = new AiResponseNormalizer(objectMapper);
        chatModel = mock(ChatModel.class);
        service = new SpringAiToolLoopService(chatModel, normalizer, objectMapper);
    }

    @Test
    void shouldAdvertiseToolCallbacksAndInternalExecutionDisabled() {
        // Model returns a final JSON object immediately (no tool call) — we only want to
        // inspect the options passed into the very first call.
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("{\"ok\":true}"));

        Map<String, ToolExecutor> executors = Map.of("echo", args -> Map.of("echo", args.get("message")));
        ReactResult result = service.execute(
                "system", List.of(Map.of("role", "user", "content", "hi")),
                List.of(echoTool), executors, 3, null, stoppingCondition, profile);

        assertThat(result.result()).containsEntry("ok", true);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt sent = captor.getValue();
        assertThat(sent.getOptions()).isInstanceOf(OpenAiChatOptions.class);
        OpenAiChatOptions opts = (OpenAiChatOptions) sent.getOptions();
        assertThat(opts.getToolCallbacks())
                .as("tool definitions must be advertised to the model")
                .hasSize(1);
        assertThat(opts.getToolCallbacks().getFirst().getToolDefinition().name()).isEqualTo("echo");
        assertThat(opts.getInternalToolExecutionEnabled())
                .as("Spring AI must not auto-execute user-controlled tools")
                .isFalse();
        assertThat(opts.getModel()).isEqualTo("test-model");
    }

    @Test
    void shouldExecuteToolAndFeedResultBackToModel() {
        // Round 1: model asks to call echo
        // Round 2: model returns final JSON
        AtomicInteger round = new AtomicInteger(0);
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            int r = round.incrementAndGet();
            if (r == 1) {
                return toolCallResponse("call_1", "echo", "{\"message\":\"hello\"}");
            }
            return finalResponse("{\"tool_seen\":true}");
        });

        AtomicInteger executorCalls = new AtomicInteger(0);
        Map<String, ToolExecutor> executors = Map.of("echo", args -> {
            executorCalls.incrementAndGet();
            return Map.of("echo", args.get("message"));
        });

        ReactResult result = service.execute(
                "system", List.of(Map.of("role", "user", "content", "go")),
                List.of(echoTool), executors, 3, null, stoppingCondition, profile);

        assertThat(result.result()).containsEntry("tool_seen", true);
        assertThat(result.iterationsUsed()).isEqualTo(2);
        assertThat(result.toolTraceEntries()).hasSize(1);
        assertThat(result.toolTraceEntries().getFirst().toolName()).isEqualTo("echo");
        assertThat(result.toolTraceEntries().getFirst().guardPassed()).isTrue();
        assertThat(executorCalls).hasValue(1);
    }

    @Test
    void shouldFailFastOnUnknownTool() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("call_1", "nonexistent", "{}"));

        assertThatThrownBy(() -> service.execute(
                "system", List.of(Map.of("role", "user", "content", "x")),
                List.of(echoTool), Map.of("echo", args -> Map.of()),
                3, null, stoppingCondition, profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown tool in ReAct loop: nonexistent");
    }

    @Test
    void shouldFailFastOnRepeatedToolBeyondLimit() {
        // Always return same tool call — will hit repeat limit of 3
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("c1", "echo", "{\"message\":\"a\"}"));

        Map<String, ToolExecutor> executors = Map.of("echo", args -> Map.of("echo", "a"));
        StoppingCondition tightSc = new StoppingCondition(10, 2, 3, 60);

        assertThatThrownBy(() -> service.execute(
                "system", List.of(Map.of("role", "user", "content", "x")),
                List.of(echoTool), executors, 10, null, tightSc, profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeding limit 2");
    }

    @Test
    void shouldFailFastWhenFinalContentIsNotJson() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("not a json at all"));

        assertThatThrownBy(() -> service.execute(
                "system", List.of(Map.of("role", "user", "content", "x")),
                List.of(echoTool), Map.of("echo", args -> Map.of()),
                3, null, stoppingCondition, profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("final content is not valid JSON");
    }

    @Test
    void shouldFailFastOnEmptyToolList() {
        assertThatThrownBy(() -> service.execute(
                "system", List.of(Map.of("role", "user", "content", "x")),
                List.of(), Map.of(), 3, null, stoppingCondition, profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one tool definition");
    }

    @Test
    void shouldApplyToolChoiceWhenSpecified() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalResponse("{\"ok\":true}"));

        service.execute(
                "system", List.of(Map.of("role", "user", "content", "x")),
                List.of(echoTool), Map.of("echo", args -> Map.of()),
                3, null, stoppingCondition, profile, "required");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        OpenAiChatOptions opts = (OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(opts.getToolChoice()).isEqualTo("required");
    }

    private static ChatResponse finalResponse(String content) {
        AssistantMessage msg = new AssistantMessage(content);
        Generation gen = new Generation(msg);
        return new ChatResponse(List.of(gen));
    }

    private static ChatResponse toolCallResponse(String callId, String name, String argsJson) {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(callId, "function", name, argsJson);
        AssistantMessage msg = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        Generation gen = new Generation(msg);
        return new ChatResponse(List.of(gen));
    }
}
