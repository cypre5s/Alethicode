package com.alethicode.service.aitutor.agent;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import com.alethicode.service.aitutor.react.TutorToolRegistry;
import com.alethicode.service.aitutor.context.LayeredPromptBuilder;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles READING and IDEATING events.
 * When ReAct is enabled, can call search_courseware to ground guidance in course material.
 */
public class GuideAgent implements TutorAgent {

    private static final Logger log = LoggerFactory.getLogger(GuideAgent.class);
    private static final int REACT_MAX_ITERATIONS = 2;

    private final AiModelGateway aiModelGateway;
    private final boolean reactEnabled;
    private final CoursewareRetrievalService coursewareService;
    private final String envPrefix;
    private LayeredPromptBuilder layeredPromptBuilder;

    public GuideAgent(AiModelGateway aiModelGateway) {
        this(aiModelGateway, false, null, null);
    }

    public GuideAgent(AiModelGateway aiModelGateway,
                      boolean reactEnabled,
                      CoursewareRetrievalService coursewareService) {
        this(aiModelGateway, reactEnabled, coursewareService, null);
    }

    public GuideAgent(AiModelGateway aiModelGateway,
                      boolean reactEnabled,
                      CoursewareRetrievalService coursewareService,
                      String envPrefix) {
        this.aiModelGateway = aiModelGateway;
        this.reactEnabled = reactEnabled;
        this.coursewareService = coursewareService;
        this.envPrefix = envPrefix;
    }

    public void setLayeredPromptBuilder(LayeredPromptBuilder builder) {
        this.layeredPromptBuilder = builder;
    }

    @Override
    public AgentCapability capability() {
        return new AgentCapability(
                "GuideAgent",
                "处理 READING 和 IDEATING 事件，提供审题导学和思路引导",
                List.of("READING", "IDEATING"),
                List.of("READING", "IDEATING")
        );
    }

    @Override
    public boolean canHandle(String phase, String event) {
        return "READING".equals(event) || "IDEATING".equals(event);
    }

    @Override
    public Map<String, Object> execute(AgentContext context) {
        if (reactEnabled && coursewareService != null) {
            return executeWithReact(context);
        }
        return executeSingleShot(context);
    }

    private Map<String, Object> executeWithReact(AgentContext context) {
        List<ToolDefinition> tools = new ArrayList<>();
        Map<String, ToolExecutor> executors = new LinkedHashMap<>();

        ToolDefinition coursewareDef = TutorToolRegistry.searchCoursewareDefinition();
        tools.add(coursewareDef);
        Long languagePackId = context.tutorContext() != null ? context.tutorContext().languagePackId() : null;
        executors.put(coursewareDef.name(),
                TutorToolRegistry.searchCoursewareExecutor(coursewareService, context.problemId(), languagePackId));

        ToolContext toolContext = new ToolContext(
                context.userId(), null, context.problemId(), languagePackId,
                context.currentPhase(), context.event(), "zh-CN", Set.of());

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", buildUserPrompt(context))
        );

        ReactResult result = aiModelGateway.callWithTools(
                buildReactSystemPrompt(context.event()), messages, tools, executors,
                REACT_MAX_ITERATIONS, toolContext,
                com.alethicode.service.aitutor.contract.StoppingCondition.defaults(), envPrefix);
        log.debug("GuideAgent ReAct completed: event={}, iterations={}, toolCalls={}",
                context.event(), result.iterationsUsed(), result.toolCallLog().size());

        Map<String, Object> output = new LinkedHashMap<>(result.result());
        if (result.toolTraceEntries() != null && !result.toolTraceEntries().isEmpty()) {
            output.put("tool_calls", result.toolTraceEntries().stream()
                    .map(com.alethicode.service.aitutor.react.ToolTraceEntry::toMap).toList());
        }
        return output;
    }

    private Map<String, Object> executeSingleShot(AgentContext context) {
        String cacheKey = com.alethicode.service.aitutor.LlmResponseCacheService
                .buildCacheKey(context.problemId(), context.event());
        return aiModelGateway.callForJsonCached(cacheKey,
                buildSingleShotSystemPrompt(context.event()),
                buildUserPrompt(context), envPrefix);
    }

    private String buildReactSystemPrompt(String event) {
        String role = "READING".equals(event)
                ? "你是 OJ 审题导学助手，具备工具调用能力。帮助学生理解题目要求、输入输出格式和约束条件。"
                : "你是 OJ 思路引导助手，具备工具调用能力。引导学生从问题分解、算法选择、边界条件三个维度思考。";
        return """
                %s
                目标用户：非计算机专业的编程初学者。
                
                你有以下工具可用：
                1. search_courseware — 检索课件知识点，找到与当前题目相关的教学内容
                
                工作流程：
                1. 分析题目涉及的知识点
                2. 调用 search_courseware 获取相关课件片段，让引导有教材依据
                3. 结合课件内容生成导学引导
                
                不要直接给出解题代码。
                输出 JSON 对象，必须包含 reasoning_chain 和 courseware_refs 字段：
                {
                  "reasoning_chain": [
                    {"step": "分析", "content": "题目涉及的知识点和关键约束"},
                    {"step": "检索", "content": "从课件中找到的相关内容"},
                    {"step": "引导", "content": "基于课件的导学方向"}
                  ],
                  "courseware_refs": [
                    {"page": 12, "title": "知识点名称", "snippet": "课件原文片段"}
                  ],
                  ... 其他字段 ...
                }
                """.formatted(role);
    }

    private String buildSingleShotSystemPrompt(String event) {
        if ("READING".equals(event)) {
            return """
                    你是 OJ 审题导学助手。
                    目标用户：非计算机专业的编程初学者。
                    帮助学生理解题目要求、输入输出格式和约束条件。
                    不要直接给出解题代码。
                    输出 JSON 对象，必须包含 reasoning_chain 字段：
                    {
                      "reasoning_chain": [
                        {"step": "分析", "content": "题目涉及的知识点和关键约束"},
                        {"step": "引导", "content": "导学方向"}
                      ],
                      ... 其他字段 ...
                    }
                    """;
        }
        return """
                你是 OJ 思路引导助手。
                目标用户：非计算机专业的编程初学者。
                引导学生从问题分解、算法选择、边界条件三个维度思考。
                不要直接给出完整代码。
                输出 JSON 对象，必须包含 reasoning_chain 字段：
                {
                  "reasoning_chain": [
                    {"step": "分析", "content": "题目关键信息拆解"},
                    {"step": "引导", "content": "思路方向提示"}
                  ],
                  ... 其他字段 ...
                }
                """;
    }

    private String buildUserPrompt(AgentContext context) {
        if (layeredPromptBuilder != null) {
            return layeredPromptBuilder.buildUserMessage(context, 2500);
        }
        String memoryBlock = context.formatMemoryContext();
        return """
                【题目上下文】
                %s
                
                %s【当前阶段】%s
                【当前事件】%s
                """.formatted(
                abbreviate(context.problemContext(), 2500),
                memoryBlock,
                context.currentPhase(),
                context.event()
        );
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
