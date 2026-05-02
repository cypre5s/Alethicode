package com.alethicode.service.languagepack.quality;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceSolutionLinterTest {

    private final ReferenceSolutionLinter linter = new ReferenceSolutionLinter();

    @Test
    void ref001ShouldFlagDirectPrintOfSetVariable() {
        String code = """
                s = {1, 2, 3}
                print(s)
                """;
        ReferenceLintReport report = linter.lint(code, "Python3");

        assertThat(report.passable()).isFalse();
        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .contains("REF001");
    }

    @Test
    void ref002ShouldFlagFloatPrintWithoutPrecisionWhenOutputDescriptionRequiresIt() {
        String code = """
                import math
                r = float(input())
                area = math.pi * r * r
                print(area)
                """;
        ReferenceLintContext context = new ReferenceLintContext(
                "求圆面积",
                "半径 r",
                "输出圆面积，结果保留小数点后 4 位"
        );
        ReferenceLintReport report = linter.lint(code, "Python3", context);

        assertThat(report.passable()).isFalse();
        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .contains("REF002");
    }

    @Test
    void ref003ShouldFlagRandomImportWithoutSeed() {
        String code = """
                import random
                value = random.randint(1, 100)
                print(value)
                """;
        ReferenceLintReport report = linter.lint(code, "Python3");

        assertThat(report.passable()).isFalse();
        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .contains("REF003");
    }

    @Test
    void ref004ShouldFlagMultipleInputCallsWithoutEofGuard() {
        String code = """
                a = int(input())
                b = int(input())
                c = int(input())
                print(a + b + c)
                """;
        ReferenceLintReport report = linter.lint(code, "Python3");

        assertThat(report.passable()).isFalse();
        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .contains("REF004");
    }

    @Test
    void ref007ShouldFlagHalfWidthCommaWhenDescriptionUsesFullWidth() {
        String code = """
                line = input()
                parts = line.split(", ")
                print(parts[0])
                """;
        ReferenceLintContext context = new ReferenceLintContext(
                "输入是若干数字，使用全角逗号分隔",
                "一行字符串",
                "输出第一个数字"
        );
        ReferenceLintReport report = linter.lint(code, "Python3", context);

        assertThat(report.passable()).isFalse();
        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .contains("REF007");
    }

    @Test
    void ref005ShouldRecordSoftMissingMainGuard() {
        String code = """
                x = int(input())
                print(x * 2)
                """;
        ReferenceLintReport report = linter.lint(code, "Python3");

        assertThat(report.passable()).isTrue();
        assertThat(report.softViolations())
                .extracting(LintViolation::ruleCode)
                .contains("REF005");
    }

    @Test
    void ref006ShouldRecordSoftWhenReferenceExceedsSoftLineLimit() {
        StringBuilder code = new StringBuilder("x = 0\n");
        for (int i = 0; i < 65; i++) {
            code.append("x = x + ").append(i).append("\n");
        }
        ReferenceLintReport report = linter.lint(code.toString(), "Python3");

        assertThat(report.softViolations())
                .extracting(LintViolation::ruleCode)
                .contains("REF006");
    }

    @Test
    void shouldNotFlagSetPrintWhenSortedExplicitly() {
        String code = """
                s = {3, 1, 2}
                print(sorted(s))
                """;
        ReferenceLintReport report = linter.lint(code, "Python3");

        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .doesNotContain("REF001");
    }

    @Test
    void shouldNotFlagFloatPrintWhenFstringPrecisionUsed() {
        String code = """
                r = float(input())
                area = 3.14 * r * r
                print(f"{area:.4f}")
                """;
        ReferenceLintContext context = new ReferenceLintContext(
                "求面积", "", "输出保留 4 位小数"
        );
        ReferenceLintReport report = linter.lint(code, "Python3", context);

        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .doesNotContain("REF002");
    }

    @Test
    void shouldNotFlagRandomWhenSeedIsExplicit() {
        String code = """
                import random
                random.seed(42)
                print(random.randint(1, 10))
                """;
        ReferenceLintReport report = linter.lint(code, "Python3");

        assertThat(report.hardViolations())
                .extracting(LintViolation::ruleCode)
                .doesNotContain("REF003");
    }
}
