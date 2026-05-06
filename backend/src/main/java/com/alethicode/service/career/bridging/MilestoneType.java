package com.alethicode.service.career.bridging;

import java.util.Arrays;
import java.util.Optional;

/**
 * Career Bridging 里程碑触发类型（plan 2.2 节枚举值）。
 * 为避免脏数据，所有写入 {@code career_bridging_milestone.milestone_type}
 * 的字符串必须经此 enum 校验后落库。
 */
public enum MilestoneType {

    ENROLLMENT("enrollment"),
    KC_CLUSTER_GRADUATED("kc_cluster_graduated"),
    CHAPTER_ENTERED("chapter_entered"),
    PROJECT_COMPLETED("project_completed"),
    PATH_NODE_UNLOCKED("path_node_unlocked");

    private final String code;

    MilestoneType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<MilestoneType> fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
