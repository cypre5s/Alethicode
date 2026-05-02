package com.alethicode.service.aitutor.agent;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.observability.AgentTraceContext;
import com.alethicode.service.aitutor.observability.AgentTraceRecorder;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import com.alethicode.service.aitutor.react.ToolTraceEntry;
import com.alethicode.service.aitutor.react.TutorToolRegistry;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.context.LayeredPromptBuilder;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
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
 * Handles ERROR_FEEDBACK events with ReAct tool-use loop and Reflection.
 * When ReAct is enabled, the agent can call tools to retrieve learner history,
 * search similar errors, and query courseware before generating a diagnosis.
 */
public class DiagnosticsAgent implements TutorAgent {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsAgent.class);
    private static final int REACT_MAX_ITERATIONS = 3;

    private final AiModelGateway aiModelGateway;
    private final ReflectionService reflectionService;
    private final boolean reactEnabled;
    private final JdbcTemplate jdbcTemplate;
    private final SimilarErrorRetrievalService similarErrorService;
    private final CoursewareRetrievalService coursewareService;
    private final String envPrefix;
    private LayeredPromptBuilder layeredPromptBuilder;

    public DiagnosticsAgent(AiModelGateway aiModelGateway, ReflectionService reflectionService) {
        this(aiModelGateway, reflectionService, false, null, null, null, null);
    }

    public DiagnosticsAgent(AiModelGateway aiModelGateway,
                            ReflectionService reflectionService,
                            boolean reactEnabled,
                            JdbcTemplate jdbcTemplate,
                            SimilarErrorRetrievalService similarErrorService,
                            CoursewareRetrievalService coursewareService) {
        this(aiModelGateway, reflectionService, reactEnabled, jdbcTemplate, similarErrorService, coursewareService, null);
    }

    public DiagnosticsAgent(AiModelGateway aiModelGateway,
                            ReflectionService reflectionService,
                            boolean reactEnabled,
                            JdbcTemplate jdbcTemplate,
                            SimilarErrorRetrievalService similarErrorService,
                            CoursewareRetrievalService coursewareService,
                            String envPrefix) {
        this.aiModelGateway = aiModelGateway;
        this.reflectionService = reflectionService;
        this.reactEnabled = reactEnabled;
        this.jdbcTemplate = jdbcTemplate;
        this.similarErrorService = similarErrorService;
        this.coursewareService = coursewareService;
        this.envPrefix = envPrefix;
    }

    public void setLayeredPromptBuilder(LayeredPromptBuilder builder) {
        this.layeredPromptBuilder = builder;
    }

    @Override
    public AgentCapability capability() {
        return new AgentCapability(
                "DiagnosticsAgent",
                "处理 ERROR_FEEDBACK 事件，通过 ReAct 工具调用进行深度错误诊断",
                List.of("ERROR_FEEDBACK"),
                List.of("CODING", "ERROR_FEEDBACK")
        );
    }

    @Override
    public boolean canHandle(String phase, String event) {
        return "ERROR_FEEDBACK".equals(event);
    }

    @Override
    public Map<String, Object> execute(AgentContext context) {
        Map<String, Object> diagnosis;
        AgentTraceContext trace = context.traceContext();

        Map<String, Object> llmMeta = new LinkedHashMap<>();
        llmMeta.put("agent", "DiagnosticsAgent");
        llmMeta.put("mode", reactEnabled && jdbcTemplate != null ? "react" : "single_shot");
        AgentTraceRecorder.SpanHandle llmSpan =
                trace == null ? null : trace.startSpan(AgentTraceRecorder.SpanType.LLM_CALL, llmMeta);
        try {
            if (reactEnabled && jdbcTemplate != null) {
                diagnosis = executeWithReact(context);
            } else {
                diagnosis = executeSingleShot(context);
            }
            if (trace != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("has_reasoning_chain", diagnosis.containsKey("reasoning_chain"));
                payload.put("tool_calls_count", diagnosis.get("tool_calls") instanceof List<?> l ? l.size() : 0);
                trace.endSpan(llmSpan, AgentTraceRecorder.SpanStatus.OK,
                        "diagnostics llm call succeeded", payload);
            }
        } catch (RuntimeException e) {
            if (trace != null) {
                trace.endSpan(llmSpan, AgentTraceRecorder.SpanStatus.FAILED,
                        "diagnostics llm call failed", Map.of("error", String.valueOf(e.getMessage())));
            }
            throw e;
        }

        AgentTraceRecorder.SpanHandle guardSpan = trace == null ? null
                : trace.startSpan(AgentTraceRecorder.SpanType.GUARDRAIL, Map.of(
                        "agent", "DiagnosticsAgent",
                        "guardrail", "ReflectionService"));
        try {
            Map<String, Object> evidenceForCritic = new LinkedHashMap<>();
            evidenceForCritic.put("problem_context", abbreviate(context.problemContext(), 1500));
            evidenceForCritic.put("event", context.event());
            ReflectionResult reflection = reflectionService.reflectAndRefine(
                    CardType.ERROR_DIAGNOSIS, evidenceForCritic, diagnosis, 1);
            diagnosis = reflection.output();
            log.debug("DiagnosticsAgent reflection: passed={}, react={}", reflection.passed(), reactEnabled);
            if (trace != null) {
                trace.endSpan(guardSpan,
                        reflection.passed()
                                ? AgentTraceRecorder.SpanStatus.OK
                                : AgentTraceRecorder.SpanStatus.FAILED,
                        reflection.passed() ? "reflection passed" : "reflection refined",
                        Map.of("passed", reflection.passed()));
            }
        } catch (RuntimeException e) {
            if (trace != null) {
                trace.endSpan(guardSpan, AgentTraceRecorder.SpanStatus.FAILED,
                        "reflection threw", Map.of("error", String.valueOf(e.getMessage())));
            }
            throw e;
        }

        return diagnosis;
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

        if (coursewareService != null) {
            ToolDefinition coursewareDef = TutorToolRegistry.searchCoursewareDefinition();
            tools.add(coursewareDef);
            executors.put(coursewareDef.name(),
                    TutorToolRegistry.searchCoursewareExecutor(coursewareService, context.problemId(), languagePackId));
        }

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
        log.debug("DiagnosticsAgent ReAct completed: iterations={}, toolCalls={}",
                result.iterationsUsed(), result.toolCallLog().size());

        Map<String, Object> output = new LinkedHashMap<>(result.result());
        attachToolCalls(output, result);
        return output;
    }

    private void attachToolCalls(Map<String, Object> output, ReactResult result) {
        if (result.toolTraceEntries() != null && !result.toolTraceEntries().isEmpty()) {
            output.put("tool_calls", result.toolTraceEntries().stream().map(ToolTraceEntry::toMap).toList());
        } else if (result.toolCallLog() != null && !result.toolCallLog().isEmpty()) {
            output.put("tool_calls", result.toolCallLog().stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tool_name", e.toolName());
                m.put("result_summary", e.resultSummary());
                return m;
            }).toList());
        }
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
                你是一名 OJ 错误诊断助手，具备工具调用能力。
                目标用户：非计算机专业的编程初学者。

                你有以下工具可用：
                1. get_learner_history — 查看学生最近提交记录，了解错误演变过程
                2. search_similar_errors — 检索学生历史中类似的错误模式，发现重复问题
                3. search_courseware — 检索课件知识点，找到相关教学内容

                工作流程：
                1. 先调用 get_learner_history 查看最近提交，理解错误上下文
                2. 如果发现明显的错误模式，调用 search_similar_errors 确认是否为重复错误
                3. 根据诊断结果，决定是否需要 search_courseware 补充知识点

                诊断原则：
                - 找到根本原因，不要只描述表面现象
                - 如果学生反复犯同类错误，明确指出这是一个需要专注解决的模式
                - 禁止给出完整可提交代码
                - 用鼓励性语言，对初学者友好

                最终输出 JSON：
                {"root_cause":"...", "what_program_is_doing":"...", "expected_behavior":"...", \
                "fix_direction":"...", "related_kcs":["..."], "error_pattern":"...", \
                "is_recurring":false, "encouragement":"...", \
                "reasoning_chain":[{"step":"观察","content":"..."},{"step":"假设","content":"..."},\
                {"step":"验证","content":"..."},{"step":"结论","content":"..."},{"step":"建议","content":"..."}]}

                reasoning_chain 是你的思考过程的结构化记录，分为5步：
                1. 观察：你从代码和工具结果中看到了什么
                2. 假设：你推测错误的原因是什么
                3. 验证：你如何确认了这个假设
                4. 结论：最终的诊断结论
                5. 建议：给学生的改进方向
                """;
    }

    private String buildSingleShotSystemPrompt() {
        return """
                你是一名 OJ 错误诊断助手。
                目标用户：非计算机专业的编程初学者。
                根据题干、错误代码、失败样例证据定位问题。
                禁止给出完整可提交代码。
                输出 JSON：
                {"root_cause":"...", "what_program_is_doing":"...", "expected_behavior":"...", \
                "fix_direction":"...", "related_kcs":["..."], "encouragement":"..."}
                """;
    }

    private String buildUserMessage(AgentContext context) {
        if (layeredPromptBuilder != null) {
            return layeredPromptBuilder.buildUserMessage(context, 2500);
        }
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
