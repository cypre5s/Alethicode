package com.alethicode.service.aitutor.reflection;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReflectionServiceImpl} 单测 —— 覆盖 plan
 * career-bridging-closure_e71ce32e.plan.md 第 3.4 节扩展的 4 个新 CardType
 * 的 critic rubric 合约：CAREER_BRIDGING / DOMAIN_VARIANT / MICRO_PROJECT_BRIEF
 * / CAREER_PATH_NODE。每个 CardType 的 rubric 关键短语必须出现在
 * AiModelGateway 收到的 system prompt 里，否则就是 rubric 漂移。
 *
 * <p>侧重点：(a) rubric 内容合约（守住 4 个 CardType 的 4 维评分维度短语）；
 * (b) reflectAndRefine 主流程 pass / fail 行为；(c) DOMAIN_VARIANT 的
 * IO schema + 测试样例语义不偏移强约束必须显式出现。
 */
@ExtendWith(MockitoExtension.class)
class ReflectionServiceImplTest {

    @Mock
    private AiModelGateway aiModelGateway;

    private ReflectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReflectionServiceImpl(aiModelGateway, new ObjectMapper());
    }

    @Test
    void careerBridgingRubricMustEnforceCitationsAndForbidFabricatedFacts() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiModelGateway.callForJson(systemPromptCaptor.capture(), anyString()))
                .thenReturn(passResult());

        service.reflectAndRefine(CardType.CAREER_BRIDGING,
                Map.of("major_dictionary", "biology"),
                Map.of("title", "x"), 1);

        String systemPrompt = systemPromptCaptor.getAllValues().get(0);
        assertThat(systemPrompt)
                .contains("major_dictionary")
                .contains("learner_state")
                .contains("citations")
                .contains("不得编造")
                .contains("Why 层");
    }

    @Test
    void domainVariantRubricMustEnforceIoSchemaUnchangedAndSemanticsUnchanged() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiModelGateway.callForJson(systemPromptCaptor.capture(), anyString()))
                .thenReturn(passResult());

        service.reflectAndRefine(CardType.DOMAIN_VARIANT,
                Map.of("problem_id", 1L),
                Map.of("title", "x"), 1);

        String systemPrompt = systemPromptCaptor.getAllValues().get(0);
        assertThat(systemPrompt)
                .as("DOMAIN_VARIANT critic rubric 必须强制 IO schema 不变 + 测试样例语义不偏移，"
                        + "并要求 verification.input_schema_unchanged / verification.semantics_unchanged 自报告")
                .contains("IO schema 不变")
                .contains("测试样例语义不偏移")
                .contains("input_schema_unchanged")
                .contains("semantics_unchanged")
                .contains("abort");
    }

    @Test
    void microProjectBriefRubricMustEnforceMajorRelevanceAndKcAlignmentAndStdLib() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiModelGateway.callForJson(systemPromptCaptor.capture(), anyString()))
                .thenReturn(passResult());

        service.reflectAndRefine(CardType.MICRO_PROJECT_BRIEF,
                Map.of("major_code", "biology"),
                Map.of("title", "x"), 1);

        String systemPrompt = systemPromptCaptor.getAllValues().get(0);
        assertThat(systemPrompt)
                .contains("专业相关性")
                .contains("KC 对齐")
                .contains("mastered_kcs")
                .contains("Python 标准库")
                .contains("reference_solution")
                .contains("test_cases");
    }

    @Test
    void careerPathNodeRubricMustEnforceFactualConsistencyFromMajorDictionary() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiModelGateway.callForJson(systemPromptCaptor.capture(), anyString()))
                .thenReturn(passResult());

        service.reflectAndRefine(CardType.CAREER_PATH_NODE,
                Map.of("major_code", "biology"),
                Map.of("kc_code", "loop"), 1);

        String systemPrompt = systemPromptCaptor.getAllValues().get(0);
        assertThat(systemPrompt)
                .contains("why_md")
                .contains("major_dictionary")
                .contains("typical_use_cases")
                .contains("不得编造");
    }

    @Test
    void reflectAndRefineReturnsImmediatelyWhenCriticPasses() {
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(passResult());

        ReflectionResult result = service.reflectAndRefine(CardType.CAREER_BRIDGING,
                Map.of(), Map.of("title", "ok"), 2);

        assertThat(result.passed()).isTrue();
        assertThat(result.roundsUsed()).isEqualTo(1);
        verify(aiModelGateway, times(1)).callForJson(anyString(), anyString());
    }

    @Test
    void reflectAndRefineRefinesWhenCriticFailsThenAcceptsSecondPass() {
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(failResult("missing citations"))   // round 1 critic
                .thenReturn(refinedOutput())                    // round 1 refine
                .thenReturn(passResult());                      // round 2 critic

        // 流程：critic1 失败 → refine1 → round 2 critic 通过 → 直接返回。
        // passed 表示「最终一次 critic 是否通过」，所以这里 = true；
        // 是否「第一次就过」由 roundsUsed == 1 && passed 表达。
        ReflectionResult result = service.reflectAndRefine(CardType.DOMAIN_VARIANT,
                Map.of(), Map.of("title", "init"), 2);

        assertThat(result.passed()).isTrue();
        assertThat(result.roundsUsed()).isEqualTo(2);
        // 共 3 次 LLM 调用：critic1（失败）+ refine1 + critic2（通过）
        verify(aiModelGateway, times(3)).callForJson(anyString(), anyString());
    }

    private static Map<String, Object> passResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", true);
        result.put("verdict", "all dimensions ok");
        result.put("feedback", "");
        return result;
    }

    private static Map<String, Object> failResult(String feedback) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", false);
        result.put("verdict", "needs revision");
        result.put("feedback", feedback);
        return result;
    }

    private static Map<String, Object> refinedOutput() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "refined");
        return result;
    }
}
