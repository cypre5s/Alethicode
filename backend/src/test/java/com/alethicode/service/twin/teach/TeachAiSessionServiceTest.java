package com.alethicode.service.twin.teach;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeachAiSessionServiceTest {

    private final TeachAiSessionService service = new TeachAiSessionService(null);

    @Test
    void gradeShortExplanationScoresLow() {
        Map<String, Object> result = service.gradeExplanation("不对", "range(n) 从 1 开始");
        int score = (int) result.get("total_score");
        assertThat(score).isLessThan(30);
    }

    @Test
    void gradeMediumExplanationWithReasonScoresMedium() {
        Map<String, Object> result = service.gradeExplanation(
                "这个不对，因为 range(n) 其实是从 0 开始到 n-1 的", "range(n) 从 1 开始");
        int score = (int) result.get("total_score");
        assertThat(score).isBetween(25, 65);
    }

    @Test
    void gradeDetailedExplanationWithExampleScoresHigh() {
        String explanation = """
            这个理解不对哦！range(n) 其实是从 0 开始的，到 n-1 结束。
            因为 Python 的索引是从 0 开始的，range 也遵循这个设计。
            比如 range(5) 会生成 [0, 1, 2, 3, 4]，而不是 [1, 2, 3, 4, 5]。
            你可以试试在终端里 print(list(range(5))) 看看结果。
            举个例子，如果你要遍历一个长度为 3 的列表，
            应该写 for i in range(3)，这样 i 会是 0, 1, 2。
            """;
        Map<String, Object> result = service.gradeExplanation(explanation, "range(n) 从 1 开始");
        int score = (int) result.get("total_score");
        assertThat(score).isGreaterThanOrEqualTo(60);
    }

    @Test
    void gradeDimensionsAreAllBetweenZeroAnd25() {
        Map<String, Object> result = service.gradeExplanation(
                "不对，range(n) 是从 0 开始的，因为 Python 索引从 0 起。比如 range(3) 就是 0,1,2。",
                "range(n) 从 1 开始");
        @SuppressWarnings("unchecked")
        Map<String, Integer> dims = (Map<String, Integer>) result.get("dimensions");
        assertThat(dims.get("clarity")).isBetween(0, 25);
        assertThat(dims.get("correctness")).isBetween(0, 25);
        assertThat(dims.get("use_of_example")).isBetween(0, 25);
        assertThat(dims.get("addressing_misconception")).isBetween(0, 25);
    }

    @Test
    void gradeEmptyExplanationScoresZero() {
        Map<String, Object> result = service.gradeExplanation("", "range(n) 从 1 开始");
        int score = (int) result.get("total_score");
        assertThat(score).isEqualTo(0);
    }

    @Test
    void feedbackIsEncouragingForHighScore() {
        String explanation = """
            这个不对。因为 range(n) 其实从 0 开始，到 n-1 结束。
            举个例子，print(list(range(3))) 输出的是 [0, 1, 2] 而不是 [1, 2, 3]。
            这是因为 Python 使用零索引设计，和 C/Java 一致。
            如果你想从 1 开始，应该写 range(1, n+1)。
            """;
        Map<String, Object> result = service.gradeExplanation(explanation, "range(n) 从 1 开始");
        String feedback = (String) result.get("feedback");
        assertThat(feedback).doesNotContain("不太明白");
    }

    @Test
    void feedbackGuidesImprovementForLowScore() {
        Map<String, Object> result = service.gradeExplanation("就是错的", "range(n) 从 1 开始");
        String feedback = (String) result.get("feedback");
        assertThat(feedback).containsAnyOf("例子", "详细", "方式");
    }

    @Test
    void gradeWithCodeSnippetBoostsUseOfExample() {
        Map<String, Object> resultWith = service.gradeExplanation(
                "不对，应该是从 0 开始。```print(list(range(3)))``` 输出 [0,1,2]",
                "range(n) 从 1 开始");
        Map<String, Object> resultWithout = service.gradeExplanation(
                "不对，应该是从 0 开始。输出是 0,1,2",
                "range(n) 从 1 开始");
        int scoreWith = (int) resultWith.get("total_score");
        int scoreWithout = (int) resultWithout.get("total_score");
        assertThat(scoreWith).isGreaterThanOrEqualTo(scoreWithout);
    }

    @Test
    void totalScoreNeverExceeds100() {
        String longExplanation = """
            这个完全不对！因为 range(n) 其实是从 0 开始到 n-1 结束的。
            原因是 Python 采用零索引设计，所以 range 也从 0 起步。
            比如 range(5) 生成的序列是 0, 1, 2, 3, 4。
            举个例子，如果你写 for i in range(3): print(i)，
            输出会是 0 1 2，而不是 1 2 3。
            正确的做法应该是用 range(1, n+1) 来从 1 开始。
            这个错误很常见，我之前也犯过同样的错。
            """;
        Map<String, Object> result = service.gradeExplanation(longExplanation, "range(n) 从 1 开始");
        int score = (int) result.get("total_score");
        assertThat(score).isLessThanOrEqualTo(100);
    }
}
