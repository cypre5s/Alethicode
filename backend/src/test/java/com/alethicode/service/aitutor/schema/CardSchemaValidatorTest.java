package com.alethicode.service.aitutor.schema;

import com.alethicode.service.aitutor.contract.CardType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardSchemaValidatorTest {

    private final CardSchemaValidator validator = new CardSchemaValidator(new CardSchemaRegistry());

    @Test
    void shouldRejectProblemGuidePayloadWhenRequiredFieldMissing() {
        assertThatThrownBy(() -> validator.validateOrThrow(CardType.PROBLEM_GUIDE, Map.of(
                "problem_explanation", "说明",
                "input_translation", "输入",
                "output_translation", "输出",
                "approach_direction", "方向",
                "warmup_question", "问题",
                "courseware_refs", List.of()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("schema violation for problem_guide: plain_task is required");
    }

    @Test
    void shouldAcceptTransferProblemPayloadWhenAllFieldsPresent() {
        assertThatCode(() -> validator.validateOrThrow(CardType.TRANSFER_PROBLEM, Map.of(
                "title", "迁移练习题",
                "description", "描述",
                "input_description", "输入",
                "output_description", "输出",
                "hint", "提示",
                "reference_solution_code", "print(1)",
                "samples", List.of(Map.of("input", "1", "output", "1")),
                "test_cases", List.of(Map.of("input", "1", "output", "1")),
                "target_kcs", List.of("循环")
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptProblemGuidePayloadWhenCoursewareRefsIsEmptyList() {
        assertThatCode(() -> validator.validateOrThrow(CardType.PROBLEM_GUIDE, Map.of(
                "plain_task", "读取两个整数并输出和",
                "problem_explanation", "先完成输入解析，再计算输出",
                "input_translation", "一行两个整数",
                "output_translation", "一个整数结果",
                "approach_direction", "先拆分输入再求和",
                "warmup_question", "输入 1 2 时输出什么？",
                "courseware_refs", List.of()
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptIdeateAnalysisPayloadWhenNoLogicGapAndHintBlank() {
        assertThatCode(() -> validator.validateOrThrow(CardType.IDEATE_ANALYSIS, Map.of(
                "understood_as", "先求总和再除以科目数",
                "step_plan", List.of("读取分数", "求和", "计算平均"),
                "has_logic_gap", false,
                "logic_gap_hint", "",
                "confidence_level", "medium"
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectIdeateAnalysisPayloadWhenHasLogicGapButHintBlank() {
        assertThatThrownBy(() -> validator.validateOrThrow(CardType.IDEATE_ANALYSIS, Map.of(
                "understood_as", "先求总和再除以科目数",
                "step_plan", List.of("读取分数", "求和", "计算平均"),
                "has_logic_gap", true,
                "logic_gap_hint", "",
                "confidence_level", "low"
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("schema violation for ideate_analysis: logic_gap_hint is required");
    }

    @Test
    void shouldAcceptExecutionTraceExplainerWhenReadyPayloadContainsSteps() {
        assertThatCode(() -> validator.validateOrThrow(CardType.EXECUTION_TRACE_EXPLAINER, Map.of(
                "status", "ready",
                "input_sample", "1 2",
                "steps", List.of(Map.of("step_index", 0, "code", "a = 1")),
                "divergence_step", 0,
                "failure_reason", ""
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptExecutionTraceExplainerWhenFailedPayloadContainsReason() {
        assertThatCode(() -> validator.validateOrThrow(CardType.EXECUTION_TRACE_EXPLAINER, Map.of(
                "status", "failed",
                "input_sample", "",
                "steps", List.of(),
                "divergence_step", -1,
                "failure_reason", "当前代码暂时无法生成稳定运行轨迹"
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptFadedExamplePayloadWhenAllFieldsPresent() {
        Map<String, Object> fadedStep = new java.util.LinkedHashMap<>();
        fadedStep.put("step_id", "step_0");
        fadedStep.put("subgoal", "初始化计数器");
        fadedStep.put("code", null);
        fadedStep.put("hint", "需要一个变量来记录个数");
        fadedStep.put("faded", true);

        assertThatCode(() -> validator.validateOrThrow(CardType.FADED_EXAMPLE, Map.of(
                "scaffold_level", "faded",
                "mastery_snapshot", 0.52,
                "fade_ratio", 0.4,
                "steps", List.of(
                        fadedStep,
                        Map.of("step_id", "step_1", "subgoal", "遍历元素", "code", "for num in numbers:", "explanation", "逐个处理列表元素", "faded", false)
                ),
                "student_blanks", List.of("step_0"),
                "validation_status", "pending",
                "step_feedback", List.of()
        ))).doesNotThrowAnyException();
    }

}
