package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parsons AC 后 walkthrough 文本的 LLM-as-judge 评分器。
 *
 * <p>评分维度（设计稿 §11.2）：</p>
 * <ul>
 *   <li>是否清晰描述至少一个关键 block 的作用</li>
 *   <li>是否解释代码顺序背后的逻辑（数据流 / 控制流）</li>
 *   <li>是否避免照搬代码字面，体现"用自己话讲清"</li>
 * </ul>
 *
 * <p>分数 ∈ [0, 1]。{@link #evaluate} 直接返回 {@link Result}，
 * 上层根据 {@code score-threshold} 决定是否写 breakthrough notebook。</p>
 */
@Service
public class ParsonsWalkthroughEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ParsonsWalkthroughEvaluator.class);

    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final ParsonsProperties properties;

    public ParsonsWalkthroughEvaluator(AiModelGateway aiModelGateway,
                                       ObjectMapper objectMapper,
                                       ParsonsProperties properties) {
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Result evaluate(String walkthroughText, List<ParsonsBlock> blocks, String problemTitle) {
        String trimmed = walkthroughText == null ? "" : walkthroughText.trim();
        if (trimmed.isEmpty()) {
            return new Result(0.0, "请用自己的话至少描述一个关键代码块的作用。", false);
        }

        String systemPrompt = """
                你是 Python 编程教学 walkthrough 评分员。请基于学生写的代码理解描述，给出 0-1 的评分。
                评分标准：
                  - 0.9+：清晰说明了至少一个关键 block 的作用 + 解释了顺序背后的数据流/控制流
                  - 0.7-0.9：基本说清了一个关键步骤的目的，但顺序解释不够清晰
                  - 0.5-0.7：只复述了代码字面没有讲清意图
                  - <0.5：内容空洞、错误或与代码无关
                严格输出 JSON：{"score": <float>, "feedback": "<给学生的中文反馈，<= 80 字>"}
                """;

        StringBuilder blockSummary = new StringBuilder();
        int idx = 1;
        for (ParsonsBlock b : blocks) {
            if (b.fadingState() == ParsonsBlock.FadingState.HIDDEN) continue;
            blockSummary.append(idx++).append(". ").append("    ".repeat(b.indent())).append(b.code()).append("\n");
            if (idx > 12) {
                blockSummary.append("...\n");
                break;
            }
        }

        String userPrompt = """
                【题目】%s
                【正确代码（按顺序）】
                %s
                【学生 walkthrough 描述】
                %s
                """.formatted(problemTitle == null ? "" : problemTitle,
                blockSummary.toString().stripTrailing(),
                trimmed);

        try {
            Map<String, Object> resp = aiModelGateway.callForJson(systemPrompt, userPrompt);
            double score = resp == null ? 0.0 : asDouble(resp.get("score"));
            String feedback = resp == null ? "" : asString(resp.get("feedback"));
            score = Math.max(0.0, Math.min(1.0, score));
            boolean passed = score >= properties.getWalkthrough().getScoreThreshold();
            return new Result(round(score), feedback.isBlank() ? defaultFeedback(passed) : feedback, passed);
        } catch (RuntimeException e) {
            log.warn("Parsons walkthrough LLM evaluation failed; returning low score: {}", e.getMessage());
            return new Result(0.0, "评分服务暂不可用，请再写一次。", false);
        }
    }

    private static String defaultFeedback(boolean passed) {
        return passed ? "理解清晰，已记入顿悟笔记。" : "请补充说明关键步骤的目的与顺序。";
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return 0.0;
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String asString(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public record Result(double score, String feedback, boolean passed) {
    }
}
