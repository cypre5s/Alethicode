package com.alethicode.service.languagepack.quality;

import java.util.List;

/**
 * Reference solution 静态 lint 结果。
 * {@code passable} = true 表示无任何硬规则违反，可继续进入 self-validation。
 */
public record ReferenceLintReport(
        List<LintViolation> hardViolations,
        List<LintViolation> softViolations
) {

    public boolean passable() {
        return hardViolations == null || hardViolations.isEmpty();
    }

    public static ReferenceLintReport empty() {
        return new ReferenceLintReport(List.of(), List.of());
    }
}
