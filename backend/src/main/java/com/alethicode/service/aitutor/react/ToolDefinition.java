package com.alethicode.service.aitutor.react;

import java.util.Map;
import java.util.function.Predicate;

/**
 * 描述 ReAct 循环中 LLM 可调用的工具。
 *
 * @param name {@code tool_calls} 使用的稳定标识
 * @param description 展示给模型的自然语言说明
 * @param parameters JSON Schema 风格参数定义
 * @param domain 工具域，用于隔离 TUTOR 与 QA 场景
 * @param guard 执行前守卫，返回 true 表示允许调用
 * @param agentDescription ACI 说明，描述何时调用、何时不调用和常见失败
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
