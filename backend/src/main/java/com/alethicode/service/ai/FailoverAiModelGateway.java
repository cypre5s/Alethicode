package com.alethicode.service.ai;

import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Best-effort multi-provider decorator for {@link AiModelGateway}.
 *
 * <p>Alethicode 的生产默认 chat provider 是 <b>DeepSeek</b>（{@code deepseek-chat}
 * / {@code deepseek-reasoner}）；当 DeepSeek 抛出 recoverable error（超时 /
 * 5xx / 熔断打开等），本装饰器按 {@link #fallbackPrefixes} 顺序尝试其他配置前缀，
 * 例如：
 *
 * <pre>
 *   INIT_LLM_       → DeepSeek reasoner（初始化 / 代码质量高要求）
 *   QWEN_LLM_       → 通义千问
 *   VOLCANO_LLM_    → 字节火山
 *   MINIMAX_LLM_    → MiniMax（历史备选）
 *   ZHIPU_LLM_      → 智谱
 * </pre>
 *
 * fallbackPrefixes 通过 {@code ALETHICODE_AI_FALLBACK_PREFIXES} 环境变量（逗号
 * 分隔）配置；留空则退化为单 provider。
 *
 * <p>Schema / guard / idempotency 类错误不触发 failover（换 provider 也会失败）。
 *
 * <p>Not registered as {@code @Primary} on purpose — callers who want
 * deterministic single-provider behaviour (admin validation, eval harness) keep
 * using {@link CachingAiModelGateway}. Wire this bean explicitly from the
 * tutor/agent/language-pack modules where a user-facing failure has a high cost.
 */
public class FailoverAiModelGateway implements AiModelGateway {

    private static final Logger log = LoggerFactory.getLogger(FailoverAiModelGateway.class);

    private final AiModelGateway primary;
    private final List<String> fallbackPrefixes;
    private final Counter failoverSuccesses;
    private final Counter failoverExhausted;

    public FailoverAiModelGateway(AiModelGateway primary,
                                  List<String> fallbackPrefixes,
                                  MeterRegistry meterRegistry) {
        this.primary = primary;
        this.fallbackPrefixes = new CopyOnWriteArrayList<>(fallbackPrefixes == null ? List.of() : fallbackPrefixes);
        this.failoverSuccesses = Counter.builder("ai_failover_success_total")
                .description("Number of AI requests that succeeded on a fallback provider")
                .register(meterRegistry);
        this.failoverExhausted = Counter.builder("ai_failover_exhausted_total")
                .description("Number of AI requests that failed on every configured provider")
                .register(meterRegistry);
    }

    @Override
    public Map<String, Object> callForJson(String systemPrompt, String userPrompt) {
        return callForJson(systemPrompt, userPrompt, null);
    }

    @Override
    public Map<String, Object> callForJson(String systemPrompt, String userPrompt, String profilePrefix) {
        return withFallback("callForJson", prefix ->
                primary.callForJson(systemPrompt, userPrompt, prefix),
                profilePrefix);
    }

    @Override
    public Map<String, Object> callForJsonCached(String cacheKey, String systemPrompt, String userPrompt, String profilePrefix) {
        // Cached path short-circuits inside CachingAiModelGateway; if it reaches here the
        // cache missed so we still want failover on the underlying call.
        return withFallback("callForJsonCached", prefix ->
                primary.callForJsonCached(cacheKey, systemPrompt, userPrompt, prefix),
                profilePrefix);
    }

    @Override
    public String callForContent(String userPrompt) {
        return withFallback("callForContent", prefix -> primary.callForContent(userPrompt), null);
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
        return withFallback("callWithTools", prefix ->
                primary.callWithTools(systemPrompt, messages, tools, executors,
                        maxIterations, toolContext, stoppingCondition, prefix),
                profilePrefix);
    }

    @Override
    public String readRequiredConfig(String key) {
        return primary.readRequiredConfig(key);
    }

    @Override
    public String readConfigOrDefault(String key, String defaultValue) {
        return primary.readConfigOrDefault(key, defaultValue);
    }

    /** Try the given action on the primary profile first, then each fallback prefix in order. */
    private <T> T withFallback(String op, GatewayCall<T> call, String requestedPrefix) {
        RuntimeException lastError;
        try {
            return call.invoke(requestedPrefix);
        } catch (RuntimeException e) {
            if (!isRecoverable(e)) throw e;
            lastError = e;
            log.warn("{} primary provider (prefix={}) failed: {} — trying fallbacks",
                    op, requestedPrefix, e.getMessage());
        }
        for (String fallback : fallbackPrefixes) {
            // Skip the same prefix the caller asked for.
            if (requestedPrefix != null && requestedPrefix.equals(fallback)) continue;
            try {
                T result = call.invoke(fallback);
                failoverSuccesses.increment();
                log.info("{} recovered via fallback prefix={}", op, fallback);
                return result;
            } catch (RuntimeException e) {
                if (!isRecoverable(e)) throw e;
                lastError = e;
                log.warn("{} fallback prefix={} also failed: {}", op, fallback, e.getMessage());
            }
        }
        failoverExhausted.increment();
        throw lastError;
    }

    /**
     * Only retry on categories the fallback could plausibly resolve. Schema
     * violations / guard rejections don't change between providers.
     */
    private static boolean isRecoverable(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
        if (msg.contains("schema") || msg.contains("guard") || msg.contains("idempotency")) {
            return false;
        }
        // Generic network / provider error signals we can retry on another provider.
        return msg.contains("timeout")
                || msg.contains("connection")
                || msg.contains("5")    // 5xx as a substring hint
                || msg.contains("circuit")
                || msg.contains("unavailable")
                || msg.contains("failed");
    }

    @FunctionalInterface
    private interface GatewayCall<T> {
        T invoke(String profilePrefix);
    }
}
