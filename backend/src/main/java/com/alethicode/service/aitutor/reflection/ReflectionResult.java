package com.alethicode.service.aitutor.reflection;

import java.util.Map;

/**
 * Outcome of a Producer-Critic reflection cycle.
 *
 * <p>{@code passed} 表示「最终一次 Critic 是否通过」——既包括第一次 critic 就过，
 * 也包括经过 N 次 refine 后第 (N+1) 次 critic 通过。下游服务把它直接写入
 * {@code reflection_passed} 列时取这个语义，即「是否信任本次 LLM 输出」。
 *
 * <p>是否「第一次就过」可由 {@code roundsUsed == 1 && passed} 表达，无需单独字段。
 *
 * @param output        最终被接受的输出（原始或 refine 后）
 * @param passed        最终一次 Critic 是否通过
 * @param roundsUsed    实际执行的 Critic→Refine 轮数（1 表示无 refine）
 * @param criticVerdict 最终 Critic 的一句话总结（供日志/审计）
 */
public record ReflectionResult(
        Map<String, Object> output,
        boolean passed,
        int roundsUsed,
        String criticVerdict
) {}
