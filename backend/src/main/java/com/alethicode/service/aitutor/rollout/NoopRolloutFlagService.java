package com.alethicode.service.aitutor.rollout;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NoopRolloutFlagService implements RolloutFlagService {

    @Override
    public boolean isEnabled(String flagName, boolean defaultValue, String scopeType, String scopeKey, Map<String, Object> context) {
        return defaultValue;
    }

    @Override
    public String getVariant(String flagName, String defaultValue, String scopeType, String scopeKey, Map<String, Object> context) {
        return defaultValue;
    }
}
