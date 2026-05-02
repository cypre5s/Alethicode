package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum WorkflowEvent {
    CALIBRATING,
    READING,
    IDEATING,
    CODING,
    ERROR_FEEDBACK,
    AC_REVIEW,
    TRANSFER,
    CHAT,
    AGENT_FEEDBACK,
    /**
     * 围绕当前题目薄弱 KC 的知识点回顾辅助事件。不改变当前 phase。
     */
    KNOWLEDGE_REVIEW,
    /**
     * Faded Parsons 拼装题派发辅助事件。学生主动从任意 phase 触发，dispatch 由
     * tutor_graph parsons 节点完成；submit/walkthrough 走纯 REST 不进 graph。
     */
    PARSONS;

    public static Optional<WorkflowEvent> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }

    public boolean auxiliary() {
        return this == CHAT || this == AGENT_FEEDBACK
                || this == KNOWLEDGE_REVIEW || this == PARSONS;
    }
}
