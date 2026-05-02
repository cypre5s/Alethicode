package com.alethicode.service.ai;

import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;

import java.util.List;
import java.util.Map;


public interface AiModelGateway {

    Map<String, Object> callForJson(String systemPrompt, String userPrompt);

    Map<String, Object> callForJson(String systemPrompt, String userPrompt, String profilePrefix);

    Map<String, Object> callForJsonCached(String cacheKey, String systemPrompt, String userPrompt, String profilePrefix);

    String callForContent(String userPrompt);

    ReactResult callWithTools(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<ToolDefinition> tools,
            Map<String, ToolExecutor> executors,
            int maxIterations,
            ToolContext toolContext,
            StoppingCondition stoppingCondition,
            String profilePrefix
    );

    String readRequiredConfig(String key);

    String readConfigOrDefault(String key, String defaultValue);
}
