package com.alethicode.service.aitutor.rollout;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;
import io.getunleash.variant.Variant;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(name = "alethicode.rollout.enabled", havingValue = "true")
public class UnleashRolloutFlagService implements RolloutFlagService {

    private final DefaultUnleash unleash;

    public UnleashRolloutFlagService(
            @Value("${alethicode.rollout.unleash-url}") String unleashUrl,
            @Value("${alethicode.rollout.unleash-api-key:}") String apiKey,
            @Value("${alethicode.rollout.unleash-app-name:alethicode-java}") String appName,
            @Value("${alethicode.rollout.unleash-instance-id:local}") String instanceId,
            @Value("${alethicode.rollout.unleash-project:ai-tutor}") String project
    ) {
        UnleashConfig config = UnleashConfig.builder()
                .appName(appName)
                .instanceId(instanceId)
                .projectName(project)
                .unleashAPI(unleashUrl)
                .apiKey(apiKey)
                .synchronousFetchOnInitialisation(true)
                .build();
        this.unleash = new DefaultUnleash(config);
    }

    @Override
    public boolean isEnabled(String flagName, boolean defaultValue, String scopeType, String scopeKey, Map<String, Object> context) {
        return unleash.isEnabled(flagName, buildContext(scopeType, scopeKey, context), defaultValue);
    }

    @Override
    public String getVariant(String flagName, String defaultValue, String scopeType, String scopeKey, Map<String, Object> context) {
        Variant variant = unleash.getVariant(flagName, buildContext(scopeType, scopeKey, context));
        if (variant == null || !variant.isEnabled() || variant.getName() == null || variant.getName().isBlank()) {
            return defaultValue;
        }
        return variant.getName();
    }

    private UnleashContext buildContext(String scopeType, String scopeKey, Map<String, Object> context) {
        UnleashContext.Builder builder = UnleashContext.builder()
                .appName("alethicode-java")
                .addProperty("scopeType", scopeType == null ? "" : scopeType)
                .addProperty("scopeKey", scopeKey == null ? "" : scopeKey);
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (entry.getValue() != null) {
                    builder.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return builder.build();
    }

    @PreDestroy
    void shutdown() {
        unleash.shutdown();
    }
}
