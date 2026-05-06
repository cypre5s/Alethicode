package com.alethicode.service.aitutor.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学习笔记（LearnerNotebook）条目引用解析后的不可变摘要。
 *
 * <p>对应 token：{@code @notebook:<entryId>}。让学生在做新题时引用过去自己写下的笔记
 * （"和上次那道递归题对比"）。条目鉴权：必须是当前用户自己的笔记。</p>
 *
 * <p>Design: Phase 2 sprint of chat composer plan。</p>
 */
public record NotebookSummary(
        String entryId,
        String title,
        String content,
        Instant entryCreatedAt,
        Instant retrievedAt
) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entry_id", entryId);
        m.put("title", title == null ? "" : title);
        m.put("content", content == null ? "" : content);
        m.put("entry_created_at", entryCreatedAt == null ? "" : entryCreatedAt.toString());
        m.put("retrieved_at", retrievedAt == null ? "" : retrievedAt.toString());
        return m;
    }
}
