package com.alethicode.service.aitutor.rollout;

import java.util.Map;

public interface RolloutFlagService {

    boolean isEnabled(String flagName, boolean defaultValue, String scopeType, String scopeKey, Map<String, Object> context);

    String getVariant(String flagName, String defaultValue, String scopeType, String scopeKey, Map<String, Object> context);
}
