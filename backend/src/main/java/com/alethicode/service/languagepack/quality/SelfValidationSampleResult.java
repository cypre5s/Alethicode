package com.alethicode.service.languagepack.quality;

/**
 * 单个 sample 的 self-validation 结果：sample.output 期望 == reference(sample.input)。
 */
public record SelfValidationSampleResult(
        int index,
        String status,
        String expectedOutput,
        String actualOutput,
        String diff
) {
    public static final String STATUS_AC = "AC";
    public static final String STATUS_WA = "WA";
    public static final String STATUS_NO_MATCH = "NO_MATCH";

    public boolean passed() {
        return STATUS_AC.equals(status);
    }
}
