package com.alethicode.service.aitutor.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ReflectionServiceImpl implements ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionServiceImpl.class);

    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;

    public ReflectionServiceImpl(AiModelGateway aiModelGateway, ObjectMapper objectMapper) {
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReflectionResult reflectAndRefine(
            CardType cardType,
            Map<String, Object> evidence,
            Map<String, Object> initialOutput,
            int maxRounds
    ) {
        Map<String, Object> current = initialOutput;

        for (int round = 1; round <= maxRounds; round++) {
            Map<String, Object> criticResult = runCritic(cardType, evidence, current);

            boolean pass = extractBoolean(criticResult, "pass");
            String verdict = extractString(criticResult, "verdict");
            log.debug("Reflection round {}/{} for {}: pass={}, verdict={}",
                    round, maxRounds, cardType.messageType(), pass, abbreviate(verdict, 120));

            if (pass) {
                return new ReflectionResult(current, round == 1, round, verdict);
            }

            String feedback = extractString(criticResult, "feedback");
            current = runRefine(cardType, evidence, current, feedback);
        }

        Map<String, Object> finalCritic = runCritic(cardType, evidence, current);
        boolean finalPass = extractBoolean(finalCritic, "pass");
        String finalVerdict = extractString(finalCritic, "verdict");

        if (!finalPass) {
            log.warn("Reflection for {} did not pass after {} rounds; accepting last refinement. verdict={}",
                    cardType.messageType(), maxRounds, abbreviate(finalVerdict, 200));
        }

        return new ReflectionResult(current, finalPass, maxRounds, finalVerdict);
    }

    private Map<String, Object> runCritic(CardType cardType, Map<String, Object> evidence, Map<String, Object> output) {
        String systemPrompt = buildCriticSystemPrompt(cardType);
        String userPrompt = buildCriticUserPrompt(evidence, output);
        return aiModelGateway.callForJson(systemPrompt, userPrompt);
    }

    private Map<String, Object> runRefine(CardType cardType, Map<String, Object> evidence,
                                          Map<String, Object> output, String feedback) {
        String systemPrompt = buildRefineSystemPrompt(cardType);
        String userPrompt = buildRefineUserPrompt(evidence, output, feedback);
        return aiModelGateway.callForJson(systemPrompt, userPrompt);
    }

    private String buildCriticSystemPrompt(CardType cardType) {
        String dimensions = switch (cardType) {
            case ERROR_DIAGNOSIS -> """
                    1. 事实一致性：root_cause 是否与 evidence 中的错误信息和提交结果吻合
                    2. 教学适切性：诊断语言是否适合编程初学者理解
                    3. schema 完整性：是否包含 root_cause, what_program_is_doing, expected_behavior, fix_direction, related_kcs, encouragement
                    4. 答案泄露检测：fix_direction 是否直接给出了完整修改后代码""";
            case FADED_EXAMPLE -> """
                    1. 事实一致性：脚手架内容是否与题目要求和课件一致
                    2. 教学适切性：scaffold_level 是否与 learner mastery 匹配（mastery 低→worked example，高→minimal hint）
                    3. schema 完整性：是否包含必填字段
                    4. 答案泄露检测：是否直接暴露了完整解题代码""";
            case POST_AC -> """
                    1. 事实一致性：迁移建议是否与已学 KC 和课件相关
                    2. 教学适切性：建议难度是否合理递进
                    3. schema 完整性：字段是否齐全
                    4. 逻辑相关性：迁移方向是否与当前题目知识点存在学习路径关联""";
            default -> """
                    1. 事实一致性：内容是否与 evidence 一致
                    2. 教学适切性：语言是否适合初学者
                    3. schema 完整性：字段是否齐全
                    4. 答案泄露检测：是否直接给出完整答案""";
        };

        return """
                你是一个严格的教学内容质检员（Critic）。
                你需要根据以下维度评估生成的教学卡片质量：
                %s
                
                输出必须是 JSON 对象，包含：
                - pass (boolean): 所有维度均通过则为 true
                - verdict (string): 一句话总结评估结论
                - feedback (string): 如果 pass=false，给出具体的改进指令；如果 pass=true，可为空串
                - dimension_scores (object): 每个维度的通过状态，如 {"factual_consistency": true, "pedagogical_fit": false, ...}
                """.formatted(dimensions);
    }

    private String buildCriticUserPrompt(Map<String, Object> evidence, Map<String, Object> output) {
        return """
                【原始证据/上下文】
                %s

                【生成的卡片内容】
                %s
                """.formatted(safeJson(evidence), safeJson(output));
    }

    private String buildRefineSystemPrompt(CardType cardType) {
        return """
                你是一个教学内容修正专家（Refiner）。
                你将收到一份原始证据、一份被质检员标记为需要修正的教学卡片、以及质检反馈。
                请根据反馈修正卡片内容，输出修正后的完整 JSON 卡片。
                保留原始卡片的所有字段结构，仅修正质检反馈中指出的问题。
                不要添加额外字段，不要改变 JSON 结构。
                """;
    }

    private String buildRefineUserPrompt(Map<String, Object> evidence,
                                         Map<String, Object> output, String feedback) {
        return """
                【原始证据/上下文】
                %s

                【需要修正的卡片】
                %s

                【质检反馈】
                %s
                """.formatted(safeJson(evidence), safeJson(output), feedback);
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
