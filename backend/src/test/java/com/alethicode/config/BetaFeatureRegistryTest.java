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

    @Test
    void definitionsExposeDefaultTrueForTutorReact() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BetaFeatureRegistry registry = new BetaFeatureRegistry(jdbcTemplate, objectMapper);
        registry.loadFromDb();

        assertThat(registry.definitions())
                .filteredOn(definition -> "TUTOR_REACT_ENABLED".equals(definition.key()))
                .singleElement()
                .satisfies(definition -> assertThat(definition.defaultEnabled()).isTrue());
    }

    @Test
    void isEnabledReturnsFalseForFeaturesWithoutExplicitDefault() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BetaFeatureRegistry registry = new BetaFeatureRegistry(jdbcTemplate, objectMapper);
        registry.loadFromDb();

        assertThat(registry.isEnabled("REACT_ENABLED")).isFalse();
        assertThat(registry.isEnabled("QA_GROUNDING_CRITIC_ENABLED")).isFalse();
        assertThat(registry.isEnabled("LLM_TOOL_USE_PROMPT_FALLBACK")).isFalse();
    }

    @Test
    void adminOverrideBeatsDefault() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BetaFeatureRegistry registry = new BetaFeatureRegistry(jdbcTemplate, objectMapper);
        registry.loadFromDb();

        registry.setOverride("TUTOR_REACT_ENABLED", false);
        assertThat(registry.isEnabled("TUTOR_REACT_ENABLED")).isFalse();

        registry.clearOverride("TUTOR_REACT_ENABLED");
        assertThat(registry.isEnabled("TUTOR_REACT_ENABLED")).isEqualTo(envOrDefault("TUTOR_REACT_ENABLED", true));
    }

    @Test
    void listAllExposesDefaultEnabledAndSourceTags() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BetaFeatureRegistry registry = new BetaFeatureRegistry(jdbcTemplate, objectMapper);
        registry.loadFromDb();

        List<Map<String, Object>> features = registry.listAll();
        Map<String, Object> tutorReact = features.stream()
                .filter(f -> "TUTOR_REACT_ENABLED".equals(f.get("key")))
                .findFirst()
                .orElseThrow();

        assertThat(tutorReact.get("default_enabled")).isEqualTo(true);
        assertThat(tutorReact.get("enabled")).isEqualTo(envOrDefault("TUTOR_REACT_ENABLED", true));
        assertThat(tutorReact.get("source")).isEqualTo(envPresent("TUTOR_REACT_ENABLED") ? "env" : "default");
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
