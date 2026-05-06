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
            refs.putIfAbsent(lpId + ":" + ref.pageNo(), new ReferenceResolver.PageReference(lpId, ref.pageNo()));
        }
        if (refs.isEmpty()) return List.of();

        Map<Long, String> allowed = loadAllowedPacks(username);
        List<PageSummary> result = new ArrayList<>();
        for (ReferenceResolver.PageReference ref : refs.values()) {
            if (!allowed.containsKey(ref.lpId())) {
                throw BusinessExceptions.fromLegacy("permission-denied", "Page reference not accessible: " + ref.lpId());
            }
            result.addAll(loadPage(ref.lpId(), ref.pageNo(), allowed.get(ref.lpId())));
        }
        return result;
    }

    private List<PageSummary> loadPage(Long lpId, int pageNo, String packName) {
        return jdbcTemplate.query("""
                SELECT p.language_pack_id, p.document_id, d.title AS document_title,
                       p.page_no, string_agg(p.page_text, E'\\n' ORDER BY p.chunk_index) AS page_text
                FROM language_pack_page p
                JOIN language_pack_document d ON d.id = p.document_id
                WHERE p.language_pack_id = ? AND p.page_no = ?
                GROUP BY p.language_pack_id, p.document_id, d.title, p.page_no
                ORDER BY p.document_id
                LIMIT 1
                """, (rs, rowNum) -> new PageSummary(
                rs.getLong("language_pack_id"),
                packName,
                rs.getLong("document_id"),
                rs.getString("document_title"),
                rs.getInt("page_no"),
                rs.getString("page_text"),
                Instant.now()
        ), lpId, pageNo);
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
