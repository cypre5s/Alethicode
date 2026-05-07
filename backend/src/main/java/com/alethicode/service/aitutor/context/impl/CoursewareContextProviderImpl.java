package com.alethicode.service.aitutor.context.impl;

import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.context.CoursewareContextProvider;
import com.alethicode.service.aitutor.context.CoursewareSummary;
import com.alethicode.service.aitutor.context.ReferenceResolver;
import com.alethicode.service.languagepack.LanguagePackQaService;
import com.alethicode.service.languagepack.PageRetrievalHit;
import com.alethicode.service.languagepack.PageRetrievalService;
import com.alethicode.service.rag.RagServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoursewareContextProviderImpl implements CoursewareContextProvider {

    private static final Logger log = LoggerFactory.getLogger(CoursewareContextProviderImpl.class);

    private final LanguagePackQaService languagePackQaService;
    private final PageRetrievalService pageRetrievalService;

    public CoursewareContextProviderImpl(
            LanguagePackQaService languagePackQaService,
            PageRetrievalService pageRetrievalService
    ) {
        this.languagePackQaService = languagePackQaService;
        this.pageRetrievalService = pageRetrievalService;
    }

    @Override
    public List<CoursewareSummary> resolveCoursewareReferences(
            String username,
            List<String> rawTokens,
            String currentQuery,
            @Nullable String recentContext
    ) {
        if (username == null || username.isBlank()) {
            throw BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (rawTokens == null || rawTokens.isEmpty()) {
            return List.of();
        }
        // 顺序保留 + 去重——同一 lp_id 在一句话里多次出现只检索一次
        Map<Long, Boolean> seen = new LinkedHashMap<>();
        for (String raw : rawTokens) {
            Long lpId = ReferenceResolver.extractCoursewareId(raw);
            if (lpId == null) continue;
            seen.putIfAbsent(lpId, Boolean.TRUE);
        }
        if (seen.isEmpty()) {
            return List.of();
        }

        // 鉴权：按当前用户能 listQaPacks 看到的清单做白名单
        Map<Long, String> allowedLpToName = loadAllowedPacks(username);

        // 越权 fail-fast：哪怕一条 lp_id 不在允许列表里都直接 403
        for (Long lpId : seen.keySet()) {
            if (!allowedLpToName.containsKey(lpId)) {
                throw BusinessExceptions.fromLegacy(
                        "permission-denied",
                        "Courseware reference not accessible: " + lpId
                );
            }
        }

        String query = currentQuery == null ? "" : currentQuery.trim();
        List<CoursewareSummary> result = new ArrayList<>(seen.size());
        for (Long lpId : seen.keySet()) {
            String packName = allowedLpToName.get(lpId);
            List<CoursewareSummary.RetrievedChunk> chunks = retrieveChunksOrDegrade(
                    lpId, packName, query, recentContext);
            result.add(new CoursewareSummary(lpId, packName, chunks, Instant.now()));
        }
        return result;
    }

    /**
     * RAG 异常时返回空 chunks 但不阻断：CoursewareSummary 仍然返回（带 lp 信息），
     * 由 chat 入口决定是把空 chunks 拼进 prompt（让 LLM 知道引用了课件但检索失败）
     * 还是静默降级。这与 plan §三「Failfast 原则」一致：越权 fail-fast，
     * 但 RAG 服务级故障不应阻断用户对话主链路。
     */
    private List<CoursewareSummary.RetrievedChunk> retrieveChunksOrDegrade(
            Long lpId, String packName, String query, @Nullable String recentContext
    ) {
        if (query.isEmpty()) {
            return List.of();
        }
        try {
            List<PageRetrievalHit> hits = pageRetrievalService.retrieve(lpId, query, recentContext);
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }
            List<CoursewareSummary.RetrievedChunk> chunks = new ArrayList<>(hits.size());
            for (PageRetrievalHit hit : hits) {
                String text = preferText(hit);
                if (text == null || text.isBlank()) continue;
                chunks.add(new CoursewareSummary.RetrievedChunk(
                        hit.documentId(),
                        hit.documentTitle(),
                        hit.pageNo(),
                        text,
                        hit.score()
                ));
            }
            return chunks;
        } catch (RagServiceException ex) {
            log.warn("@courseware:{} ({}) RAG retrieve failed, returning empty chunks: {}",
                    lpId, packName, ex.getMessage());
            return List.of();
        }
    }

    /** 优先用 page_text 全文（容量上限由 RAG 服务控制），fallback 到摘录。 */
    private static String preferText(PageRetrievalHit hit) {
        if (hit == null) return null;
        if (hit.pageText() != null && !hit.pageText().isBlank()) return hit.pageText();
        return hit.excerpt();
    }

    private Map<Long, String> loadAllowedPacks(String username) {
        List<Map<String, Object>> packs = languagePackQaService.listQaPacks(username);
        if (packs == null || packs.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> allowed = new LinkedHashMap<>();
        for (Map<String, Object> pack : packs) {
            Long id = asLong(pack.get("id"));
            if (id == null) continue;
            String name = asString(pack.get("name"));
            allowed.put(id, name == null ? "" : name);
        }
        return allowed;
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
