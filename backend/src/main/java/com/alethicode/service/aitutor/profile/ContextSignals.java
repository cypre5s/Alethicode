package com.alethicode.service.aitutor.profile;

import java.util.List;

/**
 * 当前题目/错误的语境信号，用于 LearnerProfileProjector 做"按当前语境的语义召回"。
 *
 * 由 tutor_graph 的 evidence assembler 在调用 internal /learners/{id}/state-with-context
 * 时携带。null 表示退回到"按时间/置信度的近期召回"（兼容旧路径）。
 */
public record ContextSignals(
        List<String> currentKcs,
        String currentErrorContext,
        String currentProblemStatement
) {
    public static ContextSignals empty() {
        return new ContextSignals(List.of(), "", "");
    }

    public boolean hasErrorContext() {
        return currentErrorContext != null && !currentErrorContext.isBlank();
    }

    public boolean hasKcs() {
        return currentKcs != null && !currentKcs.isEmpty();
    }
}
