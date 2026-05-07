package com.alethicode.service.languagepack.quality;

import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.Sample;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.TestCase;
import com.alethicode.service.languagepack.impl.JudgeCheckResult;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reference solution × test_cases × samples 的运行期自验证。
 *
 * <p>调用顺序：</p>
 * <ol>
 *   <li>{@link ReferenceSolutionLinter} 静态 lint。硬规则违反则直接返回 {@code lintBlocked}，不再调 judge。</li>
 *   <li>{@link LanguagePackProblemJudgeCheckService#executeReferenceSolution} 跑 reference × test_cases。</li>
 *   <li>把 judge 的 actualOutput 与题包中 expected_output 比对，输出 {@link SelfValidationCaseResult}。</li>
 *   <li>对每个 sample 用 input 反查 test_case 的 actualOutput，得到 {@link SelfValidationSampleResult}。</li>
 * </ol>
 */
@Service
public class ReferenceSolutionSelfValidator {

    private static final Logger log = LoggerFactory.getLogger(ReferenceSolutionSelfValidator.class);
    private static final int DEFAULT_TIME_LIMIT_MS = 3000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final int DIFF_PREVIEW_LIMIT = 200;
    private static final int FAILURE_SUMMARY_PREVIEW = 3;

    private final ReferenceSolutionLinter linter;
    private final LanguagePackProblemJudgeCheckService judgeCheckService;

    public ReferenceSolutionSelfValidator(ReferenceSolutionLinter linter,
                                          LanguagePackProblemJudgeCheckService judgeCheckService) {
        this.linter = linter;
        this.judgeCheckService = judgeCheckService;
    }

    public SelfValidationReport validate(LanguagePackProblemPackage pkg) {
        if (pkg == null) {
            throw new IllegalArgumentException("LanguagePackProblemPackage 不能为空");
        }
        Instant start = Instant.now();
        String displayId = pkg.displayId() == null ? "" : pkg.displayId();

        ReferenceLintContext context = new ReferenceLintContext(
                pkg.description(), pkg.inputDescription(), pkg.outputDescription()
        );
        ReferenceLintReport lintReport = linter.lint(
                pkg.referenceSolutionCode(),
                pkg.referenceSolutionLanguage(),
                context
        );

        if (!lintReport.passable()) {
            return new SelfValidationReport(
                    displayId,
                    false,
                    List.of(),
                    List.of(),
                    lintReport,
                    Optional.of(buildLintFailureSummary(lintReport)),
                    Optional.empty(),
                    Duration.between(start, Instant.now())
            );
        }

        List<TestCase> testCases = pkg.testCases() == null ? List.of() : pkg.testCases();
        if (testCases.isEmpty()) {
            return new SelfValidationReport(
                    displayId,
                    false,
                    List.of(),
                    List.of(),
                    lintReport,
                    Optional.of("test_cases 为空，无法 self-validate"),
                    Optional.empty(),
                    Duration.between(start, Instant.now())
            );
        }
        if (pkg.referenceSolutionCode() == null || pkg.referenceSolutionCode().isBlank()) {
            return new SelfValidationReport(
                    displayId,
                    false,
                    List.of(),
                    List.of(),
                    lintReport,
                    Optional.of("reference_solution_code 为空，无法 self-validate"),
                    Optional.empty(),
                    Duration.between(start, Instant.now())
            );
        }

        int timeLimitMs = pkg.timeLimit() != null ? pkg.timeLimit() : DEFAULT_TIME_LIMIT_MS;
        int memoryLimitMb = pkg.memoryLimit() != null ? pkg.memoryLimit() : DEFAULT_MEMORY_LIMIT_MB;
        String language = pkg.referenceSolutionLanguage() == null
                ? "Python3" : pkg.referenceSolutionLanguage();

        List<String> inputs = testCases.stream().map(TestCase::input).toList();
        JudgeCheckResult judgeResult;
        try {
            judgeResult = judgeCheckService.executeReferenceSolution(
                    pkg.referenceSolutionCode(), language, inputs, timeLimitMs, memoryLimitMb
            );
        } catch (LanguagePackProblemJudgeCheckService.JudgeUnavailableException ex) {
            return new SelfValidationReport(
                    displayId,
                    false,
                    List.of(),
                    List.of(),
                    lintReport,
                    Optional.of("Judge server 不可用：" + ex.getMessage()),
                    Optional.empty(),
                    Duration.between(start, Instant.now())
            );
        }

        if (judgeResult.compileError() != null && !judgeResult.compileError().isEmpty()) {
            return new SelfValidationReport(
                    displayId,
                    false,
                    List.of(),
                    List.of(),
                    lintReport,
                    Optional.of("Reference solution 编译失败"),
                    Optional.of(judgeResult.compileError()),
                    Duration.between(start, Instant.now())
            );
        }

        List<SelfValidationCaseResult> caseResults = buildCaseResults(testCases, judgeResult);
        Map<String, String> inputToActual = new LinkedHashMap<>();
        for (int i = 0; i < testCases.size() && i < caseResults.size(); i++) {
            inputToActual.put(testCases.get(i).input(), caseResults.get(i).actualOutput());
        }

        List<Sample> samples = pkg.samples() == null ? List.of() : pkg.samples();
        List<SelfValidationSampleResult> sampleResults = buildSampleResults(samples, inputToActual);

        boolean allPassed = caseResults.stream().allMatch(SelfValidationCaseResult::passed)
                && sampleResults.stream().allMatch(SelfValidationSampleResult::passed);
        Optional<String> failureSummary = allPassed
                ? Optional.empty()
                : Optional.of(buildRunFailureSummary(caseResults, sampleResults));

        return new SelfValidationReport(
                displayId,
                allPassed,
                caseResults,
                sampleResults,
                lintReport,
                failureSummary,
                Optional.empty(),
                Duration.between(start, Instant.now())
        );
    }

    private List<SelfValidationCaseResult> buildCaseResults(List<TestCase> testCases,
                                                            JudgeCheckResult judgeResult) {
        List<SelfValidationCaseResult> results = new ArrayList<>();
        List<JudgeCheckResult.CaseResult> raw = judgeResult.caseResults() == null
                ? List.of() : judgeResult.caseResults();
        for (int i = 0; i < testCases.size(); i++) {
            String caseKey = String.valueOf(i + 1);
            String expected = strip(testCases.get(i).output());
            if (i >= raw.size()) {
                results.add(new SelfValidationCaseResult(
                        caseKey,
                        SelfValidationCaseResult.STATUS_RE,
                        expected,
                        "",
                        "Judge 未返回此 case 的结果",
                        -1
                ));
                continue;
            }
            JudgeCheckResult.CaseResult cr = raw.get(i);
            String actual = strip(cr.actualOutput());
            // judge 在 self-validation 阶段使用临时 testcase 目录，expected `.out` 写空，
            // 因此程序输出非空时 resultCode 必然为 -1 (WRONG_ANSWER)。这并不代表 reference
            // 真的不可用——我们应当用题包里的 expected_output 与 strip(actual) 再比一次：
            // 仅当出现 TLE/MLE/RE 等执行级别的非零码时，才按对应分类记录。
            if (cr.passed() || cr.resultCode() == -1) {
                if (expected.equals(actual)) {
                    results.add(new SelfValidationCaseResult(
                            caseKey,
                            SelfValidationCaseResult.STATUS_AC,
                            expected,
                            actual,
                            "",
                            cr.resultCode()
                    ));
                } else {
                    results.add(new SelfValidationCaseResult(
                            caseKey,
                            SelfValidationCaseResult.STATUS_WA,
                            expected,
                            actual,
                            diffPreview(expected, actual),
                            cr.resultCode()
                    ));
                }
            } else if (cr.resultCode() == 1 || cr.resultCode() == 2 || cr.resultCode() == 4) {
                results.add(new SelfValidationCaseResult(
                        caseKey,
                        SelfValidationCaseResult.STATUS_TLE,
                        expected,
                        actual,
                        truncate(cr.error(), DIFF_PREVIEW_LIMIT),
                        cr.resultCode()
                ));
            } else if (cr.resultCode() == 3) {
                results.add(new SelfValidationCaseResult(
                        caseKey,
                        SelfValidationCaseResult.STATUS_OLE,
                        expected,
                        actual,
                        truncate(cr.error(), DIFF_PREVIEW_LIMIT),
                        cr.resultCode()
                ));
            } else {
                results.add(new SelfValidationCaseResult(
                        caseKey,
                        SelfValidationCaseResult.STATUS_RE,
                        expected,
                        actual,
                        truncate(cr.error(), DIFF_PREVIEW_LIMIT),
                        cr.resultCode()
                ));
            }
        }
        return results;
    }

    private List<SelfValidationSampleResult> buildSampleResults(List<Sample> samples,
                                                                Map<String, String> inputToActual) {
        List<SelfValidationSampleResult> results = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            String expected = strip(sample.output());
            String actual = inputToActual.get(sample.input());
            if (actual == null) {
                results.add(new SelfValidationSampleResult(
                        i,
                        SelfValidationSampleResult.STATUS_NO_MATCH,
                        expected,
                        "",
                        "sample.input 未在 test_cases 中找到对应项；无法用 reference 验证"
                ));
                continue;
            }
            String actualStripped = strip(actual);
            if (expected.equals(actualStripped)) {
                results.add(new SelfValidationSampleResult(
                        i,
                        SelfValidationSampleResult.STATUS_AC,
                        expected,
                        actualStripped,
                        ""
                ));
            } else {
                results.add(new SelfValidationSampleResult(
                        i,
                        SelfValidationSampleResult.STATUS_WA,
                        expected,
                        actualStripped,
                        diffPreview(expected, actualStripped)
                ));
            }
        }
        return results;
    }

    private String buildLintFailureSummary(ReferenceLintReport report) {
        StringBuilder sb = new StringBuilder("Reference solution 未通过静态 lint：");
        int count = 0;
        for (LintViolation v : report.hardViolations()) {
            if (count++ > 0) {
                sb.append("；");
            }
            sb.append('[').append(v.ruleCode()).append("] ").append(v.message());
            if (v.line() > 0) {
                sb.append("（line ").append(v.line()).append("）");
            }
            if (count >= FAILURE_SUMMARY_PREVIEW) {
                break;
            }
        }
        return sb.toString();
    }

    private String buildRunFailureSummary(List<SelfValidationCaseResult> caseResults,
                                          List<SelfValidationSampleResult> sampleResults) {
        StringBuilder sb = new StringBuilder("Self-validation failed: ");
        int shown = 0;
        for (SelfValidationCaseResult cr : caseResults) {
            if (cr.passed()) {
                continue;
            }
            if (shown++ > 0) {
                sb.append("; ");
            }
            sb.append("test_case[").append(cr.caseKey()).append("]=").append(cr.status());
            if (!cr.diff().isEmpty()) {
                sb.append(" diff=").append(truncate(cr.diff(), 100));
            }
            if (shown >= FAILURE_SUMMARY_PREVIEW) {
                break;
            }
        }
        if (shown == 0) {
            for (SelfValidationSampleResult sr : sampleResults) {
                if (sr.passed()) {
                    continue;
                }
                if (shown++ > 0) {
                    sb.append("; ");
                }
                sb.append("sample[").append(sr.index()).append("]=").append(sr.status());
                if (sr.diff() != null && !sr.diff().isEmpty()) {
                    sb.append(" diff=").append(truncate(sr.diff(), 100));
                }
                if (shown >= FAILURE_SUMMARY_PREVIEW) {
                    break;
                }
            }
        }
        return sb.toString();
    }

    private String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private String diffPreview(String expected, String actual) {
        return "expected=" + truncate(expected, DIFF_PREVIEW_LIMIT)
                + " actual=" + truncate(actual, DIFF_PREVIEW_LIMIT);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
