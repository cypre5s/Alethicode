package com.alethicode.service.languagepack.impl;

import com.alethicode.service.languagepack.PageRetrievalHit;
import com.alethicode.service.languagepack.PageRetrievalService;
import com.alethicode.service.languagepack.RetrievalTrace;
import com.alethicode.service.rag.RagServiceClient;
import com.alethicode.service.rag.dto.RagCoursewareQueryRequest;
import com.alethicode.service.rag.dto.RagQueryHits;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page-level RAG 检索 — Phase 3 切流。
 *
 * <p>原实现：keyword (tsvector / cjk_bigram) + vector (16 维 page_embedding cosine)
 * 双路加权 0.3/0.7 融合，再做 lexical filter。<br>
 * 新实现：100% 走 alethicode-rag 的 LightRAG mix-mode（KG + 向量 + chunk 三路），
 * 再用 metadata 里的 {@code language_pack_id + page_no}（或 legacy {@code page_id}）
 * 反查 {@code language_pack_page} 拿回展示字段（document_title / page_title /
 * excerpt / preview_asset_path 等），保持 {@link PageRetrievalHit} 的 row shape 不变。
 *
 * <p>这是计划稿 Phase 3「fail-fast」原则的体现：alethicode-rag 不可用 → 直接抛
 * {@link com.alethicode.service.rag.RagServiceException}，前端显示「导学暂不可用」，
 * 不再静默降级到旧 SQL。
 */
@Service
@Transactional(readOnly = true)
public class PageRetrievalServiceImpl implements PageRetrievalService {

    private static final int FINAL_LIMIT = 4;

    /**
     * LightRAG 1.4.x chunk 不再带 entity_id/page_id，只有 source_path 形如
     * {@code language_pack/{lpId}/p{pageNo}}（由 {@code scripts/ops/rag_backfill.py}
     * 与 alethicode-rag {@code _coerce_data} 写入）。Java 侧用这条正则把 file_path
     * 拆成 (lpId, pageNo)，再去 {@code language_pack_page} 反查 page_id。
     */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("^language_pack/(\\d+)/p(\\d+)$");

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
            Long pageId = resolvePageId(meta, languagePackId);
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

    /**
     * 优先按 metadata 中的 {@code entity_id} / {@code page_id} 反查（兼容 LightRAG 1.3.x
     * 与历史回填的 chunk）。LightRAG 1.4.x chunk metadata 不含上述字段，alethicode-rag
     * {@code query.py#_parse_courseware_path} 会把 {@code file_path =
     * "language_pack/{lpId}/p{pageNo}"} 拆成 metadata.language_pack_id + metadata.page_no；
     * 万一拆分失败再用 {@link #FILE_PATH_PATTERN} 兜底解析原始 file_path。
     * 解析出的 {@code lpFromMeta} 必须等于当前请求的 {@code languagePackId}，避免跨课件包污染。
     */
    private Long resolvePageId(Map<String, Object> meta, Long languagePackId) {
        Long pageId = toLong(meta.get("entity_id"));
        if (pageId == null) {
            pageId = toLong(meta.get("page_id"));
        }
        if (pageId != null) {
            return pageId;
        }
        Long lpFromMeta = toLong(meta.get("language_pack_id"));
        Integer pageNoFromMeta = toInteger(meta.get("page_no"));
        if (lpFromMeta == null || pageNoFromMeta == null) {
            String filePath = meta.get("file_path") == null ? null : String.valueOf(meta.get("file_path"));
            if (filePath != null) {
                Matcher matcher = FILE_PATH_PATTERN.matcher(filePath);
                if (matcher.matches()) {
                    lpFromMeta = Long.valueOf(matcher.group(1));
                    pageNoFromMeta = Integer.valueOf(matcher.group(2));
                }
            }
        }
        if (lpFromMeta == null || pageNoFromMeta == null) {
            return null;
        }
        if (!lpFromMeta.equals(languagePackId)) {
            return null;
        }
        return lookupPageIdByPageNo(lpFromMeta, pageNoFromMeta);
    }

    private Long lookupPageIdByPageNo(Long lpId, Integer pageNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id FROM language_pack_page
                    WHERE language_pack_id = ? AND page_no = ?
                    ORDER BY chunk_index ASC, id ASC
                    LIMIT 1
                    """,
                    Long.class,
                    lpId, pageNo
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
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

    private static Integer toInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
