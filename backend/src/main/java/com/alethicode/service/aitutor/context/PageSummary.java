package com.alethicode.service.aitutor.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 课件具体页引用解析后的不可变摘要，作为 AI tutor chat evidence 注入 LLM prompt。
 *
 * <p>对应 token：{@code @page:<lpId>:<n>} 或 {@code @page:<n>}（课件问答页省略 lpId）。
 * 与 {@link CoursewareSummary} 相比，本 record 只承载单一页的正文，不做 RAG top-k 检索，
 * 让用户能精确锁定某一页提问（"这一页讲的递归是什么？"）。</p>
 *
 * <p>Design: Phase 2 sprint of chat composer plan。</p>
 */
public record PageSummary(
        Long languagePackId,
        String packName,
        Long documentId,
        String documentTitle,
        int pageNumber,
        String pageText,
        Instant retrievedAt
) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("language_pack_id", languagePackId);
        m.put("pack_name", packName == null ? "" : packName);
        m.put("document_id", documentId);
        m.put("document_title", documentTitle == null ? "" : documentTitle);
        m.put("page_number", pageNumber);
        m.put("page_text", pageText == null ? "" : pageText);
        m.put("retrieved_at", retrievedAt == null ? "" : retrievedAt.toString());
        return m;
    }
}
