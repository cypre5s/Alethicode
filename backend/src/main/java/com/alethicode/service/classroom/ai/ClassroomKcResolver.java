package com.alethicode.service.classroom.ai;

import com.alethicode.exception.BusinessExceptions;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classroom 内的 KC 解析薄适配器。
 *
 * - 根据 classroom_language_pack（V30 起 classroom_id 唯一）解析班级唯一绑定的 language_pack_id
 * - 列出该 language pack 的 language_pack_kc 树供前端级联选择器
 * - 校验教师传入的 kc_ids 是否合法地属于该 language pack
 *
 * 不在本类内实现任何 KC 业务逻辑（mastery / 选题 / KC 关系图），仅提供查询/校验。
 */
@Service
public class ClassroomKcResolver {

    private final JdbcTemplate jdbcTemplate;

    public ClassroomKcResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long resolveLanguagePackId(String classroomId) {
        if (classroomId == null || classroomId.isBlank()) {
            throw BusinessExceptions.fromLegacy("error", "classroom_id is required");
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select language_pack_id from classroom_language_pack where classroom_id = ? limit 1",
                    Long.class,
                    classroomId
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw BusinessExceptions.fromLegacy("error", "班级未绑定语言包，无法使用 KC 出题/组卷能力");
        }
    }

    /**
     * 返回 chapter 分组的 KC 选项树，可直接喂级联选择器。
     */
    public List<Map<String, Object>> listKcOptionsTree(String classroomId) {
        Long languagePackId = resolveLanguagePackId(classroomId);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select kc.id          as kc_id,
                       kc.name        as kc_name,
                       kc.description as kc_description,
                       ch.id          as chapter_id,
                       ch.chapter_index as chapter_index,
                       ch.title       as chapter_title
                from language_pack_kc kc
                left join language_pack_chapter ch on ch.id = kc.chapter_id
                where kc.language_pack_id = ?
                order by coalesce(ch.chapter_index, 9999) asc, kc.id asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("kc_id", rs.getLong("kc_id"));
                    row.put("kc_name", rs.getString("kc_name"));
                    row.put("kc_description", rs.getString("kc_description"));
                    Object chapterIdRaw = rs.getObject("chapter_id");
                    row.put("chapter_id", chapterIdRaw);
                    row.put("chapter_index", rs.getObject("chapter_index"));
                    row.put("chapter_title", rs.getString("chapter_title"));
                    return row;
                },
                languagePackId
        );
        return groupByChapter(rows);
    }

    /**
     * 校验 rawIds 中每个 id 都属于 classroom 绑定的 LP，并按入参顺序去重返回。
     * 任何不属于该 LP 的 id 触发 failfast。
     */
    public List<Long> expandKcIds(String classroomId, List<?> rawIds) {
        Long languagePackId = resolveLanguagePackId(classroomId);
        List<Long> normalized = new ArrayList<>();
        if (rawIds != null) {
            for (Object raw : rawIds) {
                Long id = parseKcId(raw);
                if (id != null && !normalized.contains(id)) {
                    normalized.add(id);
                }
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(normalized.size(), "?"));
        Object[] args = new Object[normalized.size() + 1];
        for (int i = 0; i < normalized.size(); i++) {
            args[i] = normalized.get(i);
        }
        args[args.length - 1] = languagePackId;
        List<Long> validIds = jdbcTemplate.query(
                "select id from language_pack_kc where id in (" + placeholders + ") and language_pack_id = ?",
                (rs, rowNum) -> rs.getLong("id"),
                args
        );
        if (validIds.size() != normalized.size()) {
            throw BusinessExceptions.fromLegacy("error", "存在不属于该班级语言包的 KC，请重新选择");
        }
        return normalized;
    }

    public Map<Long, String> loadKcNameMap(Long languagePackId, List<Long> kcIds) {
        if (languagePackId == null || kcIds == null || kcIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(kcIds.size(), "?"));
        Object[] args = new Object[kcIds.size() + 1];
        for (int i = 0; i < kcIds.size(); i++) {
            args[i] = kcIds.get(i);
        }
        args[args.length - 1] = languagePackId;
        return jdbcTemplate.query(
                "select id, name from language_pack_kc where id in (" + placeholders + ") and language_pack_id = ?",
                rs -> {
                    Map<Long, String> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("id"), rs.getString("name"));
                    }
                    return result;
                },
                args
        );
    }

    private List<Map<String, Object>> groupByChapter(List<Map<String, Object>> rows) {
        Map<Object, Map<String, Object>> chapterMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object chapterId = row.get("chapter_id");
            Object chapterIndex = row.get("chapter_index");
            String chapterTitle = (String) row.get("chapter_title");
            Object key = chapterId == null ? "__no_chapter__" : chapterId;
            Map<String, Object> chapter = chapterMap.computeIfAbsent(key, k -> {
                Map<String, Object> grp = new LinkedHashMap<>();
                grp.put("chapter_id", chapterId);
                grp.put("chapter_index", chapterIndex);
                grp.put("chapter_title", chapterTitle == null || chapterTitle.isBlank() ? "未分组" : chapterTitle);
                grp.put("kcs", new ArrayList<Map<String, Object>>());
                return grp;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> kcs = (List<Map<String, Object>>) chapter.get("kcs");
            Map<String, Object> kc = new LinkedHashMap<>();
            kc.put("id", row.get("kc_id"));
            kc.put("name", row.get("kc_name"));
            kc.put("description", row.get("kc_description"));
            kcs.add(kc);
        }
        return new ArrayList<>(chapterMap.values());
    }

    private Long parseKcId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            throw BusinessExceptions.fromLegacy("error", "非法的 KC ID：" + text);
        }
    }
}
