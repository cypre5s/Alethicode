package com.alethicode.service.aitutor.contract;

import java.util.Map;
import java.util.Set;

public final class ErrorTaxonomy {

    public static final String SYNTAX_ERROR = "syntax_error";
    public static final String RUNTIME_ERROR = "runtime_error";
    public static final String LOGIC_ERROR = "logic_error";
    public static final String BOUNDARY_CONDITION = "boundary_condition";
    public static final String PERFORMANCE = "performance";
    public static final String ALGORITHM_ERROR = "algorithm_error";
    public static final String INPUT_PARSING = "input_parsing";
    public static final String NAME_OR_TYPE_ERROR = "name_or_type_error";
    public static final String UNKNOWN = "unknown";

    public static final Set<String> ALL = Set.of(
            SYNTAX_ERROR, RUNTIME_ERROR, LOGIC_ERROR, BOUNDARY_CONDITION,
            PERFORMANCE, ALGORITHM_ERROR, INPUT_PARSING, NAME_OR_TYPE_ERROR, UNKNOWN
    );

    private static final Map<String, String> LEGACY_MAPPING = Map.ofEntries(
            Map.entry("compile", SYNTAX_ERROR),
            Map.entry("compile_error", SYNTAX_ERROR),
            Map.entry("syntax", SYNTAX_ERROR),
            Map.entry("invalid_syntax", SYNTAX_ERROR),
            Map.entry("runtime", RUNTIME_ERROR),
            Map.entry("runtime_error", RUNTIME_ERROR),
            Map.entry("logic", LOGIC_ERROR),
            Map.entry("logic_error", LOGIC_ERROR),
            Map.entry("wrong_answer", LOGIC_ERROR),
            Map.entry("time_limit", PERFORMANCE),
            Map.entry("memory_limit", PERFORMANCE),
            Map.entry("time_limit_exceeded", PERFORMANCE),
            Map.entry("memory_limit_exceeded", PERFORMANCE),
            Map.entry("memory_error", PERFORMANCE),
            Map.entry("timeout", PERFORMANCE),
            Map.entry("infinite_loop", PERFORMANCE),
            Map.entry("boundary", BOUNDARY_CONDITION),
            Map.entry("boundary_error", BOUNDARY_CONDITION),
            Map.entry("boundary_condition", BOUNDARY_CONDITION),
            Map.entry("index_error", BOUNDARY_CONDITION),
            Map.entry("type_error", NAME_OR_TYPE_ERROR),
            Map.entry("name_error", NAME_OR_TYPE_ERROR),
            Map.entry("value_error", NAME_OR_TYPE_ERROR),
            Map.entry("system_error", UNKNOWN),
            Map.entry("unknown_error", UNKNOWN),
            Map.entry("unknown", UNKNOWN)
    );

    private static final Map<String, String> LABELS = Map.of(
            SYNTAX_ERROR, "语法错误",
            RUNTIME_ERROR, "运行时错误",
            LOGIC_ERROR, "逻辑错误",
            BOUNDARY_CONDITION, "边界条件",
            PERFORMANCE, "性能问题",
            ALGORITHM_ERROR, "算法错误",
            INPUT_PARSING, "输入解析",
            NAME_OR_TYPE_ERROR, "名称/类型错误",
            UNKNOWN, "未分类"
    );

    private ErrorTaxonomy() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String key = raw.strip().toLowerCase().replace(' ', '_');
        if (ALL.contains(key)) {
            return key;
        }
        return LEGACY_MAPPING.getOrDefault(key, UNKNOWN);
    }

    public static String label(String taxonomy) {
        return LABELS.getOrDefault(taxonomy, "未分类");
    }

    public static boolean isValid(String value) {
        return value != null && ALL.contains(value);
    }
}
