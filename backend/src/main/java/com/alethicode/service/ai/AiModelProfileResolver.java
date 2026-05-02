package com.alethicode.service.ai;

import com.alethicode.config.BetaFeatureRegistry;
import com.alethicode.service.system.SystemOptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves AI provider configuration (API key, base URL, model, timeouts) with
 * precedence: {@link BetaFeatureRegistry} runtime override → {@code sys_options}
 * DB row → process env → local {@code .env} fallback → hard-coded default.
 *
 * <p>Chat and embedding profiles are resolved independently so a call that only
 * needs chat credentials never fails because of a missing embedding key.
 */
@Component
public class AiModelProfileResolver {

    // Phase 3 切流：删除 EMBEDDING_* 配置项 — 嵌入与向量检索全部由 alethicode-rag 微服务托管，
    // Java 后端不再持有 embedding 凭据。EMBEDDING_API_KEY 等 env 仅 alethicode-rag 容器需要。
    private static final Map<String, String> ENV_TO_DB_AI_KEY = Map.of(
            "OPENAI_API_KEY", "api_key",
            "LLM_BASE_URL", "base_url",
            "LLM_MODEL", "model",
            "LLM_API_TIMEOUT_SECONDS", "timeout_seconds",
            "LLM_API_MAX_RETRIES", "max_retries"
    );

    // Defaults track the production `backend/.env`: DeepSeek chat.
    // CHANGELOG 2026-04-14 records the MiniMax → DeepSeek swap for production stability.
    private static final String DEFAULT_CHAT_BASE_URL = "https://api.deepseek.com/v1";
    private static final String DEFAULT_CHAT_MODEL = "deepseek-chat";
    private static final int DEFAULT_TIMEOUT_SECONDS = 150;
    private static final int DEFAULT_MAX_RETRIES = 9;

    private final BetaFeatureRegistry betaFeatureRegistry;
    private final LocalEnvFallbackLoader envFallbackLoader;
    private volatile SystemOptionService systemOptionService;

    public AiModelProfileResolver(BetaFeatureRegistry betaFeatureRegistry,
                                  LocalEnvFallbackLoader envFallbackLoader) {
        this.betaFeatureRegistry = betaFeatureRegistry;
        this.envFallbackLoader = envFallbackLoader;
    }

    @Autowired(required = false)
    public void setSystemOptionService(SystemOptionService systemOptionService) {
        this.systemOptionService = systemOptionService;
    }

    /**
     * Resolve the chat-model profile for the given prefix (may be empty).
     * Fail-fast when API key is missing. Embedding configuration is not touched.
     */
    public AiModelProfile resolveChat(String profilePrefix) {
        String prefix = profilePrefix == null ? "" : profilePrefix;
        return new AiModelProfile(
                prefix,
                resolveWithFallbackRequired(prefix, "API_KEY", "OPENAI_API_KEY"),
                resolveWithFallbackOrDefault(prefix, "BASE_URL", "LLM_BASE_URL", DEFAULT_CHAT_BASE_URL),
                resolveWithFallbackOrDefault(prefix, "MODEL", "LLM_MODEL", DEFAULT_CHAT_MODEL),
                parseInt(readOrDefault("LLM_API_TIMEOUT_SECONDS", String.valueOf(DEFAULT_TIMEOUT_SECONDS)), DEFAULT_TIMEOUT_SECONDS),
                parseInt(readOrDefault("LLM_API_MAX_RETRIES", String.valueOf(DEFAULT_MAX_RETRIES)), DEFAULT_MAX_RETRIES)
        );
    }

    public String readRequired(String key) {
        String value = readEnvValue(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + key);
        }
        return value.trim();
    }

    public String readOrDefault(String key, String defaultValue) {
        String value = readEnvValue(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    String readEnvValue(String key) {
        if (betaFeatureRegistry != null) {
            String override = betaFeatureRegistry.getOverride(key);
            if (override != null) return override;
        }
        String dbKey = ENV_TO_DB_AI_KEY.get(key);
        if (dbKey != null && systemOptionService != null) {
            String dbValue = systemOptionService.getRawAiConfigValue(dbKey);
            if (dbValue != null && !dbValue.isBlank()) return dbValue;
        }
        String fromProcess = System.getenv(key);
        if (fromProcess != null && !fromProcess.isBlank()) return fromProcess;
        return envFallbackLoader.get(key);
    }

    private String resolveWithFallbackRequired(String prefix, String suffix, String defaultKey) {
        if (!prefix.isEmpty()) {
            String prefixed = readEnvValue(prefix + suffix);
            if (prefixed != null && !prefixed.isBlank()) return prefixed.trim();
        }
        String value = readEnvValue(defaultKey);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + defaultKey);
        }
        return value.trim();
    }

    private String resolveWithFallbackOrDefault(String prefix, String suffix, String defaultKey, String defaultValue) {
        if (!prefix.isEmpty()) {
            String prefixed = readEnvValue(prefix + suffix);
            if (prefixed != null && !prefixed.isBlank()) return prefixed.trim();
        }
        return readOrDefault(defaultKey, defaultValue);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
