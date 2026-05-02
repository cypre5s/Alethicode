package com.alethicode.service.aitutor.agent;

import com.alethicode.service.ai.AiModelGateway;

import java.util.List;
import java.util.Map;

/**
 * Handles CHAT events. Lightweight and fast — skips Reflection for latency.
 */
public class ChatAgent implements TutorAgent {

    private final AiModelGateway aiModelGateway;
    private final String envPrefix;

    public ChatAgent(AiModelGateway aiModelGateway) {
        this(aiModelGateway, null);
    }

    public ChatAgent(AiModelGateway aiModelGateway, String envPrefix) {
        this.aiModelGateway = aiModelGateway;
        this.envPrefix = envPrefix;
    }

    @Override
    public AgentCapability capability() {
        return new AgentCapability(
                "ChatAgent",
                "处理 CHAT 事件，提供轻量级对话回复",
                List.of("CHAT"),
                List.of("READING", "IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW", "TRANSFER")
        );
    }

    @Override
    public boolean canHandle(String phase, String event) {
        return "CHAT".equals(event);
    }

    @Override
    public Map<String, Object> execute(AgentContext context) {
        String message = extractString(context.eventData(), "message");
        String memoryBlock = context.formatMemoryContext();
        return aiModelGateway.callForJson(
                """
                你是 OJ 学习助手。
                目标用户：非计算机专业的编程初学者。
                简洁、友好地回答学生的提问，不要直接给出解题代码。
                如果学习者记忆中有相关的历史错误或学习结论，可以适当引用。
                输出 JSON：{"reply":"...","suggestions":["..."]}
                """,
                """
                【题目上下文】
                %s
                
                %s【学生消息】%s
                
                【当前阶段】%s
                """.formatted(
                        abbreviate(context.problemContext(), 2000),
                        memoryBlock,
                        message,
                        context.currentPhase()
                ),
                envPrefix
        );
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
