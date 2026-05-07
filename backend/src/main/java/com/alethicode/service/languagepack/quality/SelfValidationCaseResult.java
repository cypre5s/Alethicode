package com.alethicode.service.languagepack.quality;

/**
 * 单个 test_case 的 self-validation 结果。
 *
 * @param caseKey       case 标识（通常是 1 起的序号字符串）
 * @param status AC、WA、RE、TLE 或 OLE
 * @param expectedOutput 题包内 expected_output（已 strip）
 * @param actualOutput  reference 实际输出（已 strip）
 * @param diff          WA 时给前 200 字符 diff 描述
 * @param resultCode    Judge 返回的原始 result code
 */
public record SelfValidationCaseResult(
        String caseKey,
        String status,
        String expectedOutput,
        String actualOutput,
        String diff,
        int resultCode
) {
    public static final String STATUS_AC = "AC";
    public static final String STATUS_WA = "WA";
    public static final String STATUS_RE = "RE";
    public static final String STATUS_TLE = "TLE";
    public static final String STATUS_OLE = "OLE";

    public boolean passed() {
        return STATUS_AC.equals(status);
    }
}
