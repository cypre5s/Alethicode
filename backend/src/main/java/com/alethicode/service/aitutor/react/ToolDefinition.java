package com.alethicode.service.aitutor.react;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Describes a tool that an LLM can invoke during a ReAct loop.
 * Mirrors the OpenAI function-calling tool schema.
 *
 * @param name             stable identifier used in tool_calls
 * @param description      natural-language description shown to the model
 * @param parameters       JSON-Schema-style parameter spec (may be empty map for no-arg tools)
 * @param domain           tool domain (TUTOR / QA) for domain isolation
 * @param guard            pre-execution guard; returns true if execution is allowed
 * @param agentDescription ACI documentation: when to call, when not to, common failures
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        ToolDomain domain,
        Predicate<ToolContext> guard,
        String agentDescription
) {

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this(name, description, parameters, ToolDomain.TUTOR, ctx -> true, "");
    }

    public Map<String, Object> toOpenAiToolSpec() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    public boolean checkGuard(ToolContext context) {
        return guard == null || guard.test(context);
    }
}
