package com.alethicode.service.ai;

import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Production gateway to Spring AI. Resolves the per-request {@link AiModelProfile}
 * (so prefixes like {@code INIT_LLM_} route to a different API key/model), forces
 * {@code response_format=json_object} for JSON calls, delegates tool loops to
 * {@link SpringAiToolLoopService}, and routes outbound calls through the shared
 * Resilience4j execution chain in {@link AiCircuitBreaker}.
 */
@Service
public class SpringAiModelGateway implements AiModelGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiModelGateway.class);

    private final ChatModel chatModel;
    private final AiResponseNormalizer normalizer;
    private final AiModelProfileResolver profileResolver;
    private final AiCircuitBreaker circuitBreaker;
    private final SpringAiToolLoopService toolLoopService;
    private final AiTelemetryService telemetryService;

    public SpringAiModelGateway(ChatModel chatModel,
                                AiResponseNormalizer normalizer,
                                AiModelProfileResolver profileResolver,
                                AiCircuitBreaker circuitBreaker,
                                SpringAiToolLoopService toolLoopService,
                                AiTelemetryService telemetryService) {
        this.chatModel = chatModel;
        this.normalizer = normalizer;
        this.profileResolver = profileResolver;
        this.circuitBreaker = circuitBreaker;
        this.toolLoopService = toolLoopService;
        this.telemetryService = telemetryService == null ? AiTelemetryService.noop() : telemetryService;
    }

    @Override
    public Map<String, Object> callForJson(String systemPrompt, String userPrompt) {
        return callForJson(systemPrompt, userPrompt, null);
    }

    @Override
    public Map<String, Object> callForJson(String systemPrompt, String userPrompt, String profilePrefix) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalStateException("systemPrompt is required");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalStateException("userPrompt is required");
        }
        AiModelProfile profile = profileResolver.resolveChat(profilePrefix);
        AiTelemetrySpan telemetrySpan = telemetryService.start(AiTelemetryRequest.generation(
                "callForJson",
                "callForJson",
                profile.profilePrefix(),
                profile.chatModel(),
                AiTelemetrySupport.promptHash(systemPrompt, userPrompt),
                AiTelemetrySupport.promptLength(systemPrompt, userPrompt),
                "chat",
                AiTelemetryContextHolder.current()
        ));
        try {
            return circuitBreaker.execute("callForJson", () -> {
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(profile.chatModel())
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .build();

                Prompt prompt = new Prompt(
                        List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
                        options
                );
                ChatResponse response = chatModel.call(prompt);
                if (response == null || response.getResults().isEmpty()) {
                    throw new IllegalStateException("Spring AI chat response is empty");
                }
                String raw = response.getResults().getFirst().getOutput().getText();
                if (raw == null || raw.isBlank()) {
                    throw new IllegalStateException("Spring AI chat response content is blank");
                }
                telemetrySpan.recordResponseLength(raw.length());
                String jsonContent = normalizer.normalizeJsonObjectContent(raw);
                Map<String, Object> result = normalizer.parseJsonMap(jsonContent);
                if (result.isEmpty()) {
                    throw new IllegalStateException("Spring AI response is not a valid JSON object (bytes=" + raw.length() + ")");
                }
                return result;
            });
        } catch (IllegalStateException e) {
            telemetrySpan.recordError(e);
            throw e;
        } catch (Exception e) {
            IllegalStateException wrapped = new IllegalStateException("Spring AI callForJson failed: " + e.getMessage(), e);
            telemetrySpan.recordError(wrapped);
            throw wrapped;
        } finally {
            telemetrySpan.close();
        }
    }

    @Override
    public Map<String, Object> callForJsonCached(String cacheKey, String systemPrompt, String userPrompt, String profilePrefix) {
        // CachingAiModelGateway is @Primary and wraps this bean, so production calls never land here.
        // If a caller bypasses the decorator, fall through to the uncached path rather than crash.
        log.debug("callForJsonCached called directly on SpringAiModelGateway; falling through to uncached");
        return callForJson(systemPrompt, userPrompt, profilePrefix);
    }

    @Override
    public String callForContent(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalStateException("userPrompt is required");
        }
        AiModelProfile profile = profileResolver.resolveChat(null);
        AiTelemetrySpan telemetrySpan = telemetryService.start(AiTelemetryRequest.generation(
                "callForContent",
                "callForContent",
                profile.profilePrefix(),
                profile.chatModel(),
                AiTelemetrySupport.promptHash(userPrompt),
                AiTelemetrySupport.promptLength(userPrompt),
                "chat",
                AiTelemetryContextHolder.current()
        ));
        try {
            return circuitBreaker.execute("callForContent", () -> {
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(profile.chatModel())
                        .build();
                Prompt prompt = new Prompt(List.of(new UserMessage(userPrompt)), options);
                ChatResponse response = chatModel.call(prompt);
                if (response == null || response.getResults().isEmpty()) {
                    throw new IllegalStateException("Spring AI response is empty");
                }
                String raw = response.getResults().getFirst().getOutput().getText();
                if (raw == null || raw.isBlank()) {
                    throw new IllegalStateException("Spring AI response content is blank");
                }
                String content = raw.trim();
                telemetrySpan.recordResponseLength(content.length());
                return content;
            });
        } catch (IllegalStateException e) {
            telemetrySpan.recordError(e);
            throw e;
        } catch (Exception e) {
            IllegalStateException wrapped = new IllegalStateException("Spring AI callForContent failed: " + e.getMessage(), e);
            telemetrySpan.recordError(wrapped);
            throw wrapped;
        } finally {
            telemetrySpan.close();
        }
    }

    @Override
    public ReactResult callWithTools(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<ToolDefinition> tools,
            Map<String, ToolExecutor> executors,
            int maxIterations,
            ToolContext toolContext,
            StoppingCondition stoppingCondition,
            String profilePrefix
    ) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalStateException("systemPrompt is required");
        }
        if (messages == null) {
            throw new IllegalStateException("messages are required");
        }
        AiModelProfile profile = profileResolver.resolveChat(profilePrefix);
        AiTelemetrySpan telemetrySpan = telemetryService.start(AiTelemetryRequest.generation(
                "callWithTools",
                "callWithTools",
                profile.profilePrefix(),
                profile.chatModel(),
                AiTelemetrySupport.promptHash(systemPrompt, String.valueOf(messages)),
                AiTelemetrySupport.promptLength(systemPrompt, String.valueOf(messages)),
                "chat",
                contextFromToolContext(toolContext)
        ));
        try {
            return circuitBreaker.execute("callWithTools", () -> {
                ReactResult result = toolLoopService.execute(
                        systemPrompt, messages, tools, executors,
                        maxIterations, toolContext, stoppingCondition, profile
                );
                telemetrySpan.recordResponseLength(AiTelemetrySupport.responseLength(result.result()));
                return result;
            });
        } catch (IllegalStateException e) {
            telemetrySpan.recordError(e);
            throw e;
        } catch (Exception e) {
            IllegalStateException wrapped = new IllegalStateException("Spring AI callWithTools failed: " + e.getMessage(), e);
            telemetrySpan.recordError(wrapped);
            throw wrapped;
        } finally {
            telemetrySpan.close();
        }
    }

    @Override
    public String readRequiredConfig(String key) {
        return profileResolver.readRequired(key);
    }

    @Override
    public String readConfigOrDefault(String key, String defaultValue) {
        return profileResolver.readOrDefault(key, defaultValue);
    }

    private AiTelemetryContext contextFromToolContext(ToolContext toolContext) {
        AiTelemetryContext current = AiTelemetryContextHolder.current();
        if (current != null) {
            return current;
        }
        if (toolContext == null) {
            return null;
        }
        return new AiTelemetryContext(
                toolContext.phase(),
                toolContext.userId(),
                toolContext.sessionId(),
                toolContext.problemId(),
                null
        );
    }
}
