package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum CardType {
    PROBLEM_GUIDE("problem_guide", "problem_guide"),
    IDEATE_ANALYSIS("ideate_analysis", "ideate"),
    FADED_EXAMPLE("faded_example", "scaffolding"),
    ERROR_DIAGNOSIS("error_diagnosis", "error_diagnosis"),
    EXECUTION_TRACE_EXPLAINER("execution_trace_explainer", "execution_trace_explainer"),
    POST_AC("post_ac", "post_ac"),
    TRANSFER_PROBLEM("transfer_problem", "transfer"),
    KNOWLEDGE_REVIEW("knowledge_review", "knowledge_review"),
    AI_REPLY("ai_reply", "chat"),
    VISUALIZE("visualize", "visualize"),
    PARSONS_PROBLEM("parsons_problem", "parsons");

    private final String messageType;
    private final String outputKey;

    CardType(String messageType, String outputKey) {
        this.messageType = messageType;
        this.outputKey = outputKey;
    }

    public String messageType() {
        return messageType;
    }

    public String outputKey() {
        return outputKey;
    }

    public static Optional<CardType> fromMessageType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.messageType.equalsIgnoreCase(raw.trim()))
                .findFirst();
    }

    public static Optional<CardType> fromOutputKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.outputKey.equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
