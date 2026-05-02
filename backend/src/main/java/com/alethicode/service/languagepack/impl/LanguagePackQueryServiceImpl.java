package com.alethicode.service.languagepack.impl;

import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackQueryService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class LanguagePackQueryServiceImpl implements LanguagePackQueryService {

    private final JdbcTemplate jdbcTemplate;

    public LanguagePackQueryServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> listPublishedPacks() {
        List<Map<String, Object>> packs = jdbcTemplate.query(
                """
                SELECT id, slug, version, name, primary_language, status,
                       document_count, page_count, chapter_count, kc_count,
                       example_count, problem_count, creator_id, create_time, update_time
                FROM language_pack
                WHERE status = 'published'
                ORDER BY update_time DESC
                """,
                (rs, rowNum) -> mapPackRow(rs)
        );
        packs.forEach(this::applyDerivedCounts);
        return packs;
    }

    @Override
    public List<Map<String, Object>> listVisiblePacks(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        Long userId = findUserId(username);
        if (userId == null) {
            return List.of();
        }
        if (isPureAdmin(username)) {
            return listPublishedPacks();
        }
        if (isTeacher(username)) {
            List<Map<String, Object>> packs = jdbcTemplate.query(
                    """
                    SELECT id, slug, version, name, primary_language, status,
                           document_count, page_count, chapter_count, kc_count,
                           example_count, problem_count, creator_id, create_time, update_time
                    FROM language_pack
                    WHERE status = 'published'
                      AND (creator_id IS NULL
                           OR creator_id = ?
                           OR (SELECT admin_type FROM "user" WHERE id = creator_id) IN ('Admin'))
                    ORDER BY update_time DESC
                    """,
                    (rs, rowNum) -> mapPackRow(rs),
                    userId
            );
            packs.forEach(this::applyDerivedCounts);
            return packs;
        }
        List<Map<String, Object>> packs = jdbcTemplate.query(
                """
                WITH current_classroom AS (
                    SELECT cm.classroom_id
                    FROM classroom_member cm
                    JOIN classroom c ON c.id = cm.classroom_id
                    WHERE cm.user_id = ?
                      AND c.is_active = true
                    ORDER BY cm.update_time DESC NULLS LAST, cm.join_time DESC NULLS LAST, cm.classroom_id DESC
                    LIMIT 1
                )
                SELECT DISTINCT lp.id, lp.slug, lp.version, lp.name, lp.primary_language, lp.status,
                       lp.document_count, lp.page_count, lp.chapter_count, lp.kc_count,
                       lp.example_count, lp.problem_count, lp.creator_id, lp.create_time, lp.update_time,
                       bool_or(cm.classroom_id = cc.classroom_id) AS is_current_classroom_pack,
                       max(cm.update_time) AS last_enrolled_at
                FROM classroom_member cm
                JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
                JOIN classroom c ON c.id = cm.classroom_id
                JOIN language_pack lp ON lp.id = clp.language_pack_id
                LEFT JOIN current_classroom cc ON true
                WHERE cm.user_id = ?
                  AND c.is_active = true
                  AND lp.status = 'published'
                GROUP BY lp.id, lp.slug, lp.version, lp.name, lp.primary_language, lp.status,
                         lp.document_count, lp.page_count, lp.chapter_count, lp.kc_count,
                         lp.example_count, lp.problem_count, lp.creator_id, lp.create_time, lp.update_time
                ORDER BY is_current_classroom_pack DESC, last_enrolled_at DESC NULLS LAST, lp.update_time DESC
                """,
                (rs, rowNum) -> mapPackRow(rs),
                userId,
                userId
        );
        packs.forEach(this::applyDerivedCounts);
        return packs;
    }

    @Override
    public Map<String, Object> getPackDetail(Long languagePackId) {
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                SELECT id, slug, version, name, primary_language, description, status,
                       document_count, page_count, chapter_count, kc_count,
                       example_count, problem_count, creator_id, create_time, update_time
                FROM language_pack
                WHERE id = ? AND status = 'published'
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = mapPackRow(rs);
                    item.put("description", safeString(rs.getString("description")));
                    return item;
                },
                languagePackId
        );
        if (results.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Language pack not found");
        }
        Map<String, Object> pack = results.getFirst();
        applyDerivedCounts(pack);

        List<Map<String, Object>> chapters = listPackChapters(languagePackId);
        if (chapters.isEmpty()) {
            chapters = loadFallbackChaptersFromAi(languagePackId);
        }
        pack.put("chapters", chapters);

        List<Map<String, Object>> kcs = jdbcTemplate.query(
                """
                SELECT k.id, k.name, k.name_en, k.description,
                       c.title AS chapter_title, c.chapter_index
                FROM language_pack_kc k
                LEFT JOIN language_pack_chapter c ON c.id = k.chapter_id
                WHERE k.language_pack_id = ?
                ORDER BY c.chapter_index, k.id
                """,
                (rs, rowNum) -> {
                    Map<String, Object> kc = new LinkedHashMap<>();
                    kc.put("id", rs.getLong("id"));
                    kc.put("name", rs.getString("name"));
                    kc.put("name_en", rs.getString("name_en"));
                    kc.put("description", safeString(rs.getString("description")));
                    kc.put("chapter_title", rs.getString("chapter_title"));
                    kc.put("chapter_index", nullableInt(rs, "chapter_index"));
                    return kc;
                },
                languagePackId
        );
        if (kcs.isEmpty()) {
            kcs = loadFallbackKcsFromAi(languagePackId);
        }
        pack.put("kcs", kcs);

        return pack;
    }

    @Override
    public List<Map<String, Object>> listPackDocuments(Long languagePackId) {
        return jdbcTemplate.query(
                """
                SELECT d.id, d.original_filename, d.page_count,
                       d.status, d.sort_order, d.create_time
                FROM language_pack_document d
                WHERE d.language_pack_id = ? AND d.status = 'normalized'
                ORDER BY d.sort_order, d.id
                """,
                (rs, rowNum) -> {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("id", rs.getLong("id"));
                    doc.put("original_filename", rs.getString("original_filename"));
                    doc.put("page_count", rs.getInt("page_count"));
                    doc.put("status", rs.getString("status"));
                    doc.put("create_time", toInstant(rs.getTimestamp("create_time")));
                    return doc;
                },
                languagePackId
        );
    }

    @Override
    public List<Map<String, Object>> listPackChapters(Long languagePackId) {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.chapter_index, c.title, c.description,
                       c.page_range_start, c.page_range_end,
                       (SELECT count(*) FROM language_pack_kc k WHERE k.chapter_id = c.id) AS kc_count
                FROM language_pack_chapter c
                WHERE c.language_pack_id = ?
                ORDER BY c.chapter_index
                """,
                (rs, rowNum) -> {
                    Map<String, Object> ch = new LinkedHashMap<>();
                    ch.put("id", rs.getLong("id"));
                    ch.put("chapter_index", rs.getInt("chapter_index"));
                    ch.put("title", rs.getString("title"));
                    ch.put("description", safeString(rs.getString("description")));
                    ch.put("page_range_start", nullableInt(rs, "page_range_start"));
                    ch.put("page_range_end", nullableInt(rs, "page_range_end"));
                    ch.put("kc_count", rs.getInt("kc_count"));
                    return ch;
                },
                languagePackId
        );
    }

    @Override
    public Map<String, Object> getPagePreview(Long languagePackId, Long documentId, Integer pageNo) {
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                SELECT p.id, p.document_id, p.page_no, p.page_title, p.excerpt, p.create_time
                FROM language_pack_page p
                WHERE p.language_pack_id = ? AND p.document_id = ? AND p.page_no = ?
                ORDER BY p.chunk_index
                LIMIT 1
                """,
                (rs, rowNum) -> {
                    Map<String, Object> page = new LinkedHashMap<>();
                    page.put("id", rs.getLong("id"));
                    page.put("document_id", rs.getLong("document_id"));
                    page.put("page_no", rs.getInt("page_no"));
                    page.put("page_title", rs.getString("page_title"));
                    page.put("excerpt", safeString(rs.getString("excerpt")));
                    page.put("create_time", toInstant(rs.getTimestamp("create_time")));
                    return page;
                },
                languagePackId, documentId, pageNo
        );
        if (results.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Page not found");
        }
        return results.getFirst();
    }

    private Map<String, Object> mapPackRow(ResultSet rs) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getLong("id"));
        item.put("slug", rs.getString("slug"));
        item.put("version", rs.getInt("version"));
        item.put("name", rs.getString("name"));
        item.put("primary_language", rs.getString("primary_language"));
        item.put("status", rs.getString("status"));
        item.put("document_count", rs.getInt("document_count"));
        item.put("page_count", rs.getInt("page_count"));
        item.put("chapter_count", rs.getInt("chapter_count"));
        item.put("kc_count", rs.getInt("kc_count"));
        item.put("example_count", rs.getInt("example_count"));
        item.put("problem_count", rs.getInt("problem_count"));
        long creatorIdRaw = rs.getLong("creator_id");
        item.put("creator_id", rs.wasNull() ? null : creatorIdRaw);
        item.put("create_time", toInstant(rs.getTimestamp("create_time")));
        item.put("update_time", toInstant(rs.getTimestamp("update_time")));
        return item;
    }

    private void applyDerivedCounts(Map<String, Object> pack) {
        Long languagePackId = toLong(pack.get("id"));
        if (languagePackId == null) {
            return;
        }

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_document WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer pageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_page WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer chapterCountFromPack = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_chapter WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer chapterCountFromAi = jdbcTemplate.queryForObject(
                """
                SELECT count(distinct chapter)
                FROM ai_knowledge_component
                WHERE language_pack_id = ?
                  AND trim(coalesce(chapter, '')) <> ''
                """,
                Integer.class,
                languagePackId
        );
        Integer kcCountFromPack = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_kc WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer kcCountFromAi = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_knowledge_component WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer exampleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_example WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer problemCountFromPack = jdbcTemplate.queryForObject(
                "SELECT count(distinct problem_id) FROM language_pack_problem_mapping WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );
        Integer problemCountFromAi = jdbcTemplate.queryForObject(
                "SELECT count(distinct problem_id) FROM ai_problem_kc_mapping WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        );

        int finalChapterCount = firstPositive(chapterCountFromPack, chapterCountFromAi);
        int finalKcCount = firstPositive(kcCountFromPack, kcCountFromAi);
        int finalProblemCount = firstPositive(problemCountFromPack, problemCountFromAi);

        pack.put("document_count", nullSafeInt(documentCount));
        pack.put("page_count", nullSafeInt(pageCount));
        pack.put("chapter_count", finalChapterCount);
        pack.put("kc_count", finalKcCount);
        pack.put("example_count", nullSafeInt(exampleCount));
        pack.put("problem_count", finalProblemCount);
    }

    private List<Map<String, Object>> loadFallbackChaptersFromAi(Long languagePackId) {
        return jdbcTemplate.query(
                """
                SELECT chapter, count(*) AS kc_count
                FROM ai_knowledge_component
                WHERE language_pack_id = ?
                  AND trim(coalesce(chapter, '')) <> ''
                GROUP BY chapter
                ORDER BY chapter
                """,
                (rs, rowNum) -> {
                    Map<String, Object> ch = new LinkedHashMap<>();
                    ch.put("id", -(rowNum + 1L));
                    ch.put("chapter_index", rowNum + 1);
                    ch.put("title", rs.getString("chapter"));
                    ch.put("description", "");
                    ch.put("page_range_start", null);
                    ch.put("page_range_end", null);
                    ch.put("kc_count", rs.getInt("kc_count"));
                    return ch;
                },
                languagePackId
        );
    }

    private List<Map<String, Object>> loadFallbackKcsFromAi(Long languagePackId) {
        return jdbcTemplate.query(
                """
                SELECT id, name, name_en, description, chapter
                FROM ai_knowledge_component
                WHERE language_pack_id = ?
                ORDER BY chapter, id
                """,
                (rs, rowNum) -> {
                    Map<String, Object> kc = new LinkedHashMap<>();
                    kc.put("id", rs.getLong("id"));
                    kc.put("name", rs.getString("name"));
                    kc.put("name_en", rs.getString("name_en"));
                    kc.put("description", safeString(rs.getString("description")));
                    kc.put("chapter_title", safeString(rs.getString("chapter")));
                    kc.put("chapter_index", null);
                    return kc;
                },
                languagePackId
        );
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int firstPositive(Integer primary, Integer fallback) {
        int first = nullSafeInt(primary);
        if (first > 0) {
            return first;
        }
        return nullSafeInt(fallback);
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private Long findUserId(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM \"user\" WHERE username = ?",
                    Long.class,
                    username
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private boolean isPureAdmin(String username) {
        try {
            String adminType = jdbcTemplate.queryForObject(
                    "SELECT admin_type FROM \"user\" WHERE username = ?",
                    String.class,
                    username
            );
            return "Admin".equals(adminType);
        } catch (EmptyResultDataAccessException ignored) {
            return false;
        }
    }

    private boolean isTeacher(String username) {
        try {
            String adminType = jdbcTemplate.queryForObject(
                    "SELECT admin_type FROM \"user\" WHERE username = ?",
                    String.class,
                    username
            );
            return "Teacher".equals(adminType);
        } catch (EmptyResultDataAccessException ignored) {
            return false;
        }
    }
}
