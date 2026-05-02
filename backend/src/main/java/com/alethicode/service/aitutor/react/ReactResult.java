package com.alethicode.service.aitutor.react;

import java.util.List;
import java.util.Map;

/**
 * Captures the outcome of a ReAct (Think-Act-Observe) loop execution.
 *
 * @param result         parsed JSON from the final LLM content response
 * @param iterationsUsed number of Think-Act-Observe iterations consumed
 * @param toolCallLog    ordered log of tool invocations for traceability
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
