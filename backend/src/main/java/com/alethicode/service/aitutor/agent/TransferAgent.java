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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles TRANSFER and AC_REVIEW events.
 * When ReAct is enabled, AC_REVIEW can call get_learner_history to ground
 * the review in the student's actual submission trajectory.
 */
public class TransferAgent implements TutorAgent {

    private static final Logger log = LoggerFactory.getLogger(TransferAgent.class);
    private static final int REACT_MAX_ITERATIONS = 2;

    private final AiModelGateway aiModelGateway;
    private final ReflectionService reflectionService;
    private final boolean reactEnabled;
    private final JdbcTemplate jdbcTemplate;
    private final String envPrefix;

    public TransferAgent(AiModelGateway aiModelGateway, ReflectionService reflectionService) {
        this(aiModelGateway, reflectionService, false, null, null);
    }

    public TransferAgent(AiModelGateway aiModelGateway,
                         ReflectionService reflectionService,
                         boolean reactEnabled,
                         JdbcTemplate jdbcTemplate) {
        this(aiModelGateway, reflectionService, reactEnabled, jdbcTemplate, null);
    }

    public TransferAgent(AiModelGateway aiModelGateway,
                         ReflectionService reflectionService,
                         boolean reactEnabled,
                         JdbcTemplate jdbcTemplate,
                         String envPrefix) {
        this.aiModelGateway = aiModelGateway;
        this.reflectionService = reflectionService;
        this.reactEnabled = reactEnabled;
        this.jdbcTemplate = jdbcTemplate;
        this.envPrefix = envPrefix;
    }

    @Override
    public AgentCapability capability() {
        return new AgentCapability(
                "TransferAgent",
                "处理 TRANSFER 和 AC_REVIEW 事件，提供知识迁移和AC后引导",
                List.of("TRANSFER", "AC_REVIEW"),
                List.of("TRANSFER", "AC_REVIEW")
        );
    }

    @Override
    public boolean canHandle(String phase, String event) {
        return "TRANSFER".equals(event) || "AC_REVIEW".equals(event);
    }

    @Override
    public Map<String, Object> execute(AgentContext context) {
        Map<String, Object> output;
        if ("AC_REVIEW".equals(context.event()) && reactEnabled && jdbcTemplate != null) {
            output = executeWithReact(context);
        } else {
            output = executeSingleShot(context);
        }

        if ("AC_REVIEW".equals(context.event())) {
            Map<String, Object> evidenceForCritic = new LinkedHashMap<>();
            evidenceForCritic.put("problem_context", abbreviate(context.problemContext(), 1500));
            ReflectionResult reflection = reflectionService.reflectAndRefine(
                    CardType.POST_AC, evidenceForCritic, output, 1);
            output = reflection.output();
            log.debug("TransferAgent reflection: passed={}", reflection.passed());
        }

        return output;
    }

    private Map<String, Object> executeWithReact(AgentContext context) {
        List<ToolDefinition> tools = new ArrayList<>();
        Map<String, ToolExecutor> executors = new LinkedHashMap<>();

        ToolDefinition historyDef = TutorToolRegistry.getLearnerHistoryDefinition();
        tools.add(historyDef);
        executors.put(historyDef.name(),
                TutorToolRegistry.getLearnerHistoryExecutor(jdbcTemplate, context.userId(), context.problemId()));

        Long languagePackId = context.tutorContext() != null ? context.tutorContext().languagePackId() : null;
        ToolContext toolContext = new ToolContext(
                context.userId(), null, context.problemId(), languagePackId,
                context.currentPhase(), context.event(), "zh-CN", Set.of());

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", buildUserPrompt(context))
        );

        ReactResult result = aiModelGateway.callWithTools(
                buildReactSystemPrompt(), messages, tools, executors,
                REACT_MAX_ITERATIONS, toolContext,
                com.alethicode.service.aitutor.contract.StoppingCondition.defaults(), envPrefix);
        log.debug("TransferAgent ReAct completed: iterations={}, toolCalls={}",
                result.iterationsUsed(), result.toolCallLog().size());

        return result.result();
    }

    private Map<String, Object> executeSingleShot(AgentContext context) {
        return aiModelGateway.callForJson(
                buildSingleShotSystemPrompt(context.event()),
                buildUserPrompt(context),
                envPrefix
        );
    }

    private String buildReactSystemPrompt() {
        return """
                你是 OJ AC后引导助手，具备工具调用能力。
                目标用户：非计算机专业的编程初学者。
                
                你有以下工具可用：
                1. get_learner_history — 查看学生最近提交记录，了解从错误到AC的完整过程
                
                工作流程：
                1. 调用 get_learner_history 查看学生在本题的提交轨迹
                2. 分析学生从首次错误到最终AC经历了哪些尝试和修改
                3. 基于实际提交历史生成有针对性的回顾总结和迁移建议
                
                引导原则：
                - 基于学生的真实解题过程进行总结，不泛泛而谈
                - 指出学生在过程中展现的进步
                - 提出迁移方向帮助巩固
                
                输出 JSON 对象。
                """;
    }

    private String buildSingleShotSystemPrompt(String event) {
        if ("AC_REVIEW".equals(event)) {
            return """
                    你是 OJ AC后引导助手。
                    目标用户：非计算机专业的编程初学者。
                    帮助学生回顾解题过程，总结知识点，提出迁移方向。
                    输出 JSON 对象。
                    """;
        }
        return """
                你是 OJ 知识迁移助手。
                目标用户：非计算机专业的编程初学者。
                根据已完成的题目，生成一道变式题帮助巩固知识点。
                输出 JSON 对象。
                """;
    }

    private String buildUserPrompt(AgentContext context) {
        String memoryBlock = context.formatMemoryContext();
        return """
                【题目上下文】
                %s
                
                %s【当前事件】%s
                """.formatted(abbreviate(context.problemContext(), 2500), memoryBlock, context.event());
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
