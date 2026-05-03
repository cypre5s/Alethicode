package com.alethicode.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BetaFeatureRegistry {

    private static final Logger log = LoggerFactory.getLogger(BetaFeatureRegistry.class);
    private static final String DB_KEY = "beta_features";
    private static final String SELECT_SQL = "select value::text from sys_options where key = ?";
    private static final String UPSERT_SQL =
            "insert into sys_options(key, value, updated_at) values (?, cast(? as jsonb), now()) " +
                    "on conflict (key) do update set value = excluded.value, updated_at = now()";

    public record BetaFeatureDefinition(
            String key,
            String name,
            String description,
            String warning,
            String category,
            boolean defaultEnabled
    ) {
        public BetaFeatureDefinition(String key, String name, String description, String warning, String category) {
            this(key, name, description, warning, category, false);
        }
    }

    private static final List<BetaFeatureDefinition> DEFINITIONS = List.of(
            new BetaFeatureDefinition(
                    "QA_GROUNDING_CRITIC_ENABLED",
                    "QA Grounding Critic",
                    "对问答结果进行额外的 LLM 验证，检查答案是否有充分依据",
                    "每次问答额外一次 LLM 调用，可能增加拒答率（严格模式下覆盖率下降）",
                    "AI 推理"
            ),
            new BetaFeatureDefinition(
                    "LLM_TOOL_USE_PROMPT_FALLBACK",
                    "LLM 工具调用提示词降级",
                    "使用提示词拼接方式进行工具调用，替代模型原生 function calling 接口",
                    "兼容性依赖模型提供商实现，可能降低工具调用可靠性；仅在原生接口不可用时考虑开启",
                    "LLM 兼容"
            )
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    public BetaFeatureRegistry(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadFromDb() {
        try {
            String rawJson = jdbcTemplate.queryForObject(SELECT_SQL, String.class, DB_KEY);
            if (rawJson != null) {
                Map<String, Boolean> stored = objectMapper.readValue(rawJson, new TypeReference<>() {});
                cache.putAll(stored);
                log.info("Beta features loaded from DB: {}", cache);
            }
        } catch (EmptyResultDataAccessException ignored) {
            log.info("No beta_features row in sys_options, starting with defaults");
        } catch (Exception e) {
            log.warn("Failed to load beta_features from DB, starting with defaults: {}", e.getMessage());
        }
    }

    public List<BetaFeatureDefinition> definitions() {
        return DEFINITIONS;
    }

    public String getOverride(String key) {
        Boolean val = cache.get(key);
        return val == null ? null : val.toString();
    }

    /**
     * 返回特性的生效开关值。
     * 优先级：admin 显式写入的 override > 环境变量 > 定义里的 defaultEnabled。
     */
    public boolean isEnabled(String key) {
        Boolean dbValue = cache.get(key);
        if (dbValue != null) {
            return dbValue;
        }
        String rawEnv = System.getenv(key);
        if (rawEnv != null && !rawEnv.isBlank()) {
            return "true".equalsIgnoreCase(rawEnv.trim());
        }
        return defaultEnabledFor(key);
    }

    public void setOverride(String key, boolean enabled) {
        cache.put(key, enabled);
        persistToDb();
        log.info("Beta feature toggled: {}={}", key, enabled);
    }

    public void clearOverride(String key) {
        cache.remove(key);
        persistToDb();
        log.info("Beta feature override cleared: {}", key);
    }

    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BetaFeatureDefinition def : DEFINITIONS) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", def.key());
            entry.put("name", def.name());
            entry.put("description", def.description());
            entry.put("warning", def.warning());
            entry.put("category", def.category());
            entry.put("default_enabled", def.defaultEnabled());

            Boolean dbValue = cache.get(def.key());
            String rawEnv = System.getenv(def.key());
            boolean effective;
            if (dbValue != null) {
                effective = dbValue;
                entry.put("source", "admin");
            } else if (rawEnv != null && !rawEnv.isBlank()) {
                effective = "true".equalsIgnoreCase(rawEnv);
                entry.put("source", "env");
            } else {
                effective = def.defaultEnabled();
                entry.put("source", "default");
            }
            entry.put("enabled", effective);
            result.add(entry);
        }
        return result;
    }

    public boolean isKnownFeature(String key) {
        return DEFINITIONS.stream().anyMatch(d -> d.key().equals(key));
    }

    private boolean defaultEnabledFor(String key) {
        for (BetaFeatureDefinition def : DEFINITIONS) {
            if (def.key().equals(key)) {
                return def.defaultEnabled();
            }
        }
        return false;
    }

    private void persistToDb() {
        try {
            String json = objectMapper.writeValueAsString(cache);
            jdbcTemplate.update(UPSERT_SQL, DB_KEY, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize beta_features", e);
        }
    }
}
