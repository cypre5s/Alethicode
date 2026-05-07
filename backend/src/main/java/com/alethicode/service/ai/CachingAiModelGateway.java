package com.alethicode.service.ai;

import com.alethicode.service.aitutor.LlmResponseCacheService;
import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 为 {@link SpringAiModelGateway} 增加进程内 TTL 缓存的装饰器。
 *
 * <p>{@code @Primary} 让默认注入拿到缓存视图；需要绕过缓存时按 qualifier 注入：
 * <pre>{@code @Autowired @Qualifier("springAiModelGateway") AiModelGateway gateway;}</pre>
 */
@Service
@Primary
public class CachingAiModelGateway implements AiModelGateway {

    private final SpringAiModelGateway delegate;
    private final AiTelemetryService telemetryService;
    private LlmResponseCacheService cacheService;

    public CachingAiModelGateway(SpringAiModelGateway delegate, AiTelemetryService telemetryService) {
        this.delegate = delegate;
        this.telemetryService = telemetryService == null ? AiTelemetryService.noop() : telemetryService;
    }

    @Autowired(required = false)
    public void setCacheService(LlmResponseCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public Map<String, Object> callForJson(String systemPrompt, String userPrompt) {
        return delegate.callForJson(systemPrompt, userPrompt);
    }

    @Override
    public Map<String, Object> callForJson(String systemPrompt, String userPrompt, String profilePrefix) {
        return delegate.callForJson(systemPrompt, userPrompt, profilePrefix);
    }

    @Override
    public Map<String, Object> callForJsonCached(String cacheKey, String systemPrompt, String userPrompt, String profilePrefix) {
        if (cacheService != null && cacheKey != null) {
            Map<String, Object> cached = cacheService.get(cacheKey);
            if (cached != null) {
                recordCacheObservation(cacheKey, systemPrompt, userPrompt, profilePrefix, true, cached);
                return cached;
            }
        }
        Map<String, Object> result = delegate.callForJson(systemPrompt, userPrompt, profilePrefix);
        if (cacheService != null && cacheKey != null) {
            cacheService.put(cacheKey, result);
            recordCacheObservation(cacheKey, systemPrompt, userPrompt, profilePrefix, false, result);
        }
        return result;
    }

    @Override
    public String callForContent(String userPrompt) {
        return delegate.callForContent(userPrompt);
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
        return delegate.callWithTools(systemPrompt, messages, tools, executors, maxIterations, toolContext, stoppingCondition, profilePrefix);
    }

    @Override
    public String readRequiredConfig(String key) {
        return delegate.readRequiredConfig(key);
    }

    @Override
    public String readConfigOrDefault(String key, String defaultValue) {
        return delegate.readConfigOrDefault(key, defaultValue);
    }

    private void recordCacheObservation(
            String cacheKey,
            String systemPrompt,
            String userPrompt,
            String profilePrefix,
            boolean cacheHit,
            Map<String, Object> response
    ) {
        AiTelemetryRequest request = AiTelemetryRequest.cacheObservation(
                "callForJsonCached",
                profilePrefix,
                AiTelemetrySupport.promptHash(systemPrompt, userPrompt),
                AiTelemetrySupport.promptLength(systemPrompt, userPrompt),
                AiTelemetrySupport.sha256(cacheKey),
                cacheHit,
                AiTelemetryContextHolder.current()
        );
        try (AiTelemetrySpan span = telemetryService.start(request)) {
            span.recordResponseLength(AiTelemetrySupport.responseLength(response));
        }
    }
}
