package com.alethicode.service.aitutor.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminVariantReviewService {

    private static final Logger log = LoggerFactory.getLogger(AdminVariantReviewService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    public AdminVariantReviewService(JdbcTemplate jdbcTemplate,
                                     ObjectMapper objectMapper,
                                     AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ApiResponse<Object> adminVariantReview(Map<String, String> params, Authentication authentication) {
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
        int page = parseInt(params.get("page"), 1);
        int limit = parseInt(params.get("limit"), 20);
        if (page < 1) {
            page = 1;
        }
        if (limit < 1) {
            limit = 20;
        }
        if (limit > 100) {
            limit = 100;
        }
        int offset = (page - 1) * limit;
        StringBuilder whereBuilder = new StringBuilder(" where p.is_ai_generated = true and p.visible = false ");
        List<Object> whereArgs = new ArrayList<>();
        if (languagePackId != null) {
            whereBuilder.append(
                    """
                     and exists (
                        select 1
                        from language_pack_problem_mapping lpm
                        where lpm.problem_id = p.id
                          and lpm.language_pack_id = ?
                     )
                    """
            );
            whereArgs.add(languagePackId);
        }
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from problem p " + whereBuilder,
                Long.class,
                whereArgs.toArray()
        );
        List<Object> listArgs = new ArrayList<>(whereArgs);
        listArgs.add(limit);
        listArgs.add(offset);
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select p.id, p._id, p.title, p.ai_source_problem_id, sp._id as source_problem_key,
                       sp.title as source_problem_title, p.test_case_score::text as test_case_score,
                       p.difficulty, p.create_time, u.username as created_by,
                       p.description, p.input_description, p.output_description, p.samples, p.hint
                from problem p
                left join problem sp on sp.id = p.ai_source_problem_id
                left join "user" u on u.id = p.created_by_id
                """
                        + whereBuilder +
                        """
                order by p.create_time desc
                limit ? offset ?
                """,
                (rs, rowNum) -> {
                    List<Object> tcs = parseJsonList(rs.getString("test_case_score"));
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("display_id", rs.getString("_id"));
                    item.put("title", rs.getString("title"));
                    item.put("source_problem_id", rs.getString("source_problem_key"));
                    item.put("source_problem_title", rs.getString("source_problem_title"));
                    item.put("test_case_count", tcs.size());
                    item.put("difficulty", rs.getString("difficulty"));
                    item.put("create_time", formatTime(rs.getTimestamp("create_time")));
                    item.put("created_by", rs.getString("created_by"));
                    item.put("description", trimToEmpty(rs.getString("description")));
                    item.put("input_description", trimToEmpty(rs.getString("input_description")));
                    item.put("output_description", trimToEmpty(rs.getString("output_description")));
                    item.put("samples", parseJsonList(rs.getString("samples")));
                    item.put("hint", trimToEmpty(rs.getString("hint")));
                    return item;
                },
                listArgs.toArray()
        );
        return ApiResponse.success(Map.of("total", total == null ? 0 : total, "page", page, "limit", limit, "results", results));
    }

    public ApiResponse<Object> adminVariantApprove(String problemId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.admin()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long pid = parseLong(problemId);
        if (pid == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
        }
        String requestedDisplayId = request == null ? "" : trimToEmpty(stringValue(request.get("display_id")));
        if (!requestedDisplayId.isBlank()) {
            if (!requestedDisplayId.matches("^[A-Za-z0-9._-]{1,32}$")) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid display_id");
            }
            Integer duplicate = jdbcTemplate.queryForObject(
                    "select count(*) from problem where _id = ? and id <> ?",
                    Integer.class,
                    requestedDisplayId,
                    pid
            );
            if (duplicate != null && duplicate > 0) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Display ID already exists");
            }
        }

        Map<String, Object> approved = jdbcTemplate.query(
                """
                update problem
                set visible = true,
                    _id = case when ? = '' then _id else ? end,
                    title = case when ? = '' then title else ? end,
                    last_update_time = now()
                where id = ?
                  and is_ai_generated = true
                  and visible = false
                returning id, _id, title
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("display_id", rs.getString("_id"));
                    item.put("title", rs.getString("title"));
                    return item;
                },
                requestedDisplayId,
                requestedDisplayId,
                requestedDisplayId,
                requestedDisplayId.isBlank() ? "" : normalizeTitleWithDisplayId(pid, requestedDisplayId),
                pid
        ).stream().findFirst().orElse(null);

        if (approved == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
        }
        return ApiResponse.success(approved);
    }

    public ApiResponse<Object> adminVariantReject(String problemId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.admin()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long pid = parseLong(problemId);
        if (pid == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
        }
        Map<String, Object> target = jdbcTemplate.query(
                """
                select id, test_case_id
                from problem
                where id = ?
                  and is_ai_generated = true
                  and visible = false
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("test_case_id", trimToNull(rs.getString("test_case_id")));
                    return row;
                },
                pid
        ).stream().findFirst().orElse(null);

        if (target == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
        }
        String testCaseId = (String) target.get("test_case_id");

        if (testCaseId != null) {
            String testCaseDir = properties.getSystem().getTestCaseDir();
            Path tcPath = Path.of(testCaseDir, testCaseId);
            try {
                if (Files.isDirectory(tcPath)) {
                    try (var walk = Files.walk(tcPath)) {
                        walk.sorted(java.util.Comparator.reverseOrder())
                                .forEach(p -> {
                                    try {
                                        Files.deleteIfExists(p);
                                    } catch (Exception e) {
                                        log.warn(
                                                "Failed to delete path while cleaning test case directory: problemId={}, testCaseId={}, path={}",
                                                pid,
                                                testCaseId,
                                                p,
                                                e);
                                    }
                                });
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to delete test case dir for problem {}: {}", pid, e.getMessage());
            }
        }

        jdbcTemplate.update(
                "delete from problem where id = ? and is_ai_generated = true and visible = false",
                pid
        );
        return ApiResponse.success("Rejected");
    }

    private String normalizeTitleWithDisplayId(Long problemId, String displayId) {
        String currentTitle = jdbcTemplate.query(
                "select title from problem where id = ?",
                (rs, rowNum) -> trimToEmpty(rs.getString("title")),
                problemId
        ).stream().findFirst().orElse("");
        String titleWithoutPrefix = currentTitle
                .replaceFirst("^(?:TMP-[A-Za-z0-9]+|\\d+(?:\\.\\d+)*)\\s+", "")
                .trim();
        if (titleWithoutPrefix.isBlank()) {
            return displayId;
        }
        return displayId + " " + titleWithoutPrefix;
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

    private List<Object> parseJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return new ArrayList<>();
        }
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private record UserAuth(boolean authenticated,
                            Long userId,
                            boolean admin,
                            boolean adminManager,
                            boolean teacher,
                            Set<Long> accessibleLanguagePackIds) {
    }
}
