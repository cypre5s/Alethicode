package com.alethicode.service.aitutor.reflection;

import java.util.Map;

/**
 * Outcome of a Producer-Critic reflection cycle.
 *
 * @param output       the final accepted output (original or refined)
 * @param passed       true if the Critic accepted the output without refinement
 * @param roundsUsed   number of Critic→Refine cycles actually executed
 * @param criticVerdict last Critic verdict summary (for logging/audit)
 */
public record ReflectionResult(
        Map<String, Object> output,
        boolean passed,
        int roundsUsed,
        String criticVerdict
) {}
