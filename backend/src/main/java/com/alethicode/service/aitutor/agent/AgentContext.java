package com.alethicode.service.aitutor.agent;

import com.alethicode.service.aitutor.evidence.EvidencePack;
import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import com.alethicode.service.aitutor.observability.AgentTraceContext;
import com.alethicode.service.aitutor.profile.LearnerState;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates all contextual information an agent needs to execute.
 *
 * @param event          the workflow event being processed
 * @param currentPhase   current FSM phase
 * @param eventData      raw event payload from the client
 * @param evidencePack   assembled evidence (problem, submission, courseware, etc.)
 * @param learnerState   current learner profile snapshot
 * @param tutorContext   language-aware tutor context
 * @param problemContext serialized problem context string
 * @param nodeOutputs    mutable output map shared across agents in a single turn
 * @param problemId      current problem ID
 * @param userId         current user ID
 * @param sessionHistory previous agent outputs in this session, ordered chronologically
 * @param traceContext   可观测性上下文（可空，表示当前调用链不要求 span 追踪）；
 *                       典型路径：{@code processWorkflowEvent} 创建 DISPATCH span 时填入，
 *                       agent 在 LLM / 工具调用节点通过 {@link AgentTraceContext#startSpan} 打子 span。
 */
public record AgentContext(
        String event,
        String currentPhase,
        Map<String, Object> eventData,
        EvidencePack evidencePack,
        LearnerState learnerState,
        LanguageAwareTutorContext tutorContext,
        String problemContext,
        Map<String, Object> nodeOutputs,
        Long problemId,
        Long userId,
        List<Map<String, Object>> sessionHistory,
        AgentTraceContext traceContext
) {

    public AgentContext(
            String event,
            String currentPhase,
            Map<String, Object> eventData,
            EvidencePack evidencePack,
            LearnerState learnerState,
            LanguageAwareTutorContext tutorContext,
            String problemContext,
            Map<String, Object> nodeOutputs,
            Long problemId,
            Long userId,
            List<Map<String, Object>> sessionHistory
    ) {
        this(event, currentPhase, eventData, evidencePack, learnerState,
                tutorContext, problemContext, nodeOutputs, problemId, userId, sessionHistory, null);
    }

    public AgentContext(
            String event,
            String currentPhase,
            Map<String, Object> eventData,
            EvidencePack evidencePack,
            LearnerState learnerState,
            LanguageAwareTutorContext tutorContext,
            String problemContext,
            Map<String, Object> nodeOutputs,
            Long problemId,
            Long userId
    ) {
        this(event, currentPhase, eventData, evidencePack, learnerState,
                tutorContext, problemContext, nodeOutputs, problemId, userId, List.of(), null);
    }

    /**
     * 返回一个克隆上下文，替换 traceContext 字段（保持其它字段不变）。
     * 用于在 orchestrator 层注入/下钻 trace context，不污染上游构造方式。
     */
    public AgentContext withTraceContext(AgentTraceContext newTraceContext) {
        return new AgentContext(event, currentPhase, eventData, evidencePack, learnerState,
                tutorContext, problemContext, nodeOutputs, problemId, userId,
                sessionHistory, newTraceContext);
    }

    /**
     * Formats memory refs and session history into a prompt-injectable string.
     * Returns empty string if no context is available.
     */
    public String formatMemoryContext() {
        StringBuilder sb = new StringBuilder();

        if (learnerState != null && learnerState.recommendedActionBias() != null) {
            Object stylePrompt = learnerState.recommendedActionBias().get("teaching_style_prompt");
            if (stylePrompt instanceof String s && !s.isBlank()) {
                sb.append(s).append("\n\n");
            }
        }

        if (learnerState != null && learnerState.memoryRefs() != null && !learnerState.memoryRefs().isEmpty()) {
            sb.append("【学习者记忆】\n");
            for (Map<String, Object> ref : learnerState.memoryRefs()) {
                String summary = String.valueOf(ref.getOrDefault("memory_summary", ""));
                String type = String.valueOf(ref.getOrDefault("memory_type", ""));
                double confidence = ref.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
                if (!summary.isBlank()) {
                    sb.append("- [").append(type).append(", 置信度=")
                            .append(String.format("%.2f", confidence)).append("] ").append(summary).append("\n");
                }
            }
            sb.append("\n");
        }

        if (sessionHistory != null && !sessionHistory.isEmpty()) {
            sb.append("【前序阶段摘要】\n");
            for (Map<String, Object> turn : sessionHistory) {
                String agent = String.valueOf(turn.getOrDefault("agent", ""));
                String turnEvent = String.valueOf(turn.getOrDefault("event", ""));
                String summary = String.valueOf(turn.getOrDefault("summary", ""));
                if (!summary.isBlank()) {
                    sb.append("- ").append(agent).append("(").append(turnEvent).append("): ").append(summary).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
