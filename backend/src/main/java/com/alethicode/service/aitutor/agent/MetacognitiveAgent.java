package com.alethicode.service.aitutor.agent;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import com.alethicode.service.aitutor.react.TutorToolRegistry;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.context.LayeredPromptBuilder;
import com.alethicode.service.aitutor.retrieval.SimilarErrorRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Metacognitive coaching agent active during AC_REVIEW phase.
 *
 * Unlike TransferAgent which focuses on knowledge transfer and variation problems,
 * MetacognitiveAgent focuses on helping students develop self-awareness of their
 * thinking and learning processes:
 *
 * 1. Self-explanation: asks students to articulate WHY their solution works
 * 2. Cross-problem pattern linking: connects current errors/successes to past problems
 * 3. Strategy reflection: helps students notice which problem-solving strategies they used
 * 4. Error pattern awareness: surfaces recurring error patterns the student may not notice
 */
public class MetacognitiveAgent implements TutorAgent {

    private static final Logger log = LoggerFactory.getLogger(MetacognitiveAgent.class);
    private static final int REACT_MAX_ITERATIONS = 3;

    private final AiModelGateway aiModelGateway;
    private final ReflectionService reflectionService;
    private final JdbcTemplate jdbcTemplate;
    private final SimilarErrorRetrievalService similarErrorService;
    private final String envPrefix;
    private LayeredPromptBuilder layeredPromptBuilder;

    public void setLayeredPromptBuilder(LayeredPromptBuilder builder) {
        this.layeredPromptBuilder = builder;
    }

    public MetacognitiveAgent(AiModelGateway aiModelGateway,
                              ReflectionService reflectionService,
                              JdbcTemplate jdbcTemplate,
                              SimilarErrorRetrievalService similarErrorService) {
        this(aiModelGateway, reflectionService, jdbcTemplate, similarErrorService, null);
    }

    public MetacognitiveAgent(AiModelGateway aiModelGateway,
                              ReflectionService reflectionService,
                              JdbcTemplate jdbcTemplate,
                              SimilarErrorRetrievalService similarErrorService,
                              String envPrefix) {
        this.aiModelGateway = aiModelGateway;
        this.reflectionService = reflectionService;
        this.jdbcTemplate = jdbcTemplate;
        this.similarErrorService = similarErrorService;
        this.envPrefix = envPrefix;
    }

    @Override
    public AgentCapability capability() {
        return new AgentCapability(
                "MetacognitiveAgent",
                "AC后元认知引导：自我解释、跨题模式连接、策略反思、错误模式觉察",
                List.of("METACOGNITIVE"),
                List.of("AC_REVIEW")
        );
    }

    @Override
    public boolean canHandle(String phase, String event) {
        return "METACOGNITIVE".equals(event);
    }

    @Override
    public Map<String, Object> execute(AgentContext context) {
        Map<String, Object> output;
        if (jdbcTemplate != null) {
            output = executeWithReact(context);
        } else {
            output = executeSingleShot(context);
        }

        Map<String, Object> evidenceForCritic = new LinkedHashMap<>();
        evidenceForCritic.put("problem_context", abbreviate(context.problemContext(), 1500));
        evidenceForCritic.put("learner_mastery", context.learnerState() != null
                ? context.learnerState().masteryByKc() : Map.of());
        ReflectionResult reflection = reflectionService.reflectAndRefine(
                CardType.POST_AC, evidenceForCritic, output, 1);
        output = reflection.output();
        log.debug("MetacognitiveAgent reflection: passed={}", reflection.passed());

        return output;
    }

    private Map<String, Object> executeWithReact(AgentContext context) {
        List<ToolDefinition> tools = new ArrayList<>();
        Map<String, ToolExecutor> executors = new LinkedHashMap<>();

        ToolDefinition historyDef = TutorToolRegistry.getLearnerHistoryDefinition();
        tools.add(historyDef);
        executors.put(historyDef.name(),
                TutorToolRegistry.getLearnerHistoryExecutor(jdbcTemplate, context.userId(), context.problemId()));

        if (similarErrorService != null) {
            ToolDefinition errorDef = TutorToolRegistry.searchSimilarErrorsDefinition();
            tools.add(errorDef);
            executors.put(errorDef.name(),
                    TutorToolRegistry.searchSimilarErrorsExecutor(
                            similarErrorService, context.userId(), context.problemId(),
                            context.tutorContext() != null ? context.tutorContext().currentLanguage() : "Python3"));
        }

        Long languagePackId = context.tutorContext() != null ? context.tutorContext().languagePackId() : null;
        ToolContext toolContext = new ToolContext(
                context.userId(), null, context.problemId(), languagePackId,
                context.currentPhase(), context.event(), "zh-CN", Set.of());

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", buildUserMessage(context))
        );

        ReactResult result = aiModelGateway.callWithTools(
                buildReactSystemPrompt(), messages, tools, executors,
                REACT_MAX_ITERATIONS, toolContext,
                com.alethicode.service.aitutor.contract.StoppingCondition.defaults(), envPrefix);
        log.debug("MetacognitiveAgent ReAct completed: iterations={}, toolCalls={}",
                result.iterationsUsed(), result.toolCallLog().size());

        Map<String, Object> output = new LinkedHashMap<>(result.result());
        if (result.toolTraceEntries() != null && !result.toolTraceEntries().isEmpty()) {
            output.put("tool_calls", result.toolTraceEntries().stream()
                    .map(com.alethicode.service.aitutor.react.ToolTraceEntry::toMap).toList());
        }
        return output;
    }

    private Map<String, Object> executeSingleShot(AgentContext context) {
        return aiModelGateway.callForJson(
                buildSingleShotSystemPrompt(),
                buildUserMessage(context),
                envPrefix
        );
    }

    private String buildReactSystemPrompt() {
        return """
                你是一名编程学习元认知教练。
                目标用户：非计算机专业的编程初学者。
                你的目标不是教具体的编程知识，而是帮助学生发展「学会学习」的能力。

                你有以下工具可用：
                1. get_learner_history — 查看学生在这道题上的提交历史
                2. search_similar_errors — 检索学生跨题目的错误模式

                工作流程：
                1. 先调用 get_learner_history 了解学生在本题的完整解题过程（从首次错误到最终AC）
                2. 调用 search_similar_errors 查找跨题目的错误模式重复
                3. 基于证据生成元认知引导

                元认知引导的四个维度：
                ① 自我解释 — 让学生用自己的话解释为什么最终方案是正确的
                ② 策略觉察 — 帮学生识别自己用了什么解题策略（分解、类比、试错…）
                ③ 错误模式连接 — 如果发现跨题重复的错误模式，明确告知学生
                ④ 思维框架强化 — 引导学生形成"审题→分解→编码→测试→反思"的习惯

                语气要求：
                - 不是在教学，而是在引导学生自己发现
                - 用提问式引导，而非陈述式告知
                - 对初学者友好、有鼓励性

                最终输出 JSON，必须包含 reasoning_chain 字段：
                {
                  "reasoning_chain": [
                    {"step": "回顾", "content": "学生解题过程概述"},
                    {"step": "反思", "content": "发现的关键模式或进步"},
                    {"step": "引导", "content": "元认知引导方向"}
                  ],
                  "self_explanation_prompt": "引导学生自我解释的提问",
                  "strategy_noticed": "识别出的解题策略",
                  "error_pattern_insight": "跨题错误模式洞察（如无则为空）",
                  "is_recurring_pattern": false,
                  "thinking_framework_tip": "思维框架强化建议",
                  "reflection_questions": ["引导性提问列表"],
                  "encouragement": "鼓励语",
                  "growth_indicator": "学生在这道题上展现的进步点"
                }
                """;
    }

    private String buildSingleShotSystemPrompt() {
        return """
                你是一名编程学习元认知教练。
                目标用户：非计算机专业的编程初学者。
                帮助学生发展自我反思能力，而不是直接教编程知识。

                元认知引导维度：
                ① 自我解释 — 引导学生解释为什么方案正确
                ② 策略觉察 — 帮学生识别自己的解题策略
                ③ 思维框架 — 强化"审题→分解→编码→测试→反思"习惯

                输出 JSON，必须包含 reasoning_chain 字段：
                {
                  "reasoning_chain": [
                    {"step": "回顾", "content": "学生解题过程概述"},
                    {"step": "反思", "content": "发现的关键模式"},
                    {"step": "引导", "content": "元认知引导方向"}
                  ],
                  "self_explanation_prompt": "...",
                  "strategy_noticed": "...",
                  "thinking_framework_tip": "...",
                  "reflection_questions": ["..."],
                  "encouragement": "...",
                  "growth_indicator": "..."
                }
                """;
    }

    private String buildUserMessage(AgentContext context) {
        if (layeredPromptBuilder != null) {
            return layeredPromptBuilder.buildUserMessage(context, 2500);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【题目上下文】\n").append(abbreviate(context.problemContext(), 2500)).append("\n\n");
        String memoryBlock = context.formatMemoryContext();
        if (!memoryBlock.isEmpty()) {
            sb.append(memoryBlock);
        }
        sb.append("【当前阶段】").append(context.currentPhase()).append("\n");
        if (context.learnerState() != null && context.learnerState().calibrated()) {
            sb.append("【学习者掌握度】").append(context.learnerState().masteryByKc()).append("\n");
            if (!context.learnerState().weakKcs().isEmpty()) {
                sb.append("【薄弱知识点】").append(context.learnerState().weakKcs()).append("\n");
            }
        }
        return sb.toString();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
