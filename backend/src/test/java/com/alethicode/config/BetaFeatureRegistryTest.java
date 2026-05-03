package com.alethicode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BetaFeatureRegistryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String FEATURE_KEY = "LLM_TOOL_USE_PROMPT_FALLBACK";

    @Test
    void isEnabledReturnsFalseForFeaturesWithoutExplicitDefault() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BetaFeatureRegistry registry = new BetaFeatureRegistry(jdbcTemplate, objectMapper);
        registry.loadFromDb();

        assertThat(registry.isEnabled("QA_GROUNDING_CRITIC_ENABLED")).isFalse();
        assertThat(registry.isEnabled(FEATURE_KEY)).isFalse();
    }

    @Test
    void adminOverrideBeatsDefault() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BetaFeatureRegistry registry = new BetaFeatureRegistry(jdbcTemplate, objectMapper);
        registry.loadFromDb();

        registry.setOverride(FEATURE_KEY, true);
        assertThat(registry.isEnabled(FEATURE_KEY)).isTrue();

        registry.clearOverride(FEATURE_KEY);
        assertThat(registry.isEnabled(FEATURE_KEY)).isEqualTo(envOrDefault(FEATURE_KEY, false));
    }

    @Test
    void listAllExposesDefaultEnabledAndSourceTags() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BetaFeatureRegistry registry = new BetaFeatureRegistry(jdbcTemplate, objectMapper);
        registry.loadFromDb();

        List<Map<String, Object>> features = registry.listAll();
        Map<String, Object> entry = features.stream()
                .filter(f -> FEATURE_KEY.equals(f.get("key")))
                .findFirst()
                .orElseThrow();

        assertThat(entry.get("default_enabled")).isEqualTo(false);
        assertThat(entry.get("enabled")).isEqualTo(envOrDefault(FEATURE_KEY, false));
        assertThat(entry.get("source")).isEqualTo(envPresent(FEATURE_KEY) ? "env" : "default");
    }

    private static boolean envOrDefault(String key, boolean defaultValue) {
        String raw = System.getenv(key);
        return raw == null || raw.isBlank() ? defaultValue : "true".equalsIgnoreCase(raw.trim());
    }

    private static boolean envPresent(String key) {
        String raw = System.getenv(key);
        return raw != null && !raw.isBlank();
    }
}
