package com.alethicode.service.aitutor.react;

import java.util.Map;

/**
 * Executes a tool call issued by the LLM and returns a serializable result.
 * Implementations are registered by tool name in the ReAct loop.
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * @param arguments parsed JSON arguments from the LLM tool_call
     * @return result object that will be serialized to JSON and fed back as an observation
     */
    Object execute(Map<String, Object> arguments);

    default Object execute(Map<String, Object> arguments, ToolContext context) {
        return execute(arguments);
    }
}
