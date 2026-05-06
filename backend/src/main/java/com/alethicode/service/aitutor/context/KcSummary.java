package com.alethicode.service.aitutor.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点（Knowledge Concept）节点引用解析后的不可变摘要。
 *
 * <p>对应 token：{@code @kc:<kcId>}。kcId 是 KnowledgeStarMap 节点 id，含字母数字、点、
 * 下划线、横线。摘要里附带学生的 mastery 概率，让 LLM 能基于学生当前掌握度调整讲解深度。</p>
 *
 * <p>Design: Phase 2 sprint of chat composer plan。</p>
 */
public record KcSummary(
        String kcId,
        String name,
        String description,
        List<String> chapterPath,
        double mastery,
        Instant retrievedAt
) {

    public KcSummary {
        chapterPath = chapterPath == null ? List.of() : List.copyOf(chapterPath);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kc_id", kcId);
        m.put("name", name == null ? "" : name);
        m.put("description", description == null ? "" : description);
        m.put("chapter_path", new ArrayList<>(chapterPath));
        m.put("mastery", mastery);
        m.put("retrieved_at", retrievedAt == null ? "" : retrievedAt.toString());
        return m;
    }
}
