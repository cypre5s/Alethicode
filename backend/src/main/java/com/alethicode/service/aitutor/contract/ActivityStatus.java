package com.alethicode.service.aitutor.contract;

import java.util.Map;
import java.util.Set;

public final class ActivityStatus {

    public static final String OFFLINE = "offline";
    public static final String IDLE = "idle";
    public static final String TYPING = "typing";
    public static final String RUNNING = "running";
    public static final String SUBMITTED = "submitted";
    public static final String COMPLETED = "completed";
    public static final String ABNORMAL = "abnormal";

    public static final Set<String> ALL = Set.of(
            OFFLINE, IDLE, TYPING, RUNNING, SUBMITTED, COMPLETED, ABNORMAL
    );

    private static final Map<String, String> LEGACY_STATUS_TO_ACTIVITY = Map.ofEntries(
            Map.entry("offline", OFFLINE),
            Map.entry("idle", IDLE),
            Map.entry("typing", TYPING),
            Map.entry("running", RUNNING),
            Map.entry("submitted", SUBMITTED),
            Map.entry("completed", COMPLETED),
            Map.entry("coding", TYPING),
            Map.entry("active", TYPING),
            Map.entry("online", IDLE),
            Map.entry("compile_error", ABNORMAL),
            Map.entry("runtime_error", ABNORMAL),
            Map.entry("infinite_loop", ABNORMAL)
    );

    private static final Set<String> LEGACY_ERROR_STATUSES = Set.of(
            "compile_error", "runtime_error", "infinite_loop"
    );

    private static final Map<String, String> LABELS = Map.of(
            OFFLINE, "离线",
            IDLE, "未开始",
            TYPING, "编码中",
            RUNNING, "运行中",
            SUBMITTED, "已提交",
            COMPLETED, "已完成",
            ABNORMAL, "异常"
    );

    private ActivityStatus() {}

    public static String fromLegacyStatus(String legacyStatus) {
        if (legacyStatus == null || legacyStatus.isBlank()) {
            return OFFLINE;
        }
        return LEGACY_STATUS_TO_ACTIVITY.getOrDefault(legacyStatus.strip().toLowerCase(), OFFLINE);
    }

    public static String errorTaxonomyFromLegacyStatus(String legacyStatus) {
        if (legacyStatus == null) {
            return null;
        }
        String key = legacyStatus.strip().toLowerCase();
        if (!LEGACY_ERROR_STATUSES.contains(key)) {
            return null;
        }
        return ErrorTaxonomy.normalize(key);
    }

    public static boolean isLegacyErrorStatus(String legacyStatus) {
        return legacyStatus != null && LEGACY_ERROR_STATUSES.contains(legacyStatus.strip().toLowerCase());
    }

    public static String label(String status) {
        return LABELS.getOrDefault(status, "离线");
    }

    public static boolean isValid(String value) {
        return value != null && ALL.contains(value);
    }
}
