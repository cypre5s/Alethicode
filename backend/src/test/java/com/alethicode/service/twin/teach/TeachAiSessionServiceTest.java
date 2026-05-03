package com.alethicode.service.twin.teach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeachAiSessionServiceTest {

    private final TeachAiSessionService service = new TeachAiSessionService(null);

    // ===== gradeExplanation: 空 / 极短 =====
    @Test void gradeEmptyExplanation() { assertThat((int) service.gradeExplanation("", "test").get("total_score")).isEqualTo(0); }
    @Test void gradeSingleChar() { assertThat((int) service.gradeExplanation("不", "test").get("total_score")).isLessThan(15); }
    @Test void gradeOneWord() { assertThat((int) service.gradeExplanation("不对", "test").get("total_score")).isLessThan(20); }

    // ===== gradeExplanation: 短解释 =====
    @Test void gradeShortWithReason() {
        int s = (int) service.gradeExplanation("因为这样不对", "test").get("total_score");
        assertThat(s).isBetween(5, 30);
    }
    @Test void gradeShortWithCorrection() {
        int s = (int) service.gradeExplanation("其实应该是从0开始", "test").get("total_score");
        assertThat(s).isBetween(5, 35);
    }

    // ===== gradeExplanation: 中等解释 =====
    @Test void gradeMediumWithBecause() {
        int s = (int) service.gradeExplanation("这个不对，因为 range(n) 其实是从 0 开始到 n-1 的", "range(n) 从 1 开始").get("total_score");
        assertThat(s).isBetween(20, 60);
    }
    @Test void gradeMediumWithExample() {
        int s = (int) service.gradeExplanation("这个不对。比如 range(3) 会生成 0,1,2 而不是 1,2,3", "range(n) 从 1 开始").get("total_score");
        assertThat(s).isBetween(25, 70);
    }

    // ===== gradeExplanation: 详细解释 =====
    @Test void gradeDetailedExplanation() {
        String ex = """
            这个理解不对哦！range(n) 其实是从 0 开始的，到 n-1 结束。
            因为 Python 的索引是从 0 开始的，range 也遵循这个设计。
            比如 range(5) 会生成 [0, 1, 2, 3, 4]，而不是 [1, 2, 3, 4, 5]。
            你可以试试在终端里 print(list(range(5))) 看看结果。
            举个例子，如果你要遍历一个长度为 3 的列表，
            应该写 for i in range(3)，这样 i 会是 0, 1, 2。
            """;
        int s = (int) service.gradeExplanation(ex, "range(n) 从 1 开始").get("total_score");
        assertThat(s).isGreaterThanOrEqualTo(55);
    }

    @Test void gradeWithCodeSnippet() {
        String ex = "不对，应该是从 0 开始。```print(list(range(3)))``` 输出 [0,1,2]。因为 Python 是零索引的。";
        int s = (int) service.gradeExplanation(ex, "range(n) 从 1 开始").get("total_score");
        assertThat(s).isGreaterThanOrEqualTo(30);
    }

    @Test void gradeWithPrintKeyword() {
        String ex = "不对。你可以 print(list(range(5))) 看看。因为 range 是从 0 开始的。";
        int s = (int) service.gradeExplanation(ex, "range(n) 从 1 开始").get("total_score");
        assertThat(s).isGreaterThanOrEqualTo(25);
    }

    @Test void gradeWithRangeKeyword() {
        String ex = "不对。range(n) 从 0 到 n-1。因为 Python 设计就是这样。";
        int s = (int) service.gradeExplanation(ex, "range(n) 从 1 开始").get("total_score");
        assertThat(s).isGreaterThanOrEqualTo(15);
    }

    // ===== gradeExplanation: 维度校验 =====
    @SuppressWarnings("unchecked")
    @Test void gradeDimensionsAllBounded() {
        Map<String, Object> r = service.gradeExplanation(
                "不对，range(n) 是从 0 开始的，因为 Python 索引从 0 起。比如 range(3) 就是 0,1,2。",
                "range(n) 从 1 开始");
        Map<String, Integer> dims = (Map<String, Integer>) r.get("dimensions");
        assertThat(dims.get("clarity")).isBetween(0, 25);
        assertThat(dims.get("correctness")).isBetween(0, 25);
        assertThat(dims.get("use_of_example")).isBetween(0, 25);
        assertThat(dims.get("addressing_misconception")).isBetween(0, 25);
    }

    @Test void gradeMaxScore() {
        String longEx = "因为所以其实正确应该比如举个例子不对错print range ```code``` 这里有一个？" +
                "x".repeat(200);
        int s = (int) service.gradeExplanation(longEx, "test").get("total_score");
        assertThat(s).isLessThanOrEqualTo(100);
    }

    // ===== gradeExplanation: 反馈质量 =====
    @Test void feedbackEncouragingForHigh() {
        String ex = "因为 range(n) 其实从 0 开始。比如 range(3) 输出 [0,1,2]。举个例子，print(list(range(3))) 就能验证。";
        String feedback = (String) service.gradeExplanation(ex + " " + ex, "test").get("feedback");
        assertThat(feedback).doesNotContain("不太明白");
    }

    @Test void feedbackGuidingForLow() {
        String feedback = (String) service.gradeExplanation("就是错的", "test").get("feedback");
        assertThat(feedback).containsAnyOf("例子", "详细", "方式", "换个");
    }

    @Test void feedbackForMedium() {
        String feedback = (String) service.gradeExplanation("不对，因为这样。其实应该是那样。", "test").get("feedback");
        assertThat(feedback).isNotBlank();
    }

    // ===== gradeExplanation: 各种语言风格 =====
    @ParameterizedTest
    @ValueSource(strings = {
            "这个不对哦",
            "你说的有问题",
            "这里错了",
            "并不是这样的"
    })
    void gradeDetectsCorrection(String text) {
        int s = (int) service.gradeExplanation(text, "test").get("total_score");
        assertThat(s).isGreaterThan(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "比如说...",
            "例如 range(3)",
            "举个例子"
    })
    void gradeDetectsExampleUsage(String text) {
        Map<String, Object> r = service.gradeExplanation(text + " 一些补充文字来凑字数达到阈值吧", "test");
        @SuppressWarnings("unchecked")
        Map<String, Integer> dims = (Map<String, Integer>) r.get("dimensions");
        assertThat(dims.get("use_of_example")).isGreaterThan(0);
    }
}
