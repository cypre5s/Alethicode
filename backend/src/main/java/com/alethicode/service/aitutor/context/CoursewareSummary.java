package com.alethicode.service.aitutor.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于 AI Tutor Chat 证据的轻量课件引用摘要。
 *
 * <p>摘要保存当前问题下检索到的 top-k 页面片段，帮助 LLM 基于学生主动引用的课件作答。</p>
 *
 * <p>Design: <code>docs/plans/2026-05-03-courseware-reference-token-design.md</code></p>
 */
public record CoursewareSummary(
        Long languagePackId,
        String packName,
        List<RetrievedChunk> chunks,
        Instant retrievedAt
) {

    public CoursewareSummary {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    /** 转为可传给 HTTP 和 prompt 的 snake_case 映射。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("language_pack_id", languagePackId);
        m.put("pack_name", packName == null ? "" : packName);
        m.put("retrieved_at", retrievedAt == null ? "" : retrievedAt.toString());
        List<Map<String, Object>> chunkMaps = new ArrayList<>(chunks.size());
        for (RetrievedChunk chunk : chunks) {
            chunkMaps.add(chunk.toMap());
        }
        m.put("chunks", chunkMaps);
        return m;
    }

    /**
     * 带来源位置的单个课件页面片段。
     *
     * @param documentId 语言包内的 PDF 文档 ID
     * @param documentTitle 文档标题
     * @param pageNumber 文档内页码，从 1 开始
     * @param text 作为 LLM 上下文的片段文本
     * @param score 检索相关性分数，仅在同一次检索内可比较
     */
    public record RetrievedChunk(
            Long documentId,
            String documentTitle,
            Integer pageNumber,
            String text,
            double score
    ) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("document_id", documentId);
            m.put("document_title", documentTitle == null ? "" : documentTitle);
            m.put("page_number", pageNumber);
            m.put("text", text == null ? "" : text);
            m.put("score", score);
            return m;
        }
    }
}
