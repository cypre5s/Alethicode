package com.alethicode.service.aitutor.visualize;

import java.util.Arrays;
import java.util.Optional;

public enum VisualizeIntent {
    FOR_LOOP_TRACE("for_loop_trace", "for-loop iteration trace", "mermaid"),
    RECURSION_STACK("recursion_stack", "recursion call stack", "mermaid"),
    DATA_STRUCTURE_STATE("data_structure_state", "data structure state", "svg"),
    COMPLEXITY_COMPARE("complexity_compare", "algorithm complexity comparison", "chart"),
    KC_MASTERY_RADAR("kc_mastery_radar", "knowledge concept mastery radar", "chart"),
    MEMORY_LAYOUT("memory_layout", "memory layout", "svg"),
    DATA_FLOW("data_flow", "data flow", "mermaid"),
    FLOWCHART("flowchart", "general flowchart", "mermaid");

    private final String key;
    private final String label;
    private final String defaultFormat;

    VisualizeIntent(String key, String label, String defaultFormat) {
        this.key = key;
        this.label = label;
        this.defaultFormat = defaultFormat;
    }

    public String key() { return key; }
    public String label() { return label; }
    public String defaultFormat() { return defaultFormat; }

    public static Optional<VisualizeIntent> fromKey(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String trimmed = raw.trim().toLowerCase();
        return Arrays.stream(values()).filter(v -> v.key.equals(trimmed)).findFirst();
    }
}
