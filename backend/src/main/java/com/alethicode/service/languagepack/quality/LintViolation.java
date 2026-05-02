package com.alethicode.service.languagepack.quality;

/**
 * 单条 reference solution lint 违规记录。
 *
 * @param ruleCode 规则编号，如 REF001
 * @param severity 严重级别，{@code HARD} 阻塞入库，{@code SOFT} 仅记录
 * @param message  中文说明，给重试 prompt 与人工审核使用
 * @param line     触发位置（1 起，0 表示非行级）
 */
public record LintViolation(
        String ruleCode,
        String severity,
        String message,
        int line
) {
    public static final String HARD = "HARD";
    public static final String SOFT = "SOFT";

    public static LintViolation hard(String ruleCode, String message, int line) {
        return new LintViolation(ruleCode, HARD, message, line);
    }

    public static LintViolation soft(String ruleCode, String message, int line) {
        return new LintViolation(ruleCode, SOFT, message, line);
    }
}
