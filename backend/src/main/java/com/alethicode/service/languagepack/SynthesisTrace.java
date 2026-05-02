package com.alethicode.service.languagepack;

import java.util.LinkedHashMap;
import java.util.Map;

public record SynthesisTrace(
        GroundedAnswer answer,
        boolean criticPassed,
        String criticVerdict,
        long synthesisLatencyMs,
        long criticLatencyMs,
        String failureBucket
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("grounded", answer != null && answer.grounded());
        map.put("critic_passed", criticPassed);
        map.put("critic_verdict", criticVerdict);
        map.put("synthesis_latency_ms", synthesisLatencyMs);
        map.put("critic_latency_ms", criticLatencyMs);
        if (failureBucket != null && !failureBucket.isBlank()) {
            map.put("failure_bucket", failureBucket);
        }
        return map;
    }
}
