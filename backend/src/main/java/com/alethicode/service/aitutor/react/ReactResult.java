package com.alethicode.service.aitutor.react;

import java.util.List;
import java.util.Map;

/**
 * ReAct 工具循环的执行结果。
 *
 * @param result 最终 LLM 内容响应解析出的 JSON
 * @param iterationsUsed 消耗的 Think-Act-Observe 轮数
 * @param toolCallLog 按顺序记录的工具调用日志
 */
public record ReactResult(
        Map<String, Object> result,
        int iterationsUsed,
        List<ToolCallEntry> toolCallLog,
        List<ToolTraceEntry> toolTraceEntries
) {

    public ReactResult(Map<String, Object> result, int iterationsUsed, List<ToolCallEntry> toolCallLog) {
        this(result, iterationsUsed, toolCallLog, List.of());
    }

    public record ToolCallEntry(
            int iteration,
            String toolName,
            Map<String, Object> arguments,
            String resultSummary
    ) {}
}
