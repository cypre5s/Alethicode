package com.alethicode.service.aitutor.react;

import java.util.Map;

/**
 * 执行 LLM 发起的工具调用，并返回可序列化结果。
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * @param arguments 从 LLM {@code tool_call} 解析出的 JSON 参数
     * @return 会被序列化为 JSON 并作为观察结果回传的对象
     */
    Object execute(Map<String, Object> arguments);

    default Object execute(Map<String, Object> arguments, ToolContext context) {
        return execute(arguments);
    }
}
