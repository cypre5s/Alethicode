package com.alethicode.service.adminproblemcommand.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminProblemQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminProblemQueryServiceImpl implements AdminProblemQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminProblemQueryServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminProblemQueryServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ApiResponse<Object> getAdminProblems(Map<String, String> params, Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        String username = authentication.getName();
        PermissionContext permissionContext = getPermissionContext(username);
        if (!permissionContext.hasProblemPermission()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (permissionContext.disabled()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "你的账号已被禁用");
        }
        boolean canManageAllProblems = permissionContext.canManageAllProblems();
        String requestedId = trimToNull(params.get("id"));

        if (requestedId != null) {
            return getProblemDetail(requestedId, username, permissionContext);
        }

        String languagePackRaw = trimToNull(params.get("language_pack_id"));
        Long selectedLanguagePackId = parseLong(languagePackRaw);
        if (languagePackRaw != null && (selectedLanguagePackId == null || selectedLanguagePackId <= 0)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid language_pack_id");
        }
        if (!canManageAllProblems
                && permissionContext.teacher()
                && selectedLanguagePackId != null
                && !permissionContext.accessibleLanguagePackIds().contains(selectedLanguagePackId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        String keyword = trimToNull(params.get("keyword"));
        int limit = parseLimit(params.get("limit"));
        int offset = parseOffset(params.get("offset"));

        StringBuilder whereClause = new StringBuilder(" where 1 = 1 and not (p.is_ai_generated = true and p.visible = false) ");
        List<Object> args = new ArrayList<>();
        if (keyword != null) {
            whereClause.append(" and (p.title ilike ? or p._id ilike ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (!canManageAllProblems && !permissionContext.teacher()) {
            whereClause.append(" and u.username = ? ");
            args.add(username);
        }
        if (!canManageAllProblems && permissionContext.teacher()) {
            if (permissionContext.accessibleLanguagePackIds().isEmpty()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("results", List.of());
                payload.put("total", 0L);
                return ApiResponse.success(payload);
            }
            if (selectedLanguagePackId != null) {
                whereClause.append(
                        """
                         and exists (
                            select 1
                            from language_pack_problem_mapping lpm
                            where lpm.problem_id = p.id
                              and lpm.language_pack_id = ?
                         )
                        """
                );
                args.add(selectedLanguagePackId);
            } else {
                String placeholders = permissionContext.accessibleLanguagePackIds().stream()
                        .map(v -> "?")
                        .collect(Collectors.joining(", "));
                whereClause.append(
                        " and exists (select 1 from language_pack_problem_mapping lpm where lpm.problem_id = p.id and lpm.language_pack_id in ("
                                + placeholders + ")) "
                );
                args.addAll(permissionContext.accessibleLanguagePackIds());
            }
        } else if (selectedLanguagePackId != null) {
            whereClause.append(
                    """
                     and exists (
                        select 1
                        from language_pack_problem_mapping lpm
                        where lpm.problem_id = p.id
                          and lpm.language_pack_id = ?
                     )
                    """
            );
            args.add(selectedLanguagePackId);
        }

        long total = jdbcTemplate.queryForObject(
                "select count(*) from problem p left join \"user\" u on u.id = p.created_by_id " + whereClause,
                Long.class,
                args.toArray()
        );

        List<Long> ids = fetchPagedIds(whereClause.toString(), args, limit, offset);
        List<Map<String, Object>> problems = fetchProblemsByIds(ids);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("results", problems);
        payload.put("total", total);
        return ApiResponse.success(payload);
    }

    private ApiResponse<Object> getProblemDetail(String idParam, String username, PermissionContext permissionContext) {
        long problemId;
        try {
            problemId = Long.parseLong(idParam);
        } catch (NumberFormatException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }

        List<Map<String, Object>> problems = fetchProblemsByIds(List.of(problemId));
        if (problems.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }
        Map<String, Object> problem = problems.getFirst();
        if (!permissionContext.canManageAllProblems() && permissionContext.teacher()) {
            if (!problemInLanguagePackScope(problemId, permissionContext.accessibleLanguagePackIds())) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
            }
        } else if (!permissionContext.canManageAllProblems()) {
            Map<?, ?> createdBy = (Map<?, ?>) problem.get("created_by");
            String createdByUsername = String.valueOf(createdBy.get("username"));
            if (!username.equals(createdByUsername)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
            }
        }
        return ApiResponse.success(problem);
    }

    private boolean problemInLanguagePackScope(Long problemId, java.util.Set<Long> accessibleLanguagePackIds) {
        if (problemId == null || accessibleLanguagePackIds == null || accessibleLanguagePackIds.isEmpty()) {
            return false;
        }
        String placeholders = accessibleLanguagePackIds.stream().map(v -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>();
        args.add(problemId);
        args.addAll(accessibleLanguagePackIds);
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from language_pack_problem_mapping where problem_id = ? and language_pack_id in (" + placeholders + ")",
                Integer.class,
                args.toArray()
        );
        return count != null && count > 0;
    }

    private List<Long> fetchPagedIds(String whereClause, List<Object> args, int limit, int offset) {
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(limit);
        queryArgs.add(offset);
        String sql = "select p.id from problem p " +
                "left join \"user\" u on u.id = p.created_by_id " +
                whereClause +
                " order by p.create_time desc limit ? offset ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), queryArgs.toArray());
    }

    private List<Map<String, Object>> fetchProblemsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                select
                    p.*,
                    u.id as created_by_id,
                    u.username as created_by_username,
                    up.real_name as created_by_real_name,
                    src._id as ai_source_problem_display_id
                from problem p
                left join "user" u on u.id = p.created_by_id
                left join user_profile up on up.user_id = u.id
                left join problem src on src.id = p.ai_source_problem_id
                where p.id in (%s)
                """.formatted(placeholders);

        Map<Long, Map<String, Object>> mappedById = jdbcTemplate.query(
                sql,
                rs -> {
                    Map<Long, Map<String, Object>> rows = new HashMap<>();
                    while (rs.next()) {
                        Map<String, Object> item = mapAdminProblemRow(rs);
                        Map<String, Object> createdBy = new LinkedHashMap<>();
                        createdBy.put("id", nullableLong(rs, "created_by_id"));
                        createdBy.put("username", safeString(rs.getString("created_by_username")));
                        createdBy.put("real_name", rs.getString("created_by_real_name"));
                        item.put("created_by", createdBy);
                        item.put("ai_source_problem_display_id", rs.getString("ai_source_problem_display_id"));
                        rows.put(rs.getLong("id"), item);
                    }
                    return rows;
                },
                ids.toArray()
        );

        List<Map<String, Object>> ordered = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> row = mappedById.get(id);
            if (row != null) {
                ordered.add(row);
            }
        }
        attachTags(ordered);
        return ordered;
    }

    private Map<String, Object> mapAdminProblemRow(ResultSet rs) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getLong("id"));
        item.put("_id", rs.getString("_id"));
        item.put("is_public", rs.getBoolean("is_public"));
        item.put("title", rs.getString("title"));
        item.put("description", safeString(rs.getString("description")));
        item.put("input_description", safeString(rs.getString("input_description")));
        item.put("output_description", safeString(rs.getString("output_description")));
        item.put("samples", parseJsonValue(rs.getString("samples"), new TypeReference<List<Map<String, Object>>>() {
        }, List.of()));
        item.put("test_case_id", rs.getString("test_case_id"));
        item.put("test_case_score", parseJsonValue(rs.getString("test_case_score"), new TypeReference<List<Map<String, Object>>>() {
        }, List.of()));
        item.put("hint", rs.getString("hint"));
        item.put("languages", parseJsonValue(rs.getString("languages"), new TypeReference<List<String>>() {
        }, List.of()));
        item.put("template", parseJsonValue(rs.getString("template"), new TypeReference<Map<String, Object>>() {
        }, Map.of()));
        item.put("create_time", toInstant(rs.getTimestamp("create_time")));
        item.put("last_update_time", toInstant(rs.getTimestamp("last_update_time")));
        item.put("time_limit", rs.getInt("time_limit"));
        item.put("memory_limit", rs.getInt("memory_limit"));
        item.put("reference_solution_language", rs.getString("reference_solution_language"));
        item.put("reference_solution_code", rs.getString("reference_solution_code"));
        item.put("visible", rs.getBoolean("visible"));
        item.put("difficulty", rs.getString("difficulty"));
        item.put("source", rs.getString("source"));
        item.put("submission_number", rs.getLong("submission_number"));
        item.put("accepted_number", rs.getLong("accepted_number"));
        item.put("statistic_info", parseJsonValue(rs.getString("statistic_info"), new TypeReference<Map<String, Object>>() {
        }, Map.of()));
        item.put("is_ai_generated", rs.getBoolean("is_ai_generated"));
        item.put("ai_source_classroom", nullableLong(rs, "ai_source_classroom_id"));
        item.put("ai_source_problem", nullableLong(rs, "ai_source_problem_id"));
        item.put("ai_variant_index", nullableInteger(rs, "ai_variant_index"));
        item.put("visibility_status", rs.getString("visibility_status"));
        return item;
    }

    private void attachTags(List<Map<String, Object>> problems) {
        if (problems.isEmpty()) {
            return;
        }
        List<Long> ids = problems.stream().map(problem -> ((Number) problem.get("id")).longValue()).toList();
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                select pt.problem_id, t.name
                from problem_problem_tags pt
                join problem_tag t on t.id = pt.problemtag_id
                where pt.problem_id in (%s)
                order by t.id asc
                """.formatted(placeholders);

        Map<Long, List<String>> tagsByProblem = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long problemId = rs.getLong("problem_id");
            tagsByProblem.computeIfAbsent(problemId, unused -> new ArrayList<>()).add(rs.getString("name"));
        }, ids.toArray());

        for (Map<String, Object> problem : problems) {
            long problemId = ((Number) problem.get("id")).longValue();
            problem.put("tags", tagsByProblem.getOrDefault(problemId, List.of()));
        }
    }

    private PermissionContext getPermissionContext(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, admin_type, problem_permission, is_disabled from \"user\" where username = ?",
                    (rs, rowNum) -> {
                        long userId = rs.getLong("id");
                        String adminType = rs.getString("admin_type");
                        String problemPermission = rs.getString("problem_permission");
                        boolean disabled = rs.getBoolean("is_disabled");
                        boolean teacher = "Teacher".equals(adminType);
                        boolean adminRole = "Admin".equals(adminType) || teacher;
                        boolean adminManager = "Admin".equals(adminType);
                        boolean hasProblemPermission = teacher || (adminRole
                                && problemPermission != null
                                && !"None".equals(problemPermission));
                        boolean canManageAllProblems = adminManager || teacher || "All".equals(problemPermission);
                        java.util.Set<Long> accessibleLanguagePackIds = teacher ? loadTeacherLanguagePackIds(userId) : java.util.Set.of();
                        return new PermissionContext(hasProblemPermission, canManageAllProblems, teacher, accessibleLanguagePackIds, disabled);
                    },
                    username
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new PermissionContext(false, false, false, java.util.Set.of(), false);
        }
    }

    private java.util.Set<Long> loadTeacherLanguagePackIds(Long userId) {
        if (userId == null) {
            return java.util.Set.of();
        }
        return new java.util.LinkedHashSet<>(jdbcTemplate.queryForList(
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

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private int parseLimit(String raw) {
        int limit = 10;
        try {
            limit = Integer.parseInt(raw);
        } catch (Exception e) {
            log.debug("parseLimit: invalid raw={}, using default 10", raw, e);
            return 10;
        }
        if (limit < 0 || limit > 250) {
            return 10;
        }
        return limit;
    }

    private int parseOffset(String raw) {
        int offset = 0;
        try {
            offset = Integer.parseInt(raw);
        } catch (Exception e) {
            log.debug("parseOffset: invalid raw={}, using default 0", raw, e);
            return 0;
        }
        if (offset < 0) {
            return 0;
        }
        return offset;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long parseLong(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private <T> T parseJsonValue(String rawJson, TypeReference<T> typeReference, T fallback) {
        if (rawJson == null || rawJson.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(rawJson, typeReference);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private record PermissionContext(
            boolean hasProblemPermission,
            boolean canManageAllProblems,
            boolean teacher,
            java.util.Set<Long> accessibleLanguagePackIds,
            boolean disabled
    ) {
    }
}
