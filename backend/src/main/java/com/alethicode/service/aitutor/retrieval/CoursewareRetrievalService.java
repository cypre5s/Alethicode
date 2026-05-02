package com.alethicode.service.aitutor.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CoursewareRetrievalService {

    private final JdbcTemplate jdbcTemplate;

    public CoursewareRetrievalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> retrieve(Long problemId, List<Long> kcIds, String chapter, int limit, Long languagePackId) {
        if (languagePackId != null) {
            return retrieveFromLanguagePack(kcIds, limit, languagePackId);
        }
        LinkedHashMap<Long, Map<String, Object>> hits = new LinkedHashMap<>();
        if (problemId != null) {
            for (Map<String, Object> hit : queryByProblem(problemId)) {
                hits.putIfAbsent(((Number) hit.get("chunk_id")).longValue(), hit);
            }
        }
        for (Long kcId : kcIds == null ? List.<Long>of() : kcIds) {
            for (Map<String, Object> hit : queryByKc(kcId)) {
                hits.putIfAbsent(((Number) hit.get("chunk_id")).longValue(), hit);
            }
        }
        if (chapter != null && !chapter.isBlank()) {
            for (Map<String, Object> hit : queryByChapter(chapter)) {
                hits.putIfAbsent(((Number) hit.get("chunk_id")).longValue(), hit);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(hits.values());
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private List<Map<String, Object>> retrieveFromLanguagePack(List<Long> kcIds, int limit, Long languagePackId) {
        LinkedHashMap<Long, Map<String, Object>> hits = new LinkedHashMap<>();
        for (Long kcId : kcIds == null ? List.<Long>of() : kcIds) {
            for (Map<String, Object> hit : queryLanguagePackByKc(languagePackId, kcId)) {
                hits.putIfAbsent(((Number) hit.get("chunk_id")).longValue(), hit);
            }
        }
        if (hits.isEmpty()) {
            for (Map<String, Object> hit : queryLanguagePackRecentPages(languagePackId)) {
                hits.putIfAbsent(((Number) hit.get("chunk_id")).longValue(), hit);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(hits.values());
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private List<Map<String, Object>> queryByProblem(Long problemId) {
        return jdbcTemplate.query(
                """
                select id, title, content, chapter, kc_id
                from ai_courseware_chunk
                where problem_id = ?
                order by created_at desc
                limit 5
                """,
                (rs, rowNum) -> row(rs.getLong("id"), rs.getString("title"), rs.getString("content"), rs.getString("chapter"), rs.getObject("kc_id"), 1.0, "problem"),
                problemId
        );
    }

    private List<Map<String, Object>> queryByKc(Long kcId) {
        if (kcId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                select id, title, content, chapter, kc_id
                from ai_courseware_chunk
                where kc_id = ?
                order by created_at desc
                limit 5
                """,
                (rs, rowNum) -> row(rs.getLong("id"), rs.getString("title"), rs.getString("content"), rs.getString("chapter"), rs.getObject("kc_id"), 0.9, "kc"),
                kcId
        );
    }

    private List<Map<String, Object>> queryByChapter(String chapter) {
        return jdbcTemplate.query(
                """
                select id, title, content, chapter, kc_id
                from ai_courseware_chunk
                where chapter = ?
                order by created_at desc
                limit 5
                """,
                (rs, rowNum) -> row(rs.getLong("id"), rs.getString("title"), rs.getString("content"), rs.getString("chapter"), rs.getObject("kc_id"), 0.8, "chapter"),
                chapter
        );
    }

    private List<Map<String, Object>> queryLanguagePackByKc(Long languagePackId, Long kcId) {
        if (languagePackId == null || kcId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                select p.id as chunk_id,
                       p.document_id as document_id,
                       coalesce(p.page_title, '') as title,
                       coalesce(d.original_filename, '') as document_title,
                       p.page_text as content,
                       coalesce(cast(ch.chapter_index as text), '') as chapter,
                       p.page_no as slide_number,
                       m.kc_id
                from language_pack_kc_page_mapping m
                join language_pack_page p on p.id = m.page_id
                join language_pack_document d on d.id = p.document_id
                join language_pack_kc k on k.id = m.kc_id
                left join language_pack_chapter ch on ch.id = k.chapter_id
                where p.language_pack_id = ?
                  and m.kc_id = ?
                order by p.page_no asc, p.id asc
                limit 5
                """,
                (rs, rowNum) -> rowWithSlide(
                        rs.getLong("chunk_id"),
                        rs.getObject("document_id"),
                        rs.getString("title"),
                        rs.getString("document_title"),
                        rs.getString("content"),
                        rs.getString("chapter"),
                        rs.getObject("kc_id"),
                        rs.getObject("slide_number"),
                        0.95,
                        "language_pack_kc"
                ),
                languagePackId,
                kcId
        );
    }

    private List<Map<String, Object>> queryLanguagePackRecentPages(Long languagePackId) {
        if (languagePackId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                select p.id as chunk_id,
                       p.document_id as document_id,
                       coalesce(p.page_title, '') as title,
                       coalesce(d.original_filename, '') as document_title,
                       p.page_text as content,
                       coalesce(cast(ch.chapter_index as text), '') as chapter,
                       p.page_no as slide_number,
                       null as kc_id
                from language_pack_page p
                join language_pack_document d on d.id = p.document_id
                left join language_pack_chapter ch
                       on ch.language_pack_id = p.language_pack_id
                      and p.page_no >= ch.page_range_start
                      and p.page_no <= ch.page_range_end
                where p.language_pack_id = ?
                order by p.page_no asc, p.id asc
                limit 5
                """,
                (rs, rowNum) -> rowWithSlide(
                        rs.getLong("chunk_id"),
                        rs.getObject("document_id"),
                        rs.getString("title"),
                        rs.getString("document_title"),
                        rs.getString("content"),
                        rs.getString("chapter"),
                        rs.getObject("kc_id"),
                        rs.getObject("slide_number"),
                        0.6,
                        "language_pack_recent"
                ),
                languagePackId
        );
    }

    private Map<String, Object> row(Long id, String title, String content, String chapter, Object kcId, double score, String matchType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chunk_id", id);
        row.put("title", title == null ? "" : title);
        row.put("content", content == null ? "" : content);
        row.put("excerpt", summarize(content));
        row.put("preview", summarize(content));
        row.put("chapter", chapter == null ? "" : chapter);
        row.put("kc_id", kcId);
        row.put("score", score);
        row.put("match_type", matchType);
        return row;
    }

    private Map<String, Object> rowWithSlide(Long id, Object documentId, String title, String documentTitle,
                                             String content, String chapter, Object kcId, Object slideNumber,
                                             double score, String matchType) {
        Map<String, Object> row = row(id, title, content, chapter, kcId, score, matchType);
        if (documentId != null) {
            row.put("document_id", documentId);
        }
        row.put("document_title", documentTitle == null ? "" : documentTitle);
        row.put("slide_number", slideNumber);
        return row;
    }

    private String summarize(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120);
    }

    public List<Long> loadProblemKcIds(Long problemId, Long languagePackId) {
        if (problemId == null) {
            return List.of();
        }
        if (languagePackId != null) {
            return jdbcTemplate.query(
                    """
                    select kc_id
                    from ai_problem_kc_mapping
                    where problem_id = ? and language_pack_id = ?
                    order by weight desc, kc_id asc
                    """,
                    (rs, rowNum) -> rs.getLong("kc_id"),
                    problemId,
                    languagePackId
            );
        }
        return jdbcTemplate.query(
                "select kc_id from ai_problem_kc_mapping where problem_id = ? and language_pack_id is null order by weight desc, kc_id asc",
                (rs, rowNum) -> rs.getLong("kc_id"),
                problemId
        );
    }

    public String loadPrimaryChapter(Long problemId, Long languagePackId) {
        if (problemId == null) {
            return "";
        }
        if (languagePackId != null) {
            Set<String> chapters = new LinkedHashSet<>(jdbcTemplate.query(
                    """
                    select kc.chapter
                    from ai_problem_kc_mapping mapping
                    join ai_knowledge_component kc on kc.id = mapping.kc_id
                    where mapping.problem_id = ?
                      and mapping.language_pack_id = ?
                      and kc.chapter <> ''
                    order by mapping.weight desc, kc.id asc
                    limit 1
                    """,
                    (rs, rowNum) -> rs.getString("chapter"),
                    problemId,
                    languagePackId
            ));
            return chapters.stream().findFirst().orElse("");
        }
        Set<String> chapters = new LinkedHashSet<>(jdbcTemplate.query(
                """
                select kc.chapter
                from ai_problem_kc_mapping mapping
                join ai_knowledge_component kc on kc.id = mapping.kc_id
                where mapping.problem_id = ?
                  and mapping.language_pack_id is null
                  and kc.chapter <> ''
                order by mapping.weight desc, kc.id asc
                limit 1
                """,
                (rs, rowNum) -> rs.getString("chapter"),
                problemId
        ));
        return chapters.stream().findFirst().orElse("");
    }
}
