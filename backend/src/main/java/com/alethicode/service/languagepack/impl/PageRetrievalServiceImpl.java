package com.alethicode.service.languagepack.impl;

import com.alethicode.service.languagepack.PageRetrievalHit;
import com.alethicode.service.languagepack.PageRetrievalService;
import com.alethicode.service.languagepack.RetrievalTrace;
import com.alethicode.service.rag.RagServiceClient;
import com.alethicode.service.rag.dto.RagCoursewareQueryRequest;
import com.alethicode.service.rag.dto.RagQueryHits;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Page-level RAG 检索 — Phase 3 切流。
 *
 * <p>原实现：keyword (tsvector / cjk_bigram) + vector (16 维 page_embedding cosine)
 * 双路加权 0.3/0.7 融合，再做 lexical filter。<br>
 * 新实现：100% 走 alethicode-rag 的 LightRAG mix-mode（KG + 向量 + chunk 三路），
 * 再用 metadata 里的 {@code page_id} 反查 {@code language_pack_page} 拿回展示字段
 * （document_title / page_title / excerpt / preview_asset_path 等），保持
 * {@link PageRetrievalHit} 的 row shape 不变。
 *
 * <p>这是计划稿 Phase 3「fail-fast」原则的体现：alethicode-rag 不可用 → 直接抛
 * {@link com.alethicode.service.rag.RagServiceException}，前端显示「导学暂不可用」，
 * 不再静默降级到旧 SQL。
 */
@Service
@Transactional(readOnly = true)
public class PageRetrievalServiceImpl implements PageRetrievalService {

    private static final int FINAL_LIMIT = 4;

    private final JdbcTemplate jdbcTemplate;
    private final RagServiceClient ragServiceClient;

    public PageRetrievalServiceImpl(JdbcTemplate jdbcTemplate, RagServiceClient ragServiceClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragServiceClient = ragServiceClient;
    }

    @Override
    public List<PageRetrievalHit> retrieve(Long languagePackId, String queryText, String recentContext) {
        if (languagePackId == null) {
            return List.of();
        }
        String normalizedQuery = normalizeQuery(queryText, recentContext);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        RagQueryHits hits = ragServiceClient.queryCourseware(new RagCoursewareQueryRequest(
                languagePackId, normalizedQuery, List.of(), FINAL_LIMIT * 2
        ));

        List<PageRetrievalHit> results = new ArrayList<>();
        for (RagQueryHits.RetrievedChunk chunk : hits.chunks()) {
            Map<String, Object> meta = chunk.metadata() == null ? Map.of() : chunk.metadata();
            Long languagePackMeta = toLong(meta.get("language_pack_id"));
            if (languagePackMeta != null && !languagePackId.equals(languagePackMeta)) {
                continue;
            }
            Long pageId = toLong(meta.get("entity_id"));
            if (pageId == null) {
                pageId = toLong(meta.get("page_id"));
            }
            if (pageId == null) {
                continue;
            }
            Map<String, Object> bizRow = loadPageRow(pageId, languagePackId);
            if (bizRow == null) {
                continue;
            }
            PageRetrievalHit hit = new PageRetrievalHit(
                    pageId,
                    toLong(bizRow.get("document_id")),
                    String.valueOf(bizRow.getOrDefault("original_filename", "")),
                    intOrZero(bizRow.get("page_no")),
                    String.valueOf(bizRow.getOrDefault("page_title", "")),
                    String.valueOf(bizRow.getOrDefault("excerpt", "")),
                    String.valueOf(bizRow.getOrDefault("page_text", "")),
                    String.valueOf(bizRow.getOrDefault("preview_asset_path", "")),
                    Math.round((chunk.score() == null ? 0.5 : chunk.score()) * 1000.0) / 1000.0
            );
            results.add(hit);
            if (results.size() >= FINAL_LIMIT) {
                break;
            }
        }
        return results;
    }

    @Override
    public RetrievalTrace retrieveWithTrace(Long languagePackId, String queryText, String recentContext) {
        long start = System.currentTimeMillis();
        String normalizedQuery = normalizeQuery(queryText, recentContext);
        List<PageRetrievalHit> hits = retrieve(languagePackId, queryText, recentContext);
        long latency = System.currentTimeMillis() - start;
        boolean wasRewritten = !normalizedQuery.equals(queryText == null ? "" : queryText.trim());
        return new RetrievalTrace(
                queryText == null ? "" : queryText.trim(),
                wasRewritten ? normalizedQuery : null,
                hits,
                hits.size(),
                latency,
                "lightrag-mix"
        );
    }

    private Map<String, Object> loadPageRow(Long pageId, Long languagePackId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT p.id, p.document_id, d.original_filename, p.page_no, p.page_title,
                       p.page_text, p.excerpt, p.preview_asset_path
                FROM language_pack_page p
                JOIN language_pack_document d ON d.id = p.document_id
                WHERE p.id = ? AND p.language_pack_id = ?
                LIMIT 1
                """,
                pageId, languagePackId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String normalizeQuery(String queryText, String recentContext) {
        String query = queryText == null ? "" : queryText.trim();
        String context = recentContext == null ? "" : recentContext.trim();
        if (context.isBlank()) {
            return query;
        }
        if (query.isBlank()) {
            return context;
        }
        return query + "\n最近上下文：\n" + context;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int intOrZero(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
