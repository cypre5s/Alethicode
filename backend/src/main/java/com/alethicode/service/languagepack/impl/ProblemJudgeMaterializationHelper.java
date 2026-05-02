package com.alethicode.service.languagepack.impl;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.Sample;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将标准答案代码通过 judge 执行来物化 samples/test_cases 的 output，
 * 并在部分输入失败时执行定向重生。
 */
final class ProblemJudgeMaterializationHelper {

    private static final Logger log = LoggerFactory.getLogger(ProblemJudgeMaterializationHelper.class);
    private static final int DEFAULT_TIME_LIMIT_MS = 3000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;

    private final LanguagePackProblemJudgeCheckService judgeCheckService;
    private final AiModelGateway aiModelGateway;

    ProblemJudgeMaterializationHelper(LanguagePackProblemJudgeCheckService judgeCheckService,
                                     AiModelGateway aiModelGateway) {
        this.judgeCheckService = judgeCheckService;
        this.aiModelGateway = aiModelGateway;
    }

    /**
     * 用标准答案执行所有 test_cases 输入，物化 output 并回填到题包。
     * 如果部分输入执行失败，尝试定向重生一次。
     * 如果重生后仍失败或 judge 不可用，直接抛异常。
     */
    LanguagePackProblemPackage materializeOutputs(LanguagePackProblemPackage pkg, String primaryLanguage) {
        List<TestCase> testCases = pkg.testCases() == null ? List.of() : pkg.testCases();
        List<Sample> samples = pkg.samples() == null ? List.of() : pkg.samples();
        if (testCases.isEmpty()) {
            throw new MaterializationFailedException("test_cases is empty");
        }
        if (pkg.referenceSolutionCode() == null || pkg.referenceSolutionCode().isBlank()) {
            throw new MaterializationFailedException("reference_solution_code is missing");
        }

        int timeLimitMs = pkg.timeLimit() != null ? pkg.timeLimit() : DEFAULT_TIME_LIMIT_MS;
        int memoryLimitMb = pkg.memoryLimit() != null ? pkg.memoryLimit() : DEFAULT_MEMORY_LIMIT_MB;

        List<String> inputs = testCases.stream().map(TestCase::input).toList();
        JudgeCheckResult result;
        try {
            result = judgeCheckService.executeReferenceSolution(
                    pkg.referenceSolutionCode(), primaryLanguage, inputs, timeLimitMs, memoryLimitMb
            );
        } catch (LanguagePackProblemJudgeCheckService.JudgeUnavailableException ex) {
            throw ex;
        }

        if (!result.compileError().isEmpty()) {
            throw new MaterializationFailedException("compile_error: " + abbreviate(result.compileError(), 400));
        }

        if (result.allPassed()) {
            return overwriteOutputs(pkg, samples, testCases, result);
        }

        // 物化阶段我们写入空的 expected `.out`，judge 把"程序跑通但 stdout 与空预期不一致"
        // 一律标记为 WRONG_ANSWER（resultCode = -1）。这种情形对 reference solution 而言
        // 等价于 "ran cleanly"，我们要做的只是用 actualOutput 覆盖 test_case.output；
        // 仅当出现 RUNTIME_ERROR / TLE / MLE / SYSTEM_ERROR / 编译错误等真正的执行失败
        // 时，才说明 reference 不可用、需要走 layer2 input regen。
        if (allRanCleanly(result)) {
            return overwriteOutputs(pkg, samples, testCases, result);
        }

        log.info("Layer2 partial fail for '{}', attempting input regen. Failed: {}",
                pkg.title(), result.failedIndices());
        for (JudgeCheckResult.CaseResult cr : result.caseResults()) {
            if (!cr.passed()) {
                log.warn("  case[{}] resultCode={}, error='{}', actualOutput='{}'",
                        cr.index(), cr.resultCode(), abbreviate(cr.error(), 200), abbreviate(cr.actualOutput(), 200));
            }
        }

        try {
            return regenerateFailedInputsAndRetry(pkg, primaryLanguage, result, timeLimitMs, memoryLimitMb);
        } catch (LanguagePackProblemJudgeCheckService.JudgeUnavailableException ex) {
            throw ex;
        } catch (Exception layer2Ex) {
            throw new MaterializationFailedException(
                    "layer2_input_regen_failed: " + layer2Ex.getMessage()
                            + " [original failedIndices=" + result.failedIndices() + "]");
        }
    }

    /**
     * 在 validate 阶段，用标准答案重新执行并比对 output。
     * 返回 null 表示全部通过，否则返回错误描述列表。
     */
    List<String> verifyOutputs(LanguagePackProblemPackage pkg, String primaryLanguage) {
        List<TestCase> testCases = pkg.testCases() == null ? List.of() : pkg.testCases();
        if (testCases.isEmpty() || pkg.referenceSolutionCode() == null || pkg.referenceSolutionCode().isBlank()) {
            return List.of();
        }

        int timeLimitMs = pkg.timeLimit() != null ? pkg.timeLimit() : DEFAULT_TIME_LIMIT_MS;
        int memoryLimitMb = pkg.memoryLimit() != null ? pkg.memoryLimit() : DEFAULT_MEMORY_LIMIT_MB;

        List<String> inputs = testCases.stream().map(TestCase::input).toList();
        JudgeCheckResult result;
        try {
            result = judgeCheckService.executeReferenceSolution(
                    pkg.referenceSolutionCode(), primaryLanguage, inputs, timeLimitMs, memoryLimitMb
            );
        } catch (LanguagePackProblemJudgeCheckService.JudgeUnavailableException e) {
            return List.of("Judge server unavailable: " + e.getMessage());
        }

        if (!result.compileError().isEmpty()) {
            return List.of("Reference solution compile error: " + result.compileError());
        }

        Map<String, String> inputToJudgeOutput = new LinkedHashMap<>();
        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < testCases.size() && i < result.caseResults().size(); i++) {
            JudgeCheckResult.CaseResult cr = result.caseResults().get(i);
            // 物化阶段写入空 .out，judge 把 stdout 与空预期不一致一律判 WRONG_ANSWER（resultCode=-1）。
            // 这并不代表 reference 不可用——程序已经成功执行并返回了 actualOutput；我们只需要比较
            // actualOutput 与 test_case.output。仅当出现真正的 RUNTIME_ERROR/TLE/MLE/SYSTEM_ERROR
            // 等执行级失败时，才记录 "execution failed"。
            int code = cr.resultCode();
            boolean ranCleanly = (code == 0 || code == -1);
            if (!ranCleanly) {
                mismatches.add("test_case[" + i + "] execution failed: resultCode=" + code
                        + ", error=" + cr.error());
                continue;
            }
            inputToJudgeOutput.put(testCases.get(i).input(), cr.actualOutput());
            String expected = testCases.get(i).output().strip();
            String actual = cr.actualOutput().strip();
            if (!expected.equals(actual)) {
                mismatches.add("test_case[" + i + "] output mismatch: expected length="
                        + expected.length() + ", actual length=" + actual.length());
            }
        }

        List<Sample> samples = pkg.samples() == null ? List.of() : pkg.samples();
        for (int i = 0; i < samples.size(); i++) {
            String judgeOutput = inputToJudgeOutput.get(samples.get(i).input());
            if (judgeOutput != null && !samples.get(i).output().strip().equals(judgeOutput.strip())) {
                mismatches.add("sample[" + i + "] output mismatch with judge result");
            }
        }
        return mismatches;
    }

    /**
     * 用 judge 结果中 verifyOutputs 发现不一致的项，执行定向重生 + 重新物化。
     * 返回修复后的题包。如果修复失败返回 null。
     */
    LanguagePackProblemPackage attemptRepairForValidation(LanguagePackProblemPackage pkg,
                                                          String primaryLanguage,
                                                          List<String> verifyErrors) {
        List<TestCase> testCases = pkg.testCases() == null ? List.of() : pkg.testCases();
        int timeLimitMs = pkg.timeLimit() != null ? pkg.timeLimit() : DEFAULT_TIME_LIMIT_MS;
        int memoryLimitMb = pkg.memoryLimit() != null ? pkg.memoryLimit() : DEFAULT_MEMORY_LIMIT_MB;

        List<String> inputs = testCases.stream().map(TestCase::input).toList();
        JudgeCheckResult result;
        try {
            result = judgeCheckService.executeReferenceSolution(
                    pkg.referenceSolutionCode(), primaryLanguage, inputs, timeLimitMs, memoryLimitMb
            );
        } catch (Exception e) {
            return null;
        }

        if (!result.compileError().isEmpty()) {
            return null;
        }

        // 同 verifyOutputs/materializeOutputs 的语义：WRONG_ANSWER 仅说明 stdout 与（我们写入的）空
        // .out 不一致，并非 reference 故障；只有 RUNTIME_ERROR/TLE/MLE/SYSTEM_ERROR 才需要 layer2 重生。
        List<Integer> failedIndices = new ArrayList<>();
        for (int i = 0; i < testCases.size() && i < result.caseResults().size(); i++) {
            JudgeCheckResult.CaseResult cr = result.caseResults().get(i);
            int code = cr.resultCode();
            boolean ranCleanly = (code == 0 || code == -1);
            if (!ranCleanly) {
                failedIndices.add(i);
            }
        }

        if (failedIndices.isEmpty()) {
            return overwriteOutputs(pkg, pkg.samples(), testCases, result);
        }

        try {
            pkg = doRegenerateFailedInputs(pkg, primaryLanguage, failedIndices, timeLimitMs, memoryLimitMb);
            return pkg;
        } catch (Exception e) {
            log.warn("Validation repair failed for '{}': {}", pkg.title(), e.getMessage());
            return null;
        }
    }

    // --- internal ---

    /**
     * 判断所有 case 都"成功执行完毕"——即 ACCEPTED (0) 或 WRONG_ANSWER (-1)。
     * 对 materialization 而言，这两种结果都说明程序跑通了，可以从 actualOutput 拿到
     * 确定的 stdout；只有 RUNTIME_ERROR / TLE / MLE / SYSTEM_ERROR 等才意味着 reference
     * 代码本身不可用、需要重生输入。
     */
    private boolean allRanCleanly(JudgeCheckResult result) {
        if (result.caseResults() == null || result.caseResults().isEmpty()) {
            return false;
        }
        for (JudgeCheckResult.CaseResult cr : result.caseResults()) {
            int code = cr.resultCode();
            if (code != 0 && code != -1) {
                return false;
            }
        }
        return true;
    }

    private LanguagePackProblemPackage regenerateFailedInputsAndRetry(
            LanguagePackProblemPackage pkg, String primaryLanguage,
            JudgeCheckResult firstResult, int timeLimitMs, int memoryLimitMb) {
        List<Integer> failedIndices = firstResult.failedIndices();
        return doRegenerateFailedInputs(pkg, primaryLanguage, failedIndices, timeLimitMs, memoryLimitMb);
    }

    private LanguagePackProblemPackage doRegenerateFailedInputs(
            LanguagePackProblemPackage pkg, String primaryLanguage,
            List<Integer> failedIndices, int timeLimitMs, int memoryLimitMb) {

        Map<Integer, String> testCaseReplacements = requestRegeneratedInputs(pkg, primaryLanguage, failedIndices);

        Map<Integer, String> sampleReplacements = buildSampleReplacements(
                pkg.samples(), pkg.testCases(), testCaseReplacements
        );

        pkg = pkg.withReplacedInputs(sampleReplacements, testCaseReplacements);

        List<String> newInputs = pkg.testCases().stream().map(TestCase::input).toList();
        JudgeCheckResult retryResult = judgeCheckService.executeReferenceSolution(
                pkg.referenceSolutionCode(), primaryLanguage, newInputs, timeLimitMs, memoryLimitMb
        );

        if (!retryResult.compileError().isEmpty()) {
            throw new IllegalStateException("Reference solution compile error after input regeneration: " + retryResult.compileError());
        }
        if (!retryResult.allPassed()) {
            for (JudgeCheckResult.CaseResult cr : retryResult.caseResults()) {
                if (!cr.passed()) {
                    log.error("  retry case[{}] resultCode={}, error='{}', actualOutput='{}'",
                            cr.index(), cr.resultCode(), abbreviate(cr.error(), 300), abbreviate(cr.actualOutput(), 300));
                }
            }
            throw new IllegalStateException("Reference solution still fails after input regeneration. Failed indices: " + retryResult.failedIndices());
        }

        return overwriteOutputs(pkg, pkg.samples(), pkg.testCases(), retryResult);
    }

    private Map<Integer, String> requestRegeneratedInputs(LanguagePackProblemPackage pkg,
                                                           String primaryLanguage,
                                                           List<Integer> failedIndices) {
        String systemPrompt = buildInputRegenerationSystemPrompt(primaryLanguage);
        String userPrompt = buildInputRegenerationUserPrompt(pkg, failedIndices);

        Map<String, Object> llmResult = aiModelGateway.callForJson(systemPrompt, userPrompt, "INIT_LLM_REGEN_");

        Object rawList = llmResult.get("regenerated_inputs");
        if (!(rawList instanceof List<?> items)) {
            throw new IllegalStateException("LLM did not return regenerated_inputs array");
        }

        Map<Integer, String> replacements = new LinkedHashMap<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object idxObj = rawMap.get("index");
            Object inputObj = rawMap.get("input");
            if (idxObj instanceof Number num && inputObj != null) {
                replacements.put(num.intValue(), String.valueOf(inputObj).strip());
            }
        }

        for (int idx : failedIndices) {
            if (!replacements.containsKey(idx)) {
                throw new IllegalStateException("LLM did not regenerate input for failed index " + idx);
            }
        }
        return replacements;
    }

    /**
     * 如果某个 test_case 的 input 被替换了，且该 input 同时也是某个 sample 的 input，
     * 则 sample 的 input 也需要同步替换。
     */
    private Map<Integer, String> buildSampleReplacements(List<Sample> samples,
                                                          List<TestCase> testCases,
                                                          Map<Integer, String> testCaseReplacements) {
        if (samples == null || testCases == null) {
            return Map.of();
        }
        Map<Integer, String> sampleReplacements = new LinkedHashMap<>();
        for (int si = 0; si < samples.size(); si++) {
            String sampleInput = samples.get(si).input();
            for (int ti = 0; ti < testCases.size(); ti++) {
                if (testCaseReplacements.containsKey(ti) && testCases.get(ti).input().equals(sampleInput)) {
                    sampleReplacements.put(si, testCaseReplacements.get(ti));
                    break;
                }
            }
        }
        return sampleReplacements;
    }

    private LanguagePackProblemPackage overwriteOutputs(LanguagePackProblemPackage pkg,
                                                        List<Sample> samples,
                                                        List<TestCase> testCases,
                                                        JudgeCheckResult result) {
        Map<String, String> inputToOutput = new LinkedHashMap<>();
        for (int i = 0; i < testCases.size() && i < result.caseResults().size(); i++) {
            inputToOutput.put(testCases.get(i).input(), result.caseResults().get(i).actualOutput());
        }

        List<TestCase> newTestCases = new ArrayList<>();
        for (int i = 0; i < testCases.size() && i < result.caseResults().size(); i++) {
            newTestCases.add(new TestCase(testCases.get(i).input(), result.caseResults().get(i).actualOutput()));
        }

        List<Sample> newSamples = new ArrayList<>();
        for (Sample sample : samples) {
            String judgeOutput = inputToOutput.get(sample.input());
            newSamples.add(new Sample(sample.input(), judgeOutput != null ? judgeOutput : sample.output()));
        }

        return pkg.withOverwrittenOutputs(List.copyOf(newSamples), List.copyOf(newTestCases));
    }

    // --- prompt builders ---

    private String buildInputRegenerationSystemPrompt(String language) {
        return """
                You are fixing invalid test case inputs for a %s OJ problem.
                The reference solution code is correct but some test case inputs cause it to fail at runtime.
                You must generate replacement inputs that are valid for the same problem.
                
                Return strict JSON:
                {
                  "regenerated_inputs": [
                    {"index": 0, "input": "new valid input for that test case"}
                  ]
                }
                
                Rules:
                - Only return entries for the failed indices listed below.
                - Each replacement input must be valid according to the problem's input_description.
                - Do not modify the problem title, description, reference solution, or any metadata.
                - The input must be non-empty and use stdin format.
                - Keep the same difficulty level and edge-case coverage intent as the original input.
                """.formatted(language);
    }

    private String buildInputRegenerationUserPrompt(LanguagePackProblemPackage pkg,
                                                     List<Integer> failedIndices) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Problem title: ").append(pkg.title()).append("\n");
        prompt.append("Input description: ").append(pkg.inputDescription()).append("\n");
        prompt.append("Output description: ").append(pkg.outputDescription()).append("\n\n");
        prompt.append("Reference solution code:\n```\n").append(pkg.referenceSolutionCode()).append("\n```\n\n");
        prompt.append("Current test cases:\n");
        List<TestCase> testCases = pkg.testCases() == null ? List.of() : pkg.testCases();
        for (int i = 0; i < testCases.size(); i++) {
            prompt.append("- test_case[").append(i).append("]: input=").append(testCases.get(i).input()).append("\n");
        }
        prompt.append("\nFailed indices that need new inputs: ").append(failedIndices).append("\n");
        prompt.append("\nReturn only regenerated_inputs for the failed indices.\n");
        return prompt.toString();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
