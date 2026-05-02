package com.alethicode.service.aitutor.context;

import com.alethicode.service.ai.AiModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three-tier context compression pipeline inspired by Claude Code's architecture.
 *
 * Layer 1: truncateEvidence — zero-cost field-level truncation
 * Layer 2: collapseSessionHistory — zero-cost history folding
 * Layer 3: compactViaLlm — LLM-powered summarization (1 API call)
 *
 * Each layer is applied only when the estimated token count exceeds
 * the threshold that would trigger the next layer.
 */
@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    private static final int PROBLEM_CONTEXT_MAX_CHARS = 2500;
    private static final int CODE_CONTEXT_MAX_CHARS = 3000;
    private static final int EVIDENCE_FIELD_MAX_CHARS = 1500;
    private static final int HISTORY_KEEP_RECENT = 3;
    private static final int HISTORY_COLLAPSE_THRESHOLD = 5;
    private static final int LLM_COMPACT_CHAR_THRESHOLD = 12000;

    private final AiModelGateway aiModelGateway;

    public ContextCompressor(AiModelGateway aiModelGateway) {
        this.aiModelGateway = aiModelGateway;
    }

    /**
     * Layer 1: Truncate oversized evidence fields.
     * Cost: zero (pure string truncation).
     */
    public String truncateEvidence(String problemContext, int maxChars) {
        if (problemContext == null) return "";
        int limit = maxChars > 0 ? maxChars : PROBLEM_CONTEXT_MAX_CHARS;
        if (problemContext.length() <= limit) return problemContext;
        return problemContext.substring(0, limit) + "...";
    }

    public String truncateCode(String code) {
        if (code == null) return "";
        if (code.length() <= CODE_CONTEXT_MAX_CHARS) return code;
        return code.substring(0, CODE_CONTEXT_MAX_CHARS) + "...";
    }

    public String truncateField(String value) {
        if (value == null) return "";
        if (value.length() <= EVIDENCE_FIELD_MAX_CHARS) return value;
        return value.substring(0, EVIDENCE_FIELD_MAX_CHARS) + "...";
    }

    /**
     * Layer 2: Collapse session history entries older than the most recent N.
     * Earlier entries are folded into a single summary entry.
     * Cost: zero (in-memory aggregation).
     */
    public List<Map<String, Object>> collapseSessionHistory(List<Map<String, Object>> history) {
        if (history == null || history.size() <= HISTORY_COLLAPSE_THRESHOLD) {
            return history == null ? List.of() : history;
        }

        int splitAt = history.size() - HISTORY_KEEP_RECENT;
        List<Map<String, Object>> older = history.subList(0, splitAt);
        List<Map<String, Object>> recent = history.subList(splitAt, history.size());

        StringBuilder collapsedSummary = new StringBuilder();
        for (Map<String, Object> entry : older) {
            String agent = String.valueOf(entry.getOrDefault("agent", ""));
            String event = String.valueOf(entry.getOrDefault("event", ""));
            String summary = String.valueOf(entry.getOrDefault("summary", ""));
            if (!summary.isBlank()) {
                collapsedSummary.append(agent).append("(").append(event).append("): ")
                        .append(abbreviate(summary, 80)).append("; ");
            }
        }

        Map<String, Object> collapsedEntry = new LinkedHashMap<>();
        collapsedEntry.put("agent", "context_summary");
        collapsedEntry.put("event", "COLLAPSED");
        collapsedEntry.put("summary", abbreviate(collapsedSummary.toString(), 500));
        collapsedEntry.put("collapsed_count", older.size());

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(collapsedEntry);
        result.addAll(recent);
        return result;
    }

    /**
     * Layer 3: When total context exceeds threshold, use LLM to compress.
     * Cost: 1 API call.
     *
     * @param fullContext the assembled prompt text before sending to agent
     * @return compressed version, or original if under threshold
     */
    public String compactViaLlm(String fullContext) {
        if (fullContext == null || fullContext.length() <= LLM_COMPACT_CHAR_THRESHOLD) {
            return fullContext;
        }

        log.info("Context exceeds {}chars (actual={}), triggering LLM compact",
                LLM_COMPACT_CHAR_THRESHOLD, fullContext.length());
        try {
            String compressed = aiModelGateway.callForContent(
                    "请将以下教学上下文压缩为精炼摘要，保留：1)学生当前题目和阶段 2)关键错误模式 " +
                            "3)最近的教学进展 4)学生情绪状态。删除冗余细节。直接输出摘要文本。\n\n" +
                            fullContext
            );
            if (compressed != null && !compressed.isBlank() && compressed.length() < fullContext.length()) {
                log.info("LLM compact: {} -> {} chars ({} reduction)",
                        fullContext.length(), compressed.length(),
                        String.format("%.0f%%", (1.0 - (double) compressed.length() / fullContext.length()) * 100));
                return compressed;
            }
        } catch (Exception e) {
            log.warn("LLM compact failed, keeping original context: {}", e.getMessage());
        }
        return fullContext;
    }

    /**
     * Full pipeline: apply all three layers in sequence.
     */
    public CompressedContext compress(
            String problemContext,
            String code,
            List<Map<String, Object>> sessionHistory,
            String memoryBlock
    ) {
        String compressedProblem = truncateEvidence(problemContext, PROBLEM_CONTEXT_MAX_CHARS);
        String compressedCode = truncateCode(code);
        List<Map<String, Object>> compressedHistory = collapseSessionHistory(sessionHistory);

        int totalEstimate = estimateChars(compressedProblem) + estimateChars(compressedCode)
                + estimateChars(memoryBlock) + estimateHistoryChars(compressedHistory);

        boolean llmCompacted = false;
        String compactedMemory = memoryBlock;
        if (totalEstimate > LLM_COMPACT_CHAR_THRESHOLD && memoryBlock != null && memoryBlock.length() > 500) {
            compactedMemory = compactViaLlm(memoryBlock);
            llmCompacted = !compactedMemory.equals(memoryBlock);
        }

        return new CompressedContext(compressedProblem, compressedCode, compressedHistory, compactedMemory, llmCompacted);
    }

    private int estimateChars(String s) {
        return s == null ? 0 : s.length();
    }

    private int estimateHistoryChars(List<Map<String, Object>> history) {
        if (history == null) return 0;
        int total = 0;
        for (Map<String, Object> entry : history) {
            total += String.valueOf(entry.getOrDefault("summary", "")).length() + 30;
        }
        return total;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    public record CompressedContext(
            String problemContext,
            String code,
            List<Map<String, Object>> sessionHistory,
            String memoryBlock,
            boolean llmCompacted
    ) {}
}
