package com.alethicode.service.languagepack.impl;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.Sample;
import com.alethicode.service.languagepack.LanguagePackProblemPackage.TestCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link ProblemJudgeMaterializationHelper} 在物化 reference_solution_code 输出时的契约。
 *
 * <p>核心场景：判题机用我们写入的（空）.out 文件做比对，所以即便 reference 代码完美执行并
 * 产出确定的 stdout，judge 也会回 {@code resultCode=-1} (WRONG_ANSWER)。物化阶段应当把这种
 * "代码跑通但与空预期不匹配" 解读为"成功执行"，并用 {@code actualOutput} 直接覆盖 test_case
 * 输出；只有真正的 RUNTIME_ERROR / TLE / MLE / SYSTEM_ERROR / 编译错误才意味着 reference
 * 代码不可用、需要重生输入。
 */
class ProblemJudgeMaterializationHelperTest {

    private static final String LANGUAGE = "Python3";

    private final LanguagePackProblemJudgeCheckService judgeCheckService =
            Mockito.mock(LanguagePackProblemJudgeCheckService.class);
    private final AiModelGateway aiModelGateway = Mockito.mock(AiModelGateway.class);
    private final ProblemJudgeMaterializationHelper helper =
            new ProblemJudgeMaterializationHelper(judgeCheckService, aiModelGateway);

    @Test
    void allWrongAnswerWithCleanRunsMaterializesOutputsWithoutInputRegen() {
        LanguagePackProblemPackage pkg = pkgWith(
                List.of(new Sample("3", "")),
                List.of(new TestCase("3", ""), new TestCase("5", ""))
        );

        // resultCode = -1 表示 WRONG_ANSWER：程序跑通了，但 stdout 与（我们写入的空）.out 不一致。
        // Materialization 必须接受这种结果，用 actualOutput 物化输出，而不是触发 input regen。
        Mockito.when(judgeCheckService.executeReferenceSolution(
                Mockito.any(), Mockito.eq(LANGUAGE), Mockito.anyList(),
                Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(new JudgeCheckResult(
                        false,
                        List.of(
                                new JudgeCheckResult.CaseResult(0, false, "6", "", -1),
                                new JudgeCheckResult.CaseResult(1, false, "120", "", -1)
                        ),
                        ""
                ));

        LanguagePackProblemPackage materialized = helper.materializeOutputs(pkg, LANGUAGE);

        assertThat(materialized.testCases())
                .extracting(TestCase::output)
                .containsExactly("6", "120");
        assertThat(materialized.samples().getFirst().output()).isEqualTo("6");

        // 关键：不应触发 LLM 输入重生。
        Mockito.verifyNoInteractions(aiModelGateway);
    }

    @Test
    void verifyOutputsTreatsWrongAnswerAsCleanRunAndComparesStringOutputs() {
        LanguagePackProblemPackage pkg = pkgWith(
                List.of(new Sample("3", "6")),
                List.of(new TestCase("3", "6"), new TestCase("5", "120"))
        );

        Mockito.when(judgeCheckService.executeReferenceSolution(
                Mockito.any(), Mockito.eq(LANGUAGE), Mockito.anyList(),
                Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(new JudgeCheckResult(
                        false,
                        List.of(
                                new JudgeCheckResult.CaseResult(0, false, "6", "", -1),
                                new JudgeCheckResult.CaseResult(1, false, "120", "", -1)
                        ),
                        ""
                ));

        List<String> mismatches = helper.verifyOutputs(pkg, LANGUAGE);

        assertThat(mismatches)
                .as("WRONG_ANSWER 是因为我们传给 judge 空 .out；只要 actualOutput 与 test_case.output 一致就算通过")
                .isEmpty();
    }

    @Test
    void verifyOutputsReportsRealMismatchEvenWhenCodeRunsCleanly() {
        LanguagePackProblemPackage pkg = pkgWith(
                List.of(new Sample("3", "6")),
                List.of(new TestCase("3", "6"), new TestCase("5", "120"))
        );

        Mockito.when(judgeCheckService.executeReferenceSolution(
                Mockito.any(), Mockito.eq(LANGUAGE), Mockito.anyList(),
                Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(new JudgeCheckResult(
                        false,
                        List.of(
                                new JudgeCheckResult.CaseResult(0, false, "6", "", -1),
                                new JudgeCheckResult.CaseResult(1, false, "999", "", -1)
                        ),
                        ""
                ));

        List<String> mismatches = helper.verifyOutputs(pkg, LANGUAGE);

        assertThat(mismatches).hasSize(1);
        assertThat(mismatches.getFirst()).contains("test_case[1]").contains("output mismatch");
    }

    @Test
    void runtimeErrorPathStillTriggersInputRegen() {
        LanguagePackProblemPackage pkg = pkgWith(
                List.of(new Sample("3", "")),
                List.of(new TestCase("3", ""), new TestCase("5", ""))
        );

        Mockito.when(judgeCheckService.executeReferenceSolution(
                Mockito.any(), Mockito.eq(LANGUAGE), Mockito.anyList(),
                Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(new JudgeCheckResult(
                        false,
                        List.of(
                                // 4 = RUNTIME_ERROR：reference 代码本身无法跑通，需走 layer2 重生输入。
                                new JudgeCheckResult.CaseResult(0, false, "", "ZeroDivisionError", 4),
                                new JudgeCheckResult.CaseResult(1, false, "", "ZeroDivisionError", 4)
                        ),
                        ""
                ));

        // 让 LLM 重生输入这一步抛错，便于断言确实进入了 layer2 路径而不是被静默 swallow。
        Mockito.when(aiModelGateway.callForJson(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new IllegalStateException("layer2 regen invoked"));

        assertThatThrownBy(() -> helper.materializeOutputs(pkg, LANGUAGE))
                .isInstanceOf(MaterializationFailedException.class)
                .hasMessageContaining("layer2_input_regen_failed");

        Mockito.verify(aiModelGateway, Mockito.atLeastOnce())
                .callForJson(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    private LanguagePackProblemPackage pkgWith(List<Sample> samples, List<TestCase> testCases) {
        return new LanguagePackProblemPackage(
                "p-1",
                "Sum two numbers",
                "Compute n!",
                "n",
                "n!",
                samples,
                testCases,
                Map.of(LANGUAGE, "def main():\n    pass\n"),
                1000,
                256,
                "Low",
                List.of(1),
                List.of(),
                List.of(),
                "fact for beginners",
                List.of(),
                42L,
                LANGUAGE,
                "import sys\nprint(...)\n"
        );
    }
}
