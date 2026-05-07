package com.alethicode.service.aitutor.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminKcManagementService {

    private static final Logger log = LoggerFactory.getLogger(AdminKcManagementService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminKcManagementService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ApiResponse<Object> adminKcList(Map<String, String> params, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.admin()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String languagePackRaw = trimToNull(params.get("language_pack_id"));
        Long languagePackId = null;
        if (languagePackRaw != null) {
            languagePackId = parseLong(languagePackRaw);
            if (languagePackId == null || languagePackId <= 0) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid language_pack_id");
            }
        }
        String chapter = trimToNull(params.get("chapter"));
        String keyword = trimToNull(params.get("keyword"));
        int page = Math.max(parseInt(params.get("page"), 1), 1);
        int pageSize = parseInt(params.get("page_size"), 20);
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        int offset = (page - 1) * pageSize;

        StringBuilder whereBuilder = new StringBuilder(" where 1=1 ");
        List<Object> whereArgs = new ArrayList<>();
        if (languagePackId != null) {
            whereBuilder.append(" and language_pack_id = ? ");
            whereArgs.add(languagePackId);
        }
        if (chapter != null) {
            whereBuilder.append(" and chapter = ? ");
            whereArgs.add(chapter);
        }
        if (keyword != null) {
            whereBuilder.append(" and (name ilike ? or name_en ilike ?) ");
            String pattern = "%" + keyword + "%";
            whereArgs.add(pattern);
            whereArgs.add(pattern);
        }

        Long total = jdbcTemplate.queryForObject(
                "select count(*) from ai_knowledge_component " + whereBuilder,
                Long.class,
                whereArgs.toArray()
        );

        List<Object> listArgs = new ArrayList<>(whereArgs);
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess
                from ai_knowledge_component
                """
                        + whereBuilder +
                        """
                        order by chapter, name
                        limit ? offset ?
                        """,
                (rs, rowNum) -> kcRow(rs.getLong("id"), rs.getString("name"), rs.getString("name_en"), rs.getString("chapter"), rs.getString("description"),
                        rs.getDouble("p_init"), rs.getDouble("p_transit"), rs.getDouble("p_slip"), rs.getDouble("p_guess")),
                listArgs.toArray()
        );
        return ApiResponse.success(Map.of("results", rows, "total", total == null ? 0 : total));
    }

    public ApiResponse<Object> adminKcDetailUpdate(String kcId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.admin()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long id = parseLong(kcId);
        if (id == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "KC not found");
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from ai_knowledge_component where id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "KC not found");
        }

        int updated = jdbcTemplate.update(
                """
                update ai_knowledge_component
                set name = coalesce(?, name),
                    description = coalesce(?, description),
                    p_init = coalesce(?, p_init),
                    p_transit = coalesce(?, p_transit),
                    p_slip = coalesce(?, p_slip),
                    p_guess = coalesce(?, p_guess)
                where id = ?
                """,
                trimToNull(stringValue(request.get("name"))),
                trimToNull(stringValue(request.get("description"))),
                parseDoubleObj(request.get("p_init")),
                parseDoubleObj(request.get("p_transit")),
                parseDoubleObj(request.get("p_slip")),
                parseDoubleObj(request.get("p_guess")),
                id
        );
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "No valid fields to update");
        }
        Map<String, Object> kc = jdbcTemplate.queryForObject(
                "select id, name, name_en, description from ai_knowledge_component where id = ?",
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name"),
                        "name_en", rs.getString("name_en"),
                        "description", rs.getString("description")
                ),
                id
        );
        return ApiResponse.success(kc == null ? Map.of("id", id) : kc);
    }

    public ApiResponse<Object> adminKcProblems(String kcId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.admin()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long id = parseLong(kcId);
        if (id == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "KC not found");
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from ai_knowledge_component where id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "KC not found");
        }
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select p.id as problem_id, p._id as display_id, p.title, m.weight
                from ai_problem_kc_mapping m
                join problem p on p.id = m.problem_id
                where m.kc_id = ?
                order by p.id
                """,
                (rs, rowNum) -> Map.of(
                        "problem_id", rs.getLong("problem_id"),
                        "display_id", rs.getString("display_id"),
                        "title", rs.getString("title"),
                        "weight", rs.getDouble("weight")
                ),
                id
        );
        return ApiResponse.success(Map.of("results", items, "total", items.size()));
    }

    public ApiResponse<Object> adminClassroomChapters(Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.admin()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!tableExists("classroom")) {
            return ApiResponse.success(Map.of("results", List.of()));
        }
        String sql = """
                select c.id, c.name,
                       coalesce(c.current_chapter, 1) as current_chapter
                from classroom c
                """;
        List<Object> args = new ArrayList<>();
        if (user.teacher()) {
            sql += """
                    join classroom_member cm
                      on cm.classroom_id = c.id
                     and cm.user_id = ?
                     and cm.role in ('owner', 'ta')
                    """;
            args.add(user.userId());
        }
        sql += """
                where coalesce(c.is_active, true) = true
                order by c.id
                """;
        List<Map<String, Object>> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("classroom_id", String.valueOf(rs.getObject("id")));
                    row.put("classroom_name", rs.getString("name"));
                    row.put("teacher", "");
                    row.put("current_chapter", rs.getInt("current_chapter"));
                    row.put("student_count", 0);
                    row.put("avg_mastery", 0.0);
                    return row;
                },
                args.toArray()
        );
        return ApiResponse.success(Map.of("results", rows));
    }

    private Map<String, Object> kcRow(long id, String name, String nameEn, String chapter, String description,
                                      double pInit, double pTransit, double pSlip, double pGuess) {
        Integer problemCount = jdbcTemplate.queryForObject("select count(*) from ai_problem_kc_mapping where kc_id = ?", Integer.class, id);
        Double avgMastery = jdbcTemplate.queryForObject(
                """
                select avg(coalesce((s.extra_data->>'p_mastery')::double precision, 0))
                from ai_learning_event s
                where s.event_type = 'kc_mastery_snapshot' and (s.extra_data->>'kc_id')::bigint = ?
                """,
                Double.class,
                id
        );
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("name_en", nameEn);
        row.put("chapter", chapter);
        row.put("description", description);
        row.put("p_init", pInit);
        row.put("p_transit", pTransit);
        row.put("p_slip", pSlip);
        row.put("p_guess", pGuess);
        row.put("problem_count", problemCount == null ? 0 : problemCount);
        row.put("avg_mastery", avgMastery == null ? 0.0 : Math.round(avgMastery * 10000.0) / 10000.0);
        return row;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false, false, Set.of());
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        long userId = rs.getLong("id");
                        String adminType = rs.getString("admin_type");
                        boolean teacher = "Teacher".equals(adminType);
                        boolean admin = "Admin".equals(adminType) || teacher;
                        boolean adminManager = "Admin".equals(adminType);
                        Set<Long> accessibleLanguagePackIds = teacher ? loadTeacherLanguagePackIds(userId) : Set.of();
                        return new UserAuth(true, userId, admin, adminManager, teacher, accessibleLanguagePackIds);
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false, false, Set.of());
        }
    }

    private Set<Long> loadTeacherLanguagePackIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                select distinct clp.language_pack_id
                from classroom_member cm
                join classroom c on c.id = cm.classroom_id
                join classroom_language_pack clp on clp.classroom_id = cm.classroom_id
                where cm.user_id = ?
                  and c.is_active = true
                  and cm.role in ('owner', 'ta')
                """,
                Long.class,
                userId
        ));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseLong returned null: raw={}", raw, e);
            return null;
        }
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseInt using fallback: raw={}, fallback={}", raw, fallback, e);
            return fallback;
        }
    }

    private Double parseDoubleObj(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseDoubleObj returned null: value={}", value, e);
            return null;
        }
    }

    private record UserAuth(boolean authenticated,
                            Long userId,
                            boolean admin,
                            boolean adminManager,
                            boolean teacher,
                            Set<Long> accessibleLanguagePackIds) {
    }
}
