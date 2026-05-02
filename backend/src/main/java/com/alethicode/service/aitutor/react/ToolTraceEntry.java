package com.alethicode.service.aitutor.react;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolTraceEntry(
        int iteration,
        String toolName,
        Map<String, Object> arguments,
        boolean guardPassed,
        String guardReason,
        long latencyMs,
        String resultSummary,
        String abortReason
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("iteration", iteration);
        map.put("tool_name", toolName);
        map.put("arguments", arguments);
        map.put("guard_passed", guardPassed);
        if (guardReason != null && !guardReason.isBlank()) {
            map.put("guard_reason", guardReason);
        }
        map.put("latency_ms", latencyMs);
        map.put("result_summary", resultSummary);
        if (abortReason != null && !abortReason.isBlank()) {
            map.put("abort_reason", abortReason);
        }
        return map;
    }
}
