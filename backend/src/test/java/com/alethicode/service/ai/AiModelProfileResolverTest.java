package com.alethicode.service.ai;

import com.alethicode.config.BetaFeatureRegistry;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiModelProfileResolverTest {

    private final LocalEnvFallbackLoader envLoader = new StubEnvLoader(new HashMap<>());

    @Test
    void resolveChatUsesDbValueWhenPresent() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        when(beta.getOverride(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        SystemOptionService options = mock(SystemOptionService.class);
        when(options.getRawAiConfigValue("api_key")).thenReturn("db-key");
        when(options.getRawAiConfigValue("base_url")).thenReturn("https://db.example");
        when(options.getRawAiConfigValue("model")).thenReturn("db-model");

        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, envLoader);
        resolver.setSystemOptionService(options);

        AiModelProfile profile = resolver.resolveChat(null);
        assertThat(profile.apiKey()).isEqualTo("db-key");
        assertThat(profile.baseUrl()).isEqualTo("https://db.example");
        assertThat(profile.chatModel()).isEqualTo("db-model");
        assertThat(profile.profilePrefix()).isEmpty();
    }

    @Test
    void resolveChatPrefersBetaOverride() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        when(beta.getOverride("OPENAI_API_KEY")).thenReturn("beta-key");
        when(beta.getOverride("LLM_BASE_URL")).thenReturn(null);
        when(beta.getOverride("LLM_MODEL")).thenReturn(null);
        when(beta.getOverride("LLM_API_TIMEOUT_SECONDS")).thenReturn(null);
        when(beta.getOverride("LLM_API_MAX_RETRIES")).thenReturn(null);

        SystemOptionService options = mock(SystemOptionService.class);
        when(options.getRawAiConfigValue("api_key")).thenReturn("db-key");

        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, envLoader);
        resolver.setSystemOptionService(options);

        AiModelProfile profile = resolver.resolveChat("");
        assertThat(profile.apiKey()).isEqualTo("beta-key");
    }

    @Test
    void resolveChatUsesLocalEnvFallbackWhenDbMissing() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        Map<String, String> fallback = new HashMap<>();
        fallback.put("OPENAI_API_KEY", "env-key");
        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, new StubEnvLoader(fallback));

        AiModelProfile profile = resolver.resolveChat(null);
        assertThat(profile.apiKey()).isEqualTo("env-key");
        assertThat(profile.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(profile.chatModel()).isEqualTo("deepseek-chat");
    }

    @Test
    void resolveChatFailsFastWhenApiKeyMissing() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, envLoader);

        assertThatThrownBy(() -> resolver.resolveChat(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required config: OPENAI_API_KEY");
    }

    @Test
    void resolveChatPrefixRoutesThroughPrefixedKeys() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        Map<String, String> fallback = new HashMap<>();
        fallback.put("INIT_LLM_API_KEY", "init-key");
        fallback.put("INIT_LLM_MODEL", "init-model");
        fallback.put("INIT_LLM_BASE_URL", "https://init.example");
        fallback.put("OPENAI_API_KEY", "default-key");
        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, new StubEnvLoader(fallback));

        AiModelProfile profile = resolver.resolveChat("INIT_LLM_");
        assertThat(profile.apiKey()).isEqualTo("init-key");
        assertThat(profile.baseUrl()).isEqualTo("https://init.example");
        assertThat(profile.chatModel()).isEqualTo("init-model");
        assertThat(profile.profilePrefix()).isEqualTo("INIT_LLM_");
    }

    @Test
    void resolveChatPrefixFallsBackToDefaultWhenPrefixedAbsent() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        Map<String, String> fallback = new HashMap<>();
        fallback.put("OPENAI_API_KEY", "default-key");
        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, new StubEnvLoader(fallback));

        AiModelProfile profile = resolver.resolveChat("INIT_LLM_");
        assertThat(profile.apiKey()).isEqualTo("default-key");
    }

    @Test
    void readRequiredFailsFastOnMissingKey() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, envLoader);

        assertThatThrownBy(() -> resolver.readRequired("ABSENT_KEY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ABSENT_KEY");
    }

    @Test
    void readOrDefaultReturnsDefaultWhenMissing() {
        BetaFeatureRegistry beta = mock(BetaFeatureRegistry.class);
        AiModelProfileResolver resolver = new AiModelProfileResolver(beta, envLoader);

        assertThat(resolver.readOrDefault("ABSENT_KEY", "default-value")).isEqualTo("default-value");
    }

    /** 避免访问文件系统的内存替身。 */
    private static final class StubEnvLoader extends LocalEnvFallbackLoader {
        private final Map<String, String> values;

        StubEnvLoader(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }
    }
}
