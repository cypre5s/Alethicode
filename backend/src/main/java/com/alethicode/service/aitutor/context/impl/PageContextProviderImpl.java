package com.alethicode.service.aitutor.context.impl;

import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.context.PageContextProvider;
import com.alethicode.service.aitutor.context.PageSummary;
import com.alethicode.service.aitutor.context.ReferenceResolver;
import com.alethicode.service.languagepack.LanguagePackQaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PageContextProviderImpl implements PageContextProvider {

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackQaService languagePackQaService;

    public PageContextProviderImpl(JdbcTemplate jdbcTemplate, LanguagePackQaService languagePackQaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.languagePackQaService = languagePackQaService;
    }

    @Override
    public List<PageSummary> resolvePageReferences(String username, @Nullable Long defaultLpId, List<String> rawTokens) {
        if (username == null || username.isBlank()) {
            throw BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (rawTokens == null || rawTokens.isEmpty()) {
            return List.of();
        }
        Map<String, ReferenceResolver.PageReference> refs = new LinkedHashMap<>();
        for (String raw : rawTokens) {
            ReferenceResolver.PageReference ref = ReferenceResolver.extractPageRef(raw);
            if (ref == null) continue;
            Long lpId = ref.lpId() == null ? defaultLpId : ref.lpId();
            if (lpId == null) continue;
            String dedupKey = lpId + ":" + (ref.chapter() == null ? "" : ref.chapter() + ".") + ref.pageNo();
            refs.putIfAbsent(dedupKey, new ReferenceResolver.PageReference(lpId, ref.chapter(), ref.pageNo()));
        }
        if (refs.isEmpty()) return List.of();

        Map<Long, String> allowed = loadAllowedPacks(username);
        List<PageSummary> result = new ArrayList<>();
        for (ReferenceResolver.PageReference ref : refs.values()) {
            if (!allowed.containsKey(ref.lpId())) {
                throw BusinessExceptions.fromLegacy("permission-denied", "Page reference not accessible: " + ref.lpId());
            }
            String packName = allowed.get(ref.lpId());
            if (ref.chapter() == null) {
                result.addAll(loadGlobalPage(ref.lpId(), ref.pageNo(), packName));
            } else {
                result.addAll(loadChapterPage(ref.lpId(), ref.chapter(), ref.pageNo(), packName));
            }
        }
        return result;
    }

    private List<PageSummary> loadGlobalPage(Long lpId, int globalPageNo, String packName) {
        return jdbcTemplate.query("""
                WITH ordered_pages AS (
                    SELECT p.language_pack_id,
                           p.document_id,
                           d.original_filename AS document_title,
                           p.page_no,
                           p.chunk_index,
                           p.page_text,
                           dense_rank() OVER (ORDER BY d.sort_order, d.id, p.page_no) AS global_page_no
                    FROM language_pack_page p
                    JOIN language_pack_document d ON d.id = p.document_id
                    WHERE p.language_pack_id = ?
                      AND d.status = 'normalized'
                )
                SELECT language_pack_id,
                       document_id,
                       document_title,
                       page_no,
                       string_agg(page_text, E'\\n' ORDER BY chunk_index) AS page_text
                FROM ordered_pages
                WHERE global_page_no = ?
                GROUP BY language_pack_id, document_id, document_title, page_no
                """, (rs, rowNum) -> new PageSummary(
                rs.getLong("language_pack_id"),
                packName,
                rs.getLong("document_id"),
                rs.getString("document_title"),
                rs.getInt("page_no"),
                rs.getString("page_text"),
                Instant.now()
        ), lpId, globalPageNo);
    }

    /**
     * 二级目录引用：第 {@code chapter} 个 normalized 文档（按 {@code (sort_order, id)} 1-based）
     * 内的 {@code page_no} = {@code pageNo} 对应页。命中失败返回空列表，让上层把空 PageSummary
     * 透传给 LLM；不抛异常，避免学生输入笔误打断 chat。
     */
    private List<PageSummary> loadChapterPage(Long lpId, int chapter, int pageNo, String packName) {
        return jdbcTemplate.query("""
                WITH ordered_documents AS (
                    SELECT id,
                           original_filename,
                           dense_rank() OVER (ORDER BY sort_order, id) AS chapter_index
                    FROM language_pack_document
                    WHERE language_pack_id = ?
                      AND status = 'normalized'
                )
                SELECT p.language_pack_id,
                       p.document_id,
                       od.original_filename AS document_title,
                       p.page_no,
                       string_agg(p.page_text, E'\\n' ORDER BY p.chunk_index) AS page_text
                FROM language_pack_page p
                JOIN ordered_documents od ON od.id = p.document_id
                WHERE p.language_pack_id = ?
                  AND od.chapter_index = ?
                  AND p.page_no = ?
                GROUP BY p.language_pack_id, p.document_id, od.original_filename, p.page_no
                """, (rs, rowNum) -> new PageSummary(
                rs.getLong("language_pack_id"),
                packName,
                rs.getLong("document_id"),
                rs.getString("document_title"),
                rs.getInt("page_no"),
                rs.getString("page_text"),
                Instant.now()
        ), lpId, lpId, chapter, pageNo);
    }

    private Map<Long, String> loadAllowedPacks(String username) {
        Map<Long, String> allowed = new LinkedHashMap<>();
        for (Map<String, Object> pack : languagePackQaService.listQaPacks(username)) {
            Object rawId = pack.get("id");
            if (!(rawId instanceof Number n)) continue;
            allowed.put(n.longValue(), String.valueOf(pack.getOrDefault("name", "")));
        }
        return allowed;
    }
}
