package com.alethicode.service.problem.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.problem.ProblemQueryService;
import com.alethicode.service.aitutor.language.AiTutorProblemLanguageNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProblemQueryServiceImpl implements ProblemQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProblemQueryServiceImpl.class);

    private static final String CODING_TYPE = "coding";
    private static final Set<String> QUESTION_TYPES = Set.of("coding", "choice", "fill_blank");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiTutorProblemLanguageNormalizer aiTutorProblemLanguageNormalizer;

    public ProblemQueryServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiTutorProblemLanguageNormalizer = new AiTutorProblemLanguageNormalizer(objectMapper);
    }

    @Override
    public ApiResponse<Object> getProblems(Map<String, String> params, Authentication authentication) {
        String problemId = trimToNull(params.get("problem_id"));
        boolean withKcs = parseWithKcs(params.get("with_kcs"));
        if (problemId != null) {
            return getProblemDetail(problemId, withKcs, authentication);
        }

        if (!params.containsKey("limit")) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Limit is needed");
        }

        Long currentUserId = isAuthenticated(authentication) ? findUserIdByUsername(authentication.getName()) : null;
        boolean adminRole = isAuthenticated(authentication) && isAdminRole(authentication.getName());
        Long selectedPackId = tryParseLong(params.get("language_pack_id"));
        Set<Long> accessiblePackIds = currentUserId == null ? Set.of() : loadAccessibleLanguagePackIds(currentUserId);
        if (selectedPackId != null) {
            if (currentUserId == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
            }
            if (!adminRole && !accessiblePackIds.contains(selectedPackId)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
            }
        }

        QuerySpec querySpec = buildListQuerySpec(params, currentUserId, adminRole);
        int limit = parseLimit(params.get("limit"));
        int offset = parseOffset(params.get("offset"));
        String orderBy = resolveOrderBy(params.get("sort_by"));

        long total = jdbcTemplate.queryForObject(
                "select count(*) from problem p " + querySpec.whereClause(),
                Long.class,
                querySpec.args().toArray()
        );
        List<Long> pageIds = fetchPagedProblemIds(querySpec, orderBy, limit, offset);
        List<Map<String, Object>> problems = fetchProblemsByIds(pageIds);
        injectMyStatus(authentication, problems);
        if (withKcs) {
            injectKcNames(problems);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("results", problems);
        payload.put("total", total);
        return ApiResponse.success(payload);
    }

    @Override
    public ApiResponse<Object> getProblemTags(String keyword, String languagePackIdParam, Authentication authentication) {
        String normalizedKeyword = trimToNull(keyword);
        Long selectedPackId = tryParseLong(languagePackIdParam);
        Long currentUserId = null;
        boolean adminRole = false;
        if (isAuthenticated(authentication)) {
            currentUserId = findUserIdByUsername(authentication.getName());
            adminRole = isAdminRole(authentication.getName());
        }

        if (selectedPackId != null) {
            if (currentUserId == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
            }
            if (!adminRole && !loadAccessibleLanguagePackIds(currentUserId).contains(selectedPackId)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
            }
        }

        StringBuilder sql = new StringBuilder("""
                select t.id, t.name
                from problem_tag t
                where exists (
                    select 1
                    from problem_problem_tags pt
                    join problem p on p.id = pt.problem_id
                """);
        List<Object> args = new ArrayList<>();
        if (selectedPackId != null) {
            sql.append("""
                    join language_pack_problem_mapping lpm
                      on lpm.problem_id = p.id
                     and lpm.language_pack_id = ?
                    """);
            args.add(selectedPackId);
        }
        sql.append(" where pt.problemtag_id = t.id");
        if (!adminRole) {
            if (currentUserId == null) {
                sql.append(" and p.visible = true");
            } else {
                sql.append("""
                         and (
                            p.visible = true
                            or (
                                p.is_ai_generated = true
                                and p.visibility_status = 'student_private'
                                and p.created_by_id = ?
                            )
                         )
                        """);
                args.add(currentUserId);
            }
        }
        sql.append(")");
        if (selectedPackId != null) {
            sql.append("""
                     and (
                        t.name not like 'kc:%'
                        or exists (
                            select 1
                            from language_pack_kc k
                            where k.language_pack_id = ?
                              and t.name = ('kc:' || k.name)
                        )
                     )
                    """);
            args.add(selectedPackId);
        }
        if (normalizedKeyword != null) {
            sql.append(" and t.name ilike ?");
            args.add("%" + normalizedKeyword + "%");
        }
        sql.append(" order by t.id asc");

        List<Map<String, Object>> tags = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("name", rs.getString("name"));
            return item;
        }, args.toArray());
        return ApiResponse.success(tags);
    }

    @Override
    public ApiResponse<Object> getTagProgress(String userIdParam, String languagePackIdParam, Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        Long currentUserId = findUserIdByUsername(authentication.getName());
        if (currentUserId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        Long targetUserId;
        if (trimToNull(userIdParam) == null) {
            targetUserId = currentUserId;
        } else {
            try {
                targetUserId = Long.parseLong(userIdParam);
            } catch (NumberFormatException ignored) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid user_id");
            }
            if (!targetUserId.equals(currentUserId) && !isAdminRole(authentication.getName())) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
            }
        }

        Long selectedLanguagePackId = tryParseLong(languagePackIdParam);
        boolean adminRole = isAdminRole(authentication.getName());
        if (selectedLanguagePackId != null && !adminRole) {
            Set<Long> accessiblePackIds = loadAccessibleLanguagePackIds(currentUserId);
            if (!accessiblePackIds.contains(selectedLanguagePackId)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
            }
        }

        Set<Long> solvedProblemIds = loadSolvedProblemIds(targetUserId);
        List<Map<String, Object>> tags = new ArrayList<>(loadVisibleTagProgress(solvedProblemIds, selectedLanguagePackId));
        tags.sort(Comparator.<Map<String, Object>, Integer>comparing(item -> (Integer) item.get("solved"))
                .reversed()
                .thenComparing(item -> (Integer) item.get("total"), Comparator.reverseOrder()));

        return ApiResponse.success(Map.of("tags", tags, "user_id", targetUserId, "language_pack_id", selectedLanguagePackId));
    }

    @Override
    public ApiResponse<Object> pickOne() {
        String pickedProblemId = jdbcTemplate.query(
                "select _id from problem where visible = true order by random() limit 1",
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (pickedProblemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "No problem to pick");
        }
        return ApiResponse.success(pickedProblemId);
    }

    private ApiResponse<Object> getProblemDetail(String problemId, boolean withKcs, Authentication authentication) {
        Long currentUserId = null;
        boolean adminRole = false;
        if (isAuthenticated(authentication)) {
            currentUserId = findUserIdByUsername(authentication.getName());
            adminRole = isAdminRole(authentication.getName());
        }
        Long requestedProblemPk = tryParseLong(problemId);
        Long problemPk = querySingleProblemId(
                "select p.id from problem p where p._id = ? and p.visible = true order by p.id desc limit 1",
                problemId
        );
        if (problemPk == null && requestedProblemPk != null) {
            problemPk = querySingleProblemId(
                    "select p.id from problem p where p.id = ? and p.visible = true limit 1",
                    requestedProblemPk
            );
        }
        if (problemPk == null && currentUserId != null) {
            if (adminRole) {
                problemPk = querySingleProblemId(
                        "select p.id from problem p where p._id = ? and p.is_ai_generated = true order by p.id desc limit 1",
                        problemId
                );
                if (problemPk == null && requestedProblemPk != null) {
                    problemPk = querySingleProblemId(
                            "select p.id from problem p where p.id = ? and p.is_ai_generated = true limit 1",
                            requestedProblemPk
                    );
                }
            } else {
                problemPk = querySingleProblemId(
                        """
                        select p.id
                        from problem p
                        where p._id = ?
                          and p.is_ai_generated = true
                          and (p.visibility_status <> 'student_private' or p.created_by_id = ?)
                        order by p.id desc
                        limit 1
                        """,
                        problemId,
                        currentUserId
                );
                if (problemPk == null && requestedProblemPk != null) {
                    problemPk = querySingleProblemId(
                            """
                            select p.id
                            from problem p
                            where p.id = ?
                              and p.is_ai_generated = true
                              and (p.visibility_status <> 'student_private' or p.created_by_id = ?)
                            limit 1
                            """,
                            requestedProblemPk,
                            currentUserId
                    );
                }
            }
        }
        if (problemPk == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }

        List<Map<String, Object>> loaded = fetchProblemsByIds(List.of(problemPk));
        if (loaded.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }
        Map<String, Object> problem = loaded.getFirst();
        Long languagePackId = toLong(problem.get("language_pack_id"));
        if (languagePackId != null) {
            if (currentUserId == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
            }
            if (!adminRole) {
                Set<Long> accessiblePacks = loadAccessibleLanguagePackIds(currentUserId);
                if (!accessiblePacks.contains(languagePackId)) {
                    throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
                }
            }
        }

        List<Map<String, Object>> wrapped = new ArrayList<>();
        wrapped.add(problem);
        injectMyStatus(authentication, wrapped);
        if (withKcs) {
            injectKcNames(wrapped);
        }
        return ApiResponse.success(problem);
    }

    private Long querySingleProblemId(String sql, Object... args) {
        List<Long> ids = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("id"),
                args
        );
        if (ids.isEmpty()) {
            return null;
        }
        return ids.getFirst();
    }

    private QuerySpec buildListQuerySpec(Map<String, String> params, Long currentUserId, boolean adminRole) {
        List<String> predicates = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        String classroomSearch = trimToNull(params.get("classroom_search"));
        if (classroomSearch == null) {
            if (currentUserId == null) {
                predicates.add("p.visible = true");
            } else {
                predicates.add("""
                        (
                            p.visible = true
                            or (
                                p.is_ai_generated = true
                                and p.visibility_status = 'student_private'
                                and p.created_by_id = ?
                            )
                        )
                        """.trim());
                args.add(currentUserId);
            }
        }

        String tag = trimToNull(params.get("tag"));
        if (tag != null) {
            predicates.add("""
                    exists (
                        select 1
                        from problem_problem_tags pt
                        join problem_tag t on t.id = pt.problemtag_id
                        where pt.problem_id = p.id and t.name = ?
                    )
                    """.trim());
            args.add(tag);
        }

        String keyword = trimToNull(params.get("keyword"));
        if (keyword != null) {
            predicates.add("(p.title ilike ? or p._id ilike ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }

        String difficulty = trimToNull(params.get("difficulty"));
        if (difficulty != null) {
            predicates.add("p.difficulty = ?");
            args.add(difficulty);
        }

        String chapter = trimToNull(params.get("chapter"));
        if (chapter != null) {
            predicates.add("p._id like ?");
            args.add("PPT" + chapter + "-%");
        }

        Long selectedLanguagePackId = tryParseLong(params.get("language_pack_id"));
        if (selectedLanguagePackId != null) {
            predicates.add("""
                    exists (
                        select 1
                        from language_pack_problem_mapping lpm
                        where lpm.problem_id = p.id and lpm.language_pack_id = ?
                    )
                    """.trim());
            args.add(selectedLanguagePackId);
        } else if (!adminRole) {
            predicates.add("""
                    not exists (
                        select 1
                        from language_pack_problem_mapping lpm
                        where lpm.problem_id = p.id
                    )
                    """.trim());
        }

        String questionType = trimToNull(params.get("question_type"));
        if (questionType != null) {
            String normalized = questionType.toLowerCase(Locale.ROOT);
            if (!QUESTION_TYPES.contains(normalized)) {
                predicates.add("1 = 0");
            } else if (CODING_TYPE.equals(normalized)) {
                predicates.add("""
                        (
                            exists (
                                select 1
                                from problem_problem_tags qpt
                                join problem_tag qt on qt.id = qpt.problemtag_id
                                where qpt.problem_id = p.id and qt.name = 'type:coding'
                            )
                            or coalesce(p.statistic_info #>> '{objective_question,question_type}', '')
                               not in ('choice', 'fill_blank')
                        )
                        """.trim());
            } else {
                predicates.add("""
                        (
                            exists (
                                select 1
                                from problem_problem_tags qpt
                                join problem_tag qt on qt.id = qpt.problemtag_id
                                where qpt.problem_id = p.id and qt.name = ?
                            )
                            or p.statistic_info #>> '{objective_question,question_type}' = ?
                        )
                        """.trim());
                args.add("type:" + normalized);
                args.add(normalized);
            }
        }

        String whereClause = predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates);
        return new QuerySpec(whereClause, args);
    }

    private List<Long> fetchPagedProblemIds(QuerySpec querySpec, String orderBy, int limit, int offset) {
        String sql = "select p.id from problem p " + querySpec.whereClause() +
                " order by " + orderBy + " limit ? offset ?";
        List<Object> args = new ArrayList<>(querySpec.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), args.toArray());
    }

    private List<Map<String, Object>> fetchProblemsByIds(List<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return List.of();
        }

        String placeholders = problemIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                select
                    p.*,
                    u.id as created_by_id,
                    u.username as created_by_username,
                    up.real_name as created_by_real_name,
                    src._id as ai_source_problem_display_id,
                    lpm.language_pack_id,
                    lp.primary_language as language_pack_primary_language
                from problem p
                left join "user" u on u.id = p.created_by_id
                left join user_profile up on up.user_id = u.id
                left join problem src on src.id = p.ai_source_problem_id
                left join language_pack_problem_mapping lpm on lpm.problem_id = p.id
                left join language_pack lp on lp.id = lpm.language_pack_id
                where p.id in (%s)
                """.formatted(placeholders);

        Map<Long, Map<String, Object>> byId = jdbcTemplate.query(
                sql,
                rs -> {
                    Map<Long, Map<String, Object>> mapped = new HashMap<>();
                    while (rs.next()) {
                        Map<String, Object> item = mapProblemRow(rs);
                        Map<String, Object> createdBy = new LinkedHashMap<>();
                        createdBy.put("id", nullableLong(rs, "created_by_id"));
                        createdBy.put("username", safeString(rs.getString("created_by_username")));
                        createdBy.put("real_name", rs.getString("created_by_real_name"));
                        item.put("created_by", createdBy);
                        item.put("ai_source_problem_display_id", rs.getString("ai_source_problem_display_id"));
                        mapped.put(rs.getLong("id"), item);
                    }
                    return mapped;
                },
                problemIds.toArray()
        );

        List<Map<String, Object>> ordered = new ArrayList<>();
        for (Long id : problemIds) {
            Map<String, Object> item = byId.get(id);
            if (item != null) {
                ordered.add(item);
            }
        }
        attachTags(ordered);
        return ordered;
    }

    private Map<String, Object> mapProblemRow(ResultSet rs) throws SQLException {
        AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalizedLanguage =
                aiTutorProblemLanguageNormalizer.normalize(
                        rs.getString("visibility_status"),
                        rs.getString("statistic_info"),
                        rs.getString("languages"),
                        rs.getString("template")
                );
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getLong("id"));
        item.put("_id", rs.getString("_id"));
        item.put("title", rs.getString("title"));
        item.put("description", safeString(rs.getString("description")));
        item.put("input_description", safeString(rs.getString("input_description")));
        item.put("output_description", safeString(rs.getString("output_description")));
        item.put("samples", parseJsonValue(rs.getString("samples"), new TypeReference<List<Map<String, Object>>>() {
        }, List.of()));
        item.put("hint", rs.getString("hint"));
        item.put("languages", normalizedLanguage.languages());
        item.put("template", normalizedLanguage.publicTemplates());
        item.put("create_time", toInstant(rs.getTimestamp("create_time")));
        item.put("last_update_time", toInstant(rs.getTimestamp("last_update_time")));
        item.put("time_limit", rs.getInt("time_limit"));
        item.put("memory_limit", rs.getInt("memory_limit"));
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
        item.put("language_pack_id", nullableLong(rs, "language_pack_id"));
        item.put("language_pack_primary_language", rs.getString("language_pack_primary_language"));
        String visibilityStatus = rs.getString("visibility_status");
        item.put("visibility_status", visibilityStatus);
        item.put("ai_tutor_enabled", normalizedLanguage.aiTutorEnabled());
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
            tagsByProblem.computeIfAbsent(problemId, unused -> new ArrayList<>())
                    .add(rs.getString("name"));
        }, ids.toArray());

        for (Map<String, Object> problem : problems) {
            long problemId = ((Number) problem.get("id")).longValue();
            problem.put("tags", tagsByProblem.getOrDefault(problemId, List.of()));
        }
    }

    private void injectMyStatus(Authentication authentication, List<Map<String, Object>> problems) {
        if (!isAuthenticated(authentication)) {
            return;
        }
        Long userId = findUserIdByUsername(authentication.getName());
        if (userId == null) {
            return;
        }
        Map<String, Object> acmStatus = loadAcmProblemStatus(userId);
        Object problemStatus = acmStatus.get("problems");
        if (!(problemStatus instanceof Map<?, ?> statusMap)) {
            for (Map<String, Object> problem : problems) {
                problem.put("my_status", null);
            }
            return;
        }

        for (Map<String, Object> problem : problems) {
            String pid = String.valueOf(problem.get("id"));
            Object statusEntry = statusMap.get(pid);
            Integer status = null;
            if (statusEntry instanceof Map<?, ?> statusItem) {
                Object value = statusItem.get("status");
                status = asInteger(value);
            }
            problem.put("my_status", status);
        }
    }

    private void injectKcNames(List<Map<String, Object>> problems) {
        if (problems.isEmpty()) {
            return;
        }
        List<Long> ids = problems.stream().map(problem -> ((Number) problem.get("id")).longValue()).toList();
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
                select m.problem_id, kc.id as kc_id, kc.name
                from ai_problem_kc_mapping m
                join ai_knowledge_component kc on kc.id = m.kc_id
                where m.problem_id in (%s)
                order by m.problem_id asc, kc.id asc
                """.formatted(placeholders);
        Map<Long, List<Map<String, Object>>> kcByProblem = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long problemId = rs.getLong("problem_id");
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("kc_id", rs.getLong("kc_id"));
            one.put("name", rs.getString("name"));
            one.put("mastery", null);
            kcByProblem.computeIfAbsent(problemId, unused -> new ArrayList<>()).add(one);
        }, ids.toArray());

        for (Map<String, Object> problem : problems) {
            long problemId = ((Number) problem.get("id")).longValue();
            problem.put("kc_names", kcByProblem.getOrDefault(problemId, List.of()));
        }
    }

    private Map<String, Object> loadAcmProblemStatus(Long userId) {
        try {
            String rawJson = jdbcTemplate.queryForObject(
                    "select acm_problems_status::text from user_profile where user_id = ?",
                    String.class,
                    userId
            );
            return parseJsonValue(rawJson, new TypeReference<Map<String, Object>>() {
            }, Map.of());
        } catch (EmptyResultDataAccessException ignored) {
            return Map.of();
        }
    }

    private Set<Long> loadSolvedProblemIds(Long userId) {
        Map<String, Object> acmStatus = loadAcmProblemStatus(userId);
        Object problems = acmStatus.get("problems");
        if (!(problems instanceof Map<?, ?> statusMap)) {
            return Set.of();
        }
        Set<Long> solved = new HashSet<>();
        for (Map.Entry<?, ?> entry : statusMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> statusItem)) {
                continue;
            }
            Integer status = asInteger(statusItem.get("status"));
            if (status != null && status == 0) {
                try {
                    solved.add(Long.parseLong(String.valueOf(entry.getKey())));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed problem id in legacy status json.
                }
            }
        }
        return solved;
    }

    private List<Map<String, Object>> loadVisibleTagProgress(Set<Long> solvedProblemIds, Long selectedLanguagePackId) {
        StringBuilder sql = new StringBuilder("""
                select
                    t.name,
                    array_agg(distinct p.id) as problem_ids
                from problem_tag t
                join problem_problem_tags pt on pt.problemtag_id = t.id
                join problem p on p.id = pt.problem_id
                """);
        List<Object> args = new ArrayList<>();
        if (selectedLanguagePackId != null) {
            sql.append("""
                    join language_pack_problem_mapping lpm
                      on lpm.problem_id = p.id
                     and lpm.language_pack_id = ?
                    """);
            args.add(selectedLanguagePackId);
        }
        sql.append(" where 1 = 1");
        if (selectedLanguagePackId != null) {
            sql.append("""
                     and (
                        t.name not like 'kc:%'
                        or exists (
                            select 1
                            from language_pack_kc k
                            where k.language_pack_id = ?
                              and t.name = ('kc:' || k.name)
                        )
                     )
                    """);
            args.add(selectedLanguagePackId);
        }
        sql.append("""
                group by t.id, t.name
                having count(distinct p.id) > 0
                """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Set<Long> problemIds = parseLongSet(rs.getArray("problem_ids"));
            int solvedCount = (int) problemIds.stream().filter(solvedProblemIds::contains).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", rs.getString("name"));
            item.put("total", problemIds.size());
            item.put("solved", solvedCount);
            return item;
        }, args.toArray());
    }

    private Set<Long> loadAccessibleLanguagePackIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new HashSet<>(jdbcTemplate.queryForList(
                """
                SELECT DISTINCT clp.language_pack_id
                FROM classroom_member cm
                JOIN classroom_language_pack clp ON clp.classroom_id = cm.classroom_id
                JOIN classroom c ON c.id = cm.classroom_id
                WHERE cm.user_id = ?
                  AND c.is_active = true
                """,
                Long.class,
                userId
        ));
    }

    private Long findUserIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id from \"user\" where username = ?",
                    Long.class,
                    username
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private boolean isAdminRole(String username) {
        try {
            String adminType = jdbcTemplate.queryForObject(
                    "select admin_type from \"user\" where username = ?",
                    String.class,
                    username
            );
            return "Admin".equals(adminType) || "Teacher".equals(adminType);
        } catch (EmptyResultDataAccessException ignored) {
            return false;
        }
    }

    private String resolveOrderBy(String sortBy) {
        if ("oldest".equals(sortBy)) {
            return "p.create_time asc";
        }
        if ("ac_rate".equals(sortBy)) {
            return """
                    case when p.submission_number > 0
                        then (p.accepted_number::double precision / p.submission_number)
                        else 0 end desc,
                    p.submission_number desc,
                    p.create_time desc
                    """.trim();
        }
        return "p.create_time desc";
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

    private boolean parseWithKcs(String raw) {
        if (raw == null) {
            return false;
        }
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long tryParseLong(String value) {
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

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
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

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Set<Long> parseLongSet(Array sqlArray) {
        if (sqlArray == null) {
            return Set.of();
        }
        try {
            Object value = sqlArray.getArray();
            if (!(value instanceof Object[] values)) {
                return Set.of();
            }
            Set<Long> parsed = new HashSet<>();
            for (Object item : values) {
                if (item == null) {
                    continue;
                }
                if (item instanceof Number number) {
                    parsed.add(number.longValue());
                    continue;
                }
                try {
                    parsed.add(Long.parseLong(String.valueOf(item)));
                } catch (NumberFormatException ignored) {
                    // ignore malformed values
                }
            }
            return parsed;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to parse SQL array", exception);
        }
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

    private record QuerySpec(String whereClause, List<Object> args) {
    }
}
