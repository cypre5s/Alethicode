package com.alethicode.service.languagepack.quality;

import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.Sample;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.TestCase;
import com.alethicode.service.languagepack.impl.JudgeCheckResult;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferenceSolutionSelfValidatorTest {

    private final ReferenceSolutionLinter linter = new ReferenceSolutionLinter();

    @Test
    void shouldReturnAllPassedWhenJudgeReturnsAcForAllCases() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(true, List.of(
                        new JudgeCheckResult.CaseResult(0, true, "78.5398", "", 0),
                        new JudgeCheckResult.CaseResult(1, true, "3.1416", "", 0)
                ), ""));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT2-1",
                "import math\nr = float(input())\nprint(f\"{math.pi*r*r:.4f}\")",
                List.of(new TestCase("5", "78.5398"), new TestCase("1", "3.1416")),
                List.of(new Sample("5", "78.5398"))
        ));

        assertThat(report.allPassed()).isTrue();
        assertThat(report.testCaseResults()).hasSize(2)
                .allSatisfy(c -> assertThat(c.status()).isEqualTo(SelfValidationCaseResult.STATUS_AC));
        assertThat(report.sampleResults()).hasSize(1);
        assertThat(report.sampleResults().get(0).status()).isEqualTo(SelfValidationSampleResult.STATUS_AC);
        assertThat(report.failureSummary()).isEmpty();
    }

    @Test
    void shouldClassifyMismatchAsWaWithDiff() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(false, List.of(
                        new JudgeCheckResult.CaseResult(0, false, "actual-output", "", -1)
                ), ""));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT3-1",
                "x = int(input())\nprint(x)",
                List.of(new TestCase("1", "expected-output")),
                List.of()
        ));

        assertThat(report.allPassed()).isFalse();
        assertThat(report.testCaseResults().get(0).status()).isEqualTo(SelfValidationCaseResult.STATUS_WA);
        assertThat(report.testCaseResults().get(0).diff()).contains("expected=").contains("actual=");
        assertThat(report.failureSummary()).isPresent();
    }

    @Test
    void shouldClassifyWrongAnswerWithMatchingStrippedOutputsAsAc() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        // judge 用空 .out 比对，program 输出 "78.5398\n"，所以 resultCode=-1，但 strip 后等于 expected。
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(false, List.of(
                        new JudgeCheckResult.CaseResult(0, false, "78.5398\n", "", -1),
                        new JudgeCheckResult.CaseResult(1, false, "3.1416", "", -1)
                ), ""));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT-WAtoAC",
                "import math\nr = float(input())\nprint(f\"{math.pi*r*r:.4f}\")",
                List.of(new TestCase("5", "78.5398"), new TestCase("1", "3.1416")),
                List.of(new Sample("5", "78.5398"))
        ));

        assertThat(report.allPassed())
                .as("strip 后字符串一致就应当算 AC，即便 judge 因空 expected 文件回 WRONG_ANSWER")
                .isTrue();
        assertThat(report.testCaseResults())
                .extracting(SelfValidationCaseResult::status)
                .containsOnly(SelfValidationCaseResult.STATUS_AC);
    }

    @Test
    void shouldClassifyRuntimeErrorAsRe() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(false, List.of(
                        new JudgeCheckResult.CaseResult(0, false, "", "IndexError", 7)
                ), ""));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT4-1",
                "data = input().split()\nprint(data[5])",
                List.of(new TestCase("1 2", "should-not-be-checked")),
                List.of()
        ));

        assertThat(report.testCaseResults().get(0).status()).isEqualTo(SelfValidationCaseResult.STATUS_RE);
    }

    @Test
    void shouldClassifyTleAsTle() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(false, List.of(
                        new JudgeCheckResult.CaseResult(0, false, "", "Time Limit Exceeded", 2)
                ), ""));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT5-1",
                "while True:\n    pass",
                List.of(new TestCase("1", "anything")),
                List.of()
        ));

        assertThat(report.testCaseResults().get(0).status()).isEqualTo(SelfValidationCaseResult.STATUS_TLE);
    }

    @Test
    void shouldShortCircuitWhenLintHardViolationFound() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        // import random + 无 seed 触发 REF003 硬规则
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT-RANDOM",
                "import random\nprint(random.randint(1, 10))",
                List.of(new TestCase("1", "6")),
                List.of()
        ));

        assertThat(report.allPassed()).isFalse();
        assertThat(report.lintBlocked()).isTrue();
        assertThat(report.testCaseResults()).isEmpty();
        // judge 不应被调用
        verify(judge, org.mockito.Mockito.never()).executeReferenceSolution(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldDetectSampleMismatchEvenWhenAllTestCasesPass() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(true, List.of(
                        new JudgeCheckResult.CaseResult(0, true, "REAL_OUTPUT", "", 0)
                ), ""));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT-SAMPLE-DIFF",
                "x = int(input())\nprint(x)",
                List.of(new TestCase("input1", "REAL_OUTPUT")),
                List.of(new Sample("input1", "WRONG_SAMPLE_OUTPUT"))
        ));

        assertThat(report.allPassed()).isFalse();
        assertThat(report.sampleResults().get(0).status()).isEqualTo(SelfValidationSampleResult.STATUS_WA);
    }

    @Test
    void shouldHandleCompileErrorWithoutCallingDiff() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(JudgeCheckResult.compileFailure("SyntaxError on line 2"));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT-COMPILE",
                "print(",
                List.of(new TestCase("1", "x")),
                List.of()
        ));

        assertThat(report.allPassed()).isFalse();
        assertThat(report.compileFailed()).isTrue();
        assertThat(report.compileError()).contains("SyntaxError on line 2");
    }

    @Test
    void shouldReportFailureWhenJudgeUnavailable() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenThrow(new LanguagePackProblemJudgeCheckService.JudgeUnavailableException("no server"));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        SelfValidationReport report = validator.validate(buildPkg(
                "PPT-NO-JUDGE",
                "print(input())",
                List.of(new TestCase("1", "1")),
                List.of()
        ));

        assertThat(report.allPassed()).isFalse();
        assertThat(report.failureSummary()).isPresent()
                .hasValueSatisfying(s -> assertThat(s).contains("不可用"));
    }

    @Test
    void shouldPassPackageTimeAndMemoryLimitsToJudge() {
        LanguagePackProblemJudgeCheckService judge = mock(LanguagePackProblemJudgeCheckService.class);
        when(judge.executeReferenceSolution(anyString(), anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(true, List.of(
                        new JudgeCheckResult.CaseResult(0, true, "1", "", 0)
                ), ""));

        ReferenceSolutionSelfValidator validator = new ReferenceSolutionSelfValidator(linter, judge);
        LanguagePackProblemPackage pkg = new LanguagePackProblemPackage(
                "PPT-LIM",
                "title",
                "desc",
                "input",
                "output",
                List.of(),
                List.of(new TestCase("0", "1")),
                Map.of("Python3", "print(1)"),
                5000,
                512,
                "Low",
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                null,
                "Python3",
                "print(1)"
        );

        validator.validate(pkg);

        ArgumentCaptor<Integer> timeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> memCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(judge).executeReferenceSolution(eq("print(1)"), eq("Python3"), anyList(),
                timeCaptor.capture(), memCaptor.capture());
        assertThat(timeCaptor.getValue()).isEqualTo(5000);
        assertThat(memCaptor.getValue()).isEqualTo(512);
    }

    private LanguagePackProblemPackage buildPkg(String displayId,
                                                String referenceCode,
                                                List<TestCase> testCases,
                                                List<Sample> samples) {
        return new LanguagePackProblemPackage(
                displayId,
                "title",
                "description",
                "input description",
                "output description",
                samples,
                testCases,
                Map.of("Python3", referenceCode),
                3000,
                256,
                "Low",
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                null,
                "Python3",
                referenceCode
        );
    }
}
