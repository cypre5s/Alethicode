package com.alethicode.service.aitutor.context;

import com.alethicode.service.aitutor.agent.AgentContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Assembles agent prompts from four semantic layers.
 *
 * Layer 1: basePrompt      — agent role + output format (cacheable, rarely changes)
 * Layer 2: memoryLayer      — learner memory refs + teaching style (changes slowly)
 * Layer 3: evidenceLayer    — problem context + submission + code (changes per request)
 * Layer 4: sessionLayer     — compressed session history (changes per turn)
 *
 * Each layer is separated by a clear delimiter, enabling future prompt-cache
 * optimizations where earlier layers can be cached across requests.
 */
@Component
public class LayeredPromptBuilder {

    private static final String LAYER_SEPARATOR = "\n\n---\n\n";

    private final ContextCompressor compressor;

    public LayeredPromptBuilder(ContextCompressor compressor) {
        this.compressor = compressor;
    }

    /**
     * Builds a complete user message from the four layers.
     *
     * @param context the agent context
     * @param problemContextMaxChars max chars for problem context truncation
     * @return assembled prompt string with layered structure
     */
    public String buildUserMessage(AgentContext context, int problemContextMaxChars) {
        StringBuilder sb = new StringBuilder();

        String memoryLayer = buildMemoryLayer(context);
        if (!memoryLayer.isEmpty()) {
            sb.append(memoryLayer).append(LAYER_SEPARATOR);
        }

        String evidenceLayer = buildEvidenceLayer(context, problemContextMaxChars);
        sb.append(evidenceLayer);

        String sessionLayer = buildSessionLayer(context);
        if (!sessionLayer.isEmpty()) {
            sb.append(LAYER_SEPARATOR).append(sessionLayer);
        }

        sb.append(LAYER_SEPARATOR)
                .append("【当前阶段】").append(nullSafe(context.currentPhase())).append("\n")
                .append("【当前事件】").append(nullSafe(context.event()));

        return sb.toString();
    }

    private String buildMemoryLayer(AgentContext context) {
        if (context.learnerState() == null) return "";

        StringBuilder sb = new StringBuilder();

        if (context.learnerState().recommendedActionBias() != null) {
            Object stylePrompt = context.learnerState().recommendedActionBias().get("teaching_style_prompt");
            if (stylePrompt instanceof String s && !s.isBlank()) {
                sb.append(s).append("\n\n");
            }
        }

        if (context.learnerState().memoryRefs() != null && !context.learnerState().memoryRefs().isEmpty()) {
            sb.append("【学习者记忆】\n");
            for (Map<String, Object> ref : context.learnerState().memoryRefs()) {
                String summary = String.valueOf(ref.getOrDefault("memory_summary", ""));
                String type = String.valueOf(ref.getOrDefault("memory_type", ""));
                double confidence = ref.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
                if (!summary.isBlank()) {
                    sb.append("- [").append(type).append(", 置信度=")
                            .append(String.format("%.2f", confidence)).append("] ").append(summary).append("\n");
                }
            }
        }

        return sb.toString().trim();
    }

    private String buildEvidenceLayer(AgentContext context, int maxChars) {
        String problem = compressor.truncateEvidence(context.problemContext(), maxChars);
        return "【题目上下文】\n" + problem;
    }

    private String buildSessionLayer(AgentContext context) {
        List<Map<String, Object>> history = context.sessionHistory();
        if (history == null || history.isEmpty()) return "";

        List<Map<String, Object>> compressed = compressor.collapseSessionHistory(history);

        StringBuilder sb = new StringBuilder("【前序阶段摘要】\n");
        for (Map<String, Object> turn : compressed) {
            String agent = String.valueOf(turn.getOrDefault("agent", ""));
            String event = String.valueOf(turn.getOrDefault("event", ""));
            String summary = String.valueOf(turn.getOrDefault("summary", ""));
            if (!summary.isBlank()) {
                sb.append("- ").append(agent).append("(").append(event).append("): ").append(summary).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
