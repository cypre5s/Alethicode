package com.alethicode.service.ai;

import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolExecutor;
import com.alethicode.service.aitutor.react.ToolTraceEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a user-controlled ReAct tool loop on top of Spring AI.
 *
 * <p>Tool definitions are advertised to the model via {@link OpenAiChatOptions#getToolCallbacks()}
 * while {@code internalToolExecutionEnabled=false} stops Spring AI from auto-executing anything.
 * We drive tool execution through project-native {@link ToolExecutor} instances so that
 * {@code ToolTraceEntry}, guards, and stopping conditions stay under our control.
 */
@Service
public class SpringAiToolLoopService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiToolLoopService.class);

    private final ChatModel chatModel;
    private final AiResponseNormalizer normalizer;
    private final ObjectMapper objectMapper;

    public SpringAiToolLoopService(ChatModel chatModel,
                                   AiResponseNormalizer normalizer,
                                   ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
    }

    public ReactResult execute(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<com.alethicode.service.aitutor.react.ToolDefinition> tools,
            Map<String, ToolExecutor> executors,
            int maxIterations,
            ToolContext toolContext,
            StoppingCondition stoppingCondition,
            AiModelProfile profile
    ) {
        return execute(systemPrompt, messages, tools, executors, maxIterations,
                toolContext, stoppingCondition, profile, "auto");
    }

    /**
     * Execute with an explicit {@code tool_choice} value (e.g. {@code "auto"}, {@code "required"},
     * {@code "none"}). Useful for validation contracts that must force a tool call.
     */
    public ReactResult execute(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<com.alethicode.service.aitutor.react.ToolDefinition> tools,
            Map<String, ToolExecutor> executors,
            int maxIterations,
            ToolContext toolContext,
            StoppingCondition stoppingCondition,
            AiModelProfile profile,
            String toolChoice
    ) {
        if (tools == null || tools.isEmpty()) {
            throw new IllegalStateException("callWithTools requires at least one tool definition");
        }
        if (executors == null) {
            throw new IllegalStateException("callWithTools requires tool executors");
        }

        Map<String, com.alethicode.service.aitutor.react.ToolDefinition> toolDefsByName = new LinkedHashMap<>();
        for (var td : tools) {
            toolDefsByName.put(td.name(), td);
        }

        List<ToolCallback> springCallbacks = new ArrayList<>(tools.size());
        for (var td : tools) {
            String inputSchema;
            try {
                inputSchema = objectMapper.writeValueAsString(td.parameters());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize tool parameters for '" + td.name() + "'", e);
            }
            ToolDefinition def = DefaultToolDefinition.builder()
                    .name(td.name())
                    .description(td.description())
                    .inputSchema(inputSchema)
                    .build();
            springCallbacks.add(new UserControlledToolCallback(def));
        }

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(profile.chatModel())
                .toolCallbacks(springCallbacks)
                .internalToolExecutionEnabled(false);
        if (toolChoice != null && !toolChoice.isBlank()) {
            optionsBuilder.toolChoice(toolChoice);
        }
        OpenAiChatOptions options = optionsBuilder.build();

        List<ToolTraceEntry> traceEntries = new ArrayList<>();
        List<ReactResult.ToolCallEntry> toolCallLog = new ArrayList<>();
        Map<String, Integer> toolCallCounts = new LinkedHashMap<>();
        long startTimeMs = System.currentTimeMillis();
        int effectiveMaxIterations = Math.min(maxIterations, stoppingCondition.maxIterations());

        List<Message> transcript = new ArrayList<>();
        transcript.add(new SystemMessage(systemPrompt));
        for (Map<String, Object> msg : messages) {
            String role = String.valueOf(msg.getOrDefault("role", "user"));
            String content = String.valueOf(msg.getOrDefault("content", ""));
            if ("user".equals(role)) transcript.add(new UserMessage(content));
            else if ("assistant".equals(role)) transcript.add(new AssistantMessage(content));
        }

        for (int iteration = 1; iteration <= effectiveMaxIterations; iteration++) {
            if (stoppingCondition.iterationExceeded(iteration)) {
                throw new IllegalStateException("StoppingCondition: iteration limit exceeded (" + iteration + " > " + stoppingCondition.maxIterations() + ")");
            }
            long elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000;
            if (elapsedSec > stoppingCondition.timeoutSeconds()) {
                throw new IllegalStateException("StoppingCondition: timeout exceeded (" + elapsedSec + "s > " + stoppingCondition.timeoutSeconds() + "s)");
            }

            Prompt prompt = new Prompt(transcript, options);
            ChatResponse response = chatModel.call(prompt);
            if (response == null || response.getResults().isEmpty()) {
                throw new IllegalStateException("LLM ReAct response missing results");
            }

            AssistantMessage assistantMessage = response.getResults().getFirst().getOutput();

            if (!assistantMessage.hasToolCalls()) {
                String content = assistantMessage.getText();
                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("LLM ReAct response missing content on final iteration");
                }
                String jsonContent = normalizer.normalizeJsonObjectContent(content);
                Map<String, Object> result = normalizer.parseJsonMap(jsonContent);
                if (result.isEmpty()) {
                    throw new IllegalStateException(
                            "LLM ReAct final content is not valid JSON (bytes=" + content.length() + ")");
                }
                return new ReactResult(result, iteration, toolCallLog, traceEntries);
            }

            transcript.add(assistantMessage);

            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                String toolName = toolCall.name();
                String argsJson = toolCall.arguments();
                String callId = toolCall.id();

                Map<String, Object> args = normalizer.parseJsonMap(argsJson);
                ToolExecutor executor = executors.get(toolName);
                if (executor == null) {
                    throw new IllegalStateException("Unknown tool in ReAct loop: " + toolName);
                }

                int repeatCount = toolCallCounts.merge(toolName, 1, Integer::sum);
                if (stoppingCondition.repeatToolCallExceeded(repeatCount)) {
                    throw new IllegalStateException("StoppingCondition: tool '" + toolName + "' called " + repeatCount + " times, exceeding limit " + stoppingCondition.maxRepeatToolCalls());
                }

                var toolDef = toolDefsByName.get(toolName);
                boolean guardPassed = true;
                String guardReason = "";
                if (toolDef != null && toolContext != null) {
                    try {
                        guardPassed = toolDef.checkGuard(toolContext);
                    } catch (IllegalStateException guardEx) {
                        guardPassed = false;
                        guardReason = guardEx.getMessage();
                    }
                }

                long toolStart = System.currentTimeMillis();
                log.debug("ReAct iteration {}: calling tool '{}' guardPassed={}", iteration, toolName, guardPassed);
                Object toolResult;
                String abortReason = "";
                if (!guardPassed) {
                    toolResult = Map.of("error", "guard_rejected: " + guardReason);
                    abortReason = guardReason;
                    log.warn("ReAct tool '{}' blocked by guard: {}", toolName, guardReason);
                } else {
                    try {
                        toolResult = (toolContext != null) ? executor.execute(args, toolContext) : executor.execute(args);
                    } catch (Exception e) {
                        toolResult = Map.of("error", e.getMessage());
                        abortReason = e.getMessage();
                        log.warn("ReAct tool '{}' failed: {}", toolName, e.getMessage());
                    }
                }
                long toolLatency = System.currentTimeMillis() - toolStart;

                String resultJson = toJson(toolResult);
                toolCallLog.add(new ReactResult.ToolCallEntry(iteration, toolName, args, abbreviate(resultJson, 500)));
                traceEntries.add(new ToolTraceEntry(
                        iteration, toolName, args, guardPassed, guardReason, toolLatency, abbreviate(resultJson, 300), abortReason));

                toolResponses.add(new ToolResponseMessage.ToolResponse(callId, toolName, resultJson));
            }

            transcript.add(ToolResponseMessage.builder().responses(toolResponses).build());
        }

        throw new IllegalStateException("ReAct loop exhausted " + maxIterations + " iterations without final answer");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "...";
    }

    /**
     * {@link ToolCallback} that advertises a tool's schema to the LLM but refuses
     * to auto-execute — execution is driven by {@link ToolExecutor} in the outer loop.
     * Spring AI will never call {@link #call(String)} because we set
     * {@code internalToolExecutionEnabled=false}; it remains as a fail-fast guard.
     */
    private static final class UserControlledToolCallback implements ToolCallback {
        private final ToolDefinition definition;

        UserControlledToolCallback(ToolDefinition definition) {
            this.definition = definition;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new IllegalStateException(
                    "UserControlledToolCallback must not be auto-executed by Spring AI: " + definition.name());
        }
    }
}
