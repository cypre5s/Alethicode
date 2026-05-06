package com.alethicode.service.career.bridging;

import java.util.Arrays;
import java.util.Optional;

/**
 * Career Bridging Why 报告类型（plan 3.2 节）。
 *
 * <p>对应 V84 表 {@code career_bridging_report.report_kind} 列，是 LLM 生成
 * Why 报告时分类不同触发场景的「报告主题」。当前已落地 {@link #MILESTONE}；
 * 后续 todo 13 落地「project_completed 重激活」时会扩展其它取值，避免散落
 * 在多个服务里硬编码。
 */
public enum ReportKind {

    /** 由某个里程碑（enrollment / kc_cluster_graduated / chapter_entered 等）直接触发。 */
    MILESTONE("milestone");

    private final String code;

    ReportKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<ReportKind> fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}
