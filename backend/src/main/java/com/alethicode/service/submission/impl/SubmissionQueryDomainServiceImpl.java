package com.alethicode.service.submission.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.submission.SubmissionQueryDomainService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class SubmissionQueryDomainServiceImpl implements SubmissionQueryDomainService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    public SubmissionQueryDomainServiceImpl(JdbcTemplate jdbcTemplate,
                                           ObjectMapper objectMapper,
                                           AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ApiResponse<Object> getSubmission(String submissionId, Authentication authentication) {
        AuthUser user = resolveAuthUser(authentication);
        requireLogin(user);

        String normalizedId = trimToNull(submissionId);
        if (normalizedId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parameter id doesn't exist");
        }

        SubmissionRow row = findSubmissionById(normalizedId);
        if (row == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Submission doesn't exist");
        }
        Map<String, Object> statisticInfo = parseJsonMap(row.statisticInfoJson());
        if (hasReviewMarker(statisticInfo)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Submission doesn't exist");
        }

        if (!canViewSubmission(row, user)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "No permission for this submission");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.id());
        payload.put("create_time", toIso(row.createTime()));
        payload.put("user_id", row.userId());
        payload.put("username", row.username());
        payload.put("code", row.code());
        payload.put("result", row.result());
        payload.put("language", row.language());
        payload.put("statistic_info", statisticInfo);
        // SEC CRIT-2 (2026-05-02 渗透报告): submission.info 字段包含 per-test-case 的
        // {exit_code, signal, cpu_time, memory, output_md5, real_time}，攻击者可用
        // os._exit(N) 把 N 编码到 exit_code，配合多次提交侧信道泄露 /test_case 答案。
        // 仅对 admin/teacher 透出原始 info 与提交 IP；学生只看到 statistic_info 概要。
        if (user.isAdminRole()) {
            payload.put("problem", row.problemId());
            payload.put("info", parseJsonMap(row.infoJson()));
            payload.put("ip", row.ip());
        } else {
            payload.put("problem", row.problemDisplayId());
        }
        return ApiResponse.success(payload);
    }

    @Override
    public ApiResponse<Object> listSubmissions(String problemId, String myself, String result, String username,
                                               String limit, String offset, Authentication authentication) {
        if (trimToNull(limit) == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Limit is needed");
        }

        AuthUser user = resolveAuthUser(authentication);
        Integer limitValue = parseInt(limit, 10);
        Integer offsetValue = parseInt(offset, 0);
        if (limitValue == null || limitValue <= 0) limitValue = 10;
        if (offsetValue == null || offsetValue < 0) offsetValue = 0;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1=1 ");
        where.append(" and coalesce(jsonb_exists(s.statistic_info, 'needs_human_review'), false) = false ");
        where.append(" and coalesce(jsonb_exists(s.statistic_info, 'human_review'), false) = false ");

        String normalizedProblemId = trimToNull(problemId);
        if (normalizedProblemId != null) {
            Long targetProblemId = findVisibleOrAiProblemByDisplayId(normalizedProblemId, user);
            if (targetProblemId == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem doesn't exist");
            }
            where.append(" and s.problem_id = ? ");
            params.add(targetProblemId);
        }

        // SEC HIGH-1 (2026-05-02 渗透报告): 非 admin/teacher 必须只能看自己的提交，
        // 无论站点 submission_list_show_all 配置或 myself 参数如何。允许 admin/teacher
        // 默认查看全平台、用 username 过滤、或主动用 myself=1 仅看自己。
        boolean isAdminViewer = user != null && user.isAdminRole();
        boolean wantsMyself = "1".equals(trimToNull(myself));
        if (!isAdminViewer || wantsMyself) {
            long currentUserId = user == null ? -1L : user.id();
            where.append(" and s.user_id = ? ");
            params.add(currentUserId);
        } else {
            String normalizedUsername = trimToNull(username);
            if (normalizedUsername != null) {
                where.append(" and s.username ilike ? ");
                params.add("%" + normalizedUsername + "%");
            }
        }

        Integer resultValue = parseInt(result, null);
        if (resultValue != null) {
            where.append(" and s.result = ? ");
            params.add(resultValue);
        }

        String countSql = "select count(*) from submission s" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(limitValue);
        queryParams.add(offsetValue);
        String querySql = """
                select s.id, s.problem_id, s.create_time, s.user_id, s.username, s.result,
                       s.language, s.shared, s.statistic_info::text as statistic_info_json,
                       p._id as problem_display_id, p.created_by_id as problem_created_by_id
                from submission s
                left join problem p on p.id = s.problem_id
                """ + where + " order by s.create_time desc limit ? offset ?";

        List<Map<String, Object>> rows = jdbcTemplate.query(querySql, (rs, rowNum) -> {
            SubmissionRow one = new SubmissionRow(
                    rs.getString("id"),
                    rs.getLong("problem_id"),
                    rs.getTimestamp("create_time"),
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    "",
                    rs.getInt("result"),
                    "{}",
                    rs.getString("language"),
                    rs.getBoolean("shared"),
                    rs.getString("statistic_info_json"),
                    null,
                    rs.getString("problem_display_id"),
                    rs.getObject("problem_created_by_id") == null ? null : rs.getLong("problem_created_by_id")
            );

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", one.id());
            item.put("problem", one.problemDisplayId());
            item.put("create_time", toIso(one.createTime()));
            item.put("user_id", one.userId());
            item.put("username", one.username());
            item.put("result", one.result());
            item.put("language", one.language());
            item.put("statistic_info", parseJsonMap(one.statisticInfoJson()));
            item.put("show_link", user != null && canViewSubmission(one, user));
            return item;
        }, queryParams.toArray());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("results", rows);
        payload.put("total", total == null ? 0 : total);
        return ApiResponse.success(payload);
    }

    @Override
    public ApiResponse<Object> recentWrong(String userIdParam, String limitParam, Authentication authentication) {
        AuthUser user = resolveAuthUser(authentication);
        requireLogin(user);

        String normalizedUserId = trimToNull(userIdParam);
        long targetUserId = user.id();
        if (normalizedUserId != null) {
            Long parsed = parseLong(normalizedUserId, null);
            if (parsed == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid user_id");
            }
            targetUserId = parsed;
        }

        if (targetUserId != user.id() && !user.isAdminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        Integer limitVal = parseInt(limitParam, 5);
        if (limitVal == null) limitVal = 5;
        if (limitVal > 20) limitVal = 20;

        Map<String, Object> profile = findUserProfile(targetUserId);
        Map<String, Object> problemsStatus = Map.of();
        if (profile != null) {
            Object statusObj = profile.get("problems");
            if (statusObj instanceof Map<?, ?> map) {
                problemsStatus = castToStringObjectMap(map);
            }
        }

        List<Long> solvedProblemIds = new ArrayList<>();
        for (Map.Entry<String, Object> entry : problemsStatus.entrySet()) {
            Long pid = parseLong(entry.getKey(), null);
            if (pid == null) continue;
            if (entry.getValue() instanceof Map<?, ?> one) {
                Object status = one.get("status");
                if (Objects.equals(parseInt(status == null ? null : String.valueOf(status), null), 0)) {
                    solvedProblemIds.add(pid);
                }
            }
        }

        Map<Integer, String> labels = Map.of(
                -2, "CE", -1, "WA", 1, "TLE", 2, "TLE", 3, "MLE", 4, "RE", 5, "SE"
        );

        List<Map<String, Object>> wrongSubs = jdbcTemplate.query("""
                select s.id, s.problem_id, p._id, p.title, s.result, s.create_time
                from submission s
                join problem p on p.id = s.problem_id
                where s.user_id = ? and s.result in (-2, -1, 1, 2, 3, 4, 5)
                order by s.create_time desc
                limit 50
                """, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("submission_id", rs.getString("id"));
            item.put("problem_id", rs.getLong("problem_id"));
            item.put("problem_key", rs.getString("_id"));
            item.put("title", rs.getString("title"));
            item.put("result", rs.getInt("result"));
            item.put("create_time", toIso(rs.getTimestamp("create_time")));
            return item;
        }, targetUserId);

        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        for (Map<String, Object> sub : wrongSubs) {
            Long pid = ((Number) sub.get("problem_id")).longValue();
            if (seen.contains(pid) || solvedProblemIds.contains(pid)) continue;
            seen.add(pid);
            Integer resultCode = ((Number) sub.get("result")).intValue();
            Map<String, Object> item = new LinkedHashMap<>(sub);
            item.put("result_label", labels.getOrDefault(resultCode, "ERR"));
            items.add(item);
            if (items.size() >= limitVal) break;
        }

        return ApiResponse.success(Map.of("items", items, "user_id", targetUserId));
    }

    @Override
    public ApiResponse<Object> submissionExists(String problemIdParam, Authentication authentication) {
        String normalized = trimToNull(problemIdParam);
        if (normalized == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parameter error, problem_id is required");
        }
        AuthUser user = resolveAuthUser(authentication);
        if (user == null) return ApiResponse.success(false);

        Long problemId = parseLong(normalized, null);
        if (problemId == null) return ApiResponse.success(false);

        Long count = jdbcTemplate.queryForObject(
                "select count(*) from submission where problem_id = ? and user_id = ?",
                Long.class, problemId, user.id());
        return ApiResponse.success(count != null && count > 0);
    }

    @Override
    public ApiResponse<Object> problemStatistics(String problemIdParam, String language) {
        String normalizedProblemId = trimToNull(problemIdParam);
        if (normalizedProblemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem ID is required");
        }

        ProblemBaseInfo problem = findProblemByParam(normalizedProblemId);
        if (problem == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }

        String normalizedLanguage = trimToNull(language);
        String sql;
        List<Object> args = new ArrayList<>();
        args.add(problem.id());
        if (normalizedLanguage != null) {
            sql = "select statistic_info::text from submission where problem_id = ? and result = 0 and language = ? order by create_time desc limit 1000";
            args.add(normalizedLanguage);
        } else {
            sql = "select statistic_info::text from submission where problem_id = ? and result = 0 order by create_time desc limit 1000";
        }
        List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), args.toArray());

        long totalTime = 0;
        long totalMemory = 0;
        int count = 0;
        for (String raw : rows) {
            Map<String, Object> info = parseJsonMap(raw);
            Object timeObj = info.get("time_cost");
            Object memoryObj = info.get("memory_cost");
            if (timeObj instanceof Number t && memoryObj instanceof Number m) {
                totalTime += t.longValue();
                totalMemory += m.longValue();
                count++;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("problem_id", problem.displayId());
        payload.put("title", problem.title());
        payload.put("ac_count", count);
        payload.put("avg_time_cost", count > 0 ? totalTime / count : 0);
        payload.put("avg_memory_cost", count > 0 ? totalMemory / count : 0);
        return ApiResponse.success(payload);
    }

    private SubmissionRow findSubmissionById(String submissionId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select s.id, s.problem_id, s.create_time, s.user_id, s.username, s.code, s.result,
                           s.info::text as info_json, s.language, s.shared,
                           s.statistic_info::text as statistic_info_json, s.ip,
                           p._id as problem_display_id, p.created_by_id as problem_created_by_id
                    from submission s
                    left join problem p on p.id = s.problem_id
                    where s.id = ?
                    """,
                    (rs, rowNum) -> new SubmissionRow(
                            rs.getString("id"),
                            rs.getLong("problem_id"),
                            rs.getTimestamp("create_time"),
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getString("code"),
                            rs.getInt("result"),
                            rs.getString("info_json"),
                            rs.getString("language"),
                            rs.getBoolean("shared"),
                            rs.getString("statistic_info_json"),
                            rs.getString("ip"),
                            rs.getString("problem_display_id"),
                            rs.getObject("problem_created_by_id") == null ? null : rs.getLong("problem_created_by_id")
                    ),
                    submissionId);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Long findVisibleOrAiProblemByDisplayId(String displayId, AuthUser user) {
        Long visible = jdbcTemplate.query(
                "select id from problem where _id = ? and visible = true order by id desc limit 1",
                (rs, rowNum) -> rs.getLong(1), displayId
        ).stream().findFirst().orElse(null);
        if (visible != null) return visible;
        if (user != null && user.isAdminRole()) {
            return jdbcTemplate.query(
                    "select id from problem where _id = ? and is_ai_generated = true order by id desc limit 1",
                    (rs, rowNum) -> rs.getLong(1), displayId
            ).stream().findFirst().orElse(null);
        }
        return jdbcTemplate.query(
                """
                select id from problem
                where _id = ? and is_ai_generated = true
                  and (visibility_status <> 'student_private' or created_by_id = ?)
                order by id desc limit 1
                """,
                (rs, rowNum) -> rs.getLong(1),
                displayId, user == null ? -1L : user.id()
        ).stream().findFirst().orElse(null);
    }

    private ProblemBaseInfo findProblemByParam(String problemIdParam) {
        Long numericId = parseLong(problemIdParam, null);
        if (numericId != null) {
            return jdbcTemplate.query(
                    "select id, _id, title from problem where id = ?",
                    (rs, rowNum) -> new ProblemBaseInfo(rs.getLong("id"), rs.getString("_id"), rs.getString("title")),
                    numericId
            ).stream().findFirst().orElse(null);
        }
        return jdbcTemplate.query(
                "select id, _id, title from problem where _id = ? and visible = true order by id desc limit 1",
                (rs, rowNum) -> new ProblemBaseInfo(rs.getLong("id"), rs.getString("_id"), rs.getString("title")),
                problemIdParam
        ).stream().findFirst().orElse(null);
    }

    private boolean canViewSubmission(SubmissionRow row, AuthUser user) {
        if (user == null) return false;
        if (Objects.equals(row.userId(), user.id())) return true;
        if (user.isAdminManager() || user.canManageAllProblem()) return true;
        return row.problemCreatedById() != null && Objects.equals(row.problemCreatedById(), user.id());
    }

    private boolean isSubmissionListShowAll() {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "select value::text from sys_options where key = 'website_config'", String.class);
            if (raw != null) {
                Map<String, Object> config = parseJsonMap(raw);
                Object flag = config.get("submission_list_show_all");
                if (flag instanceof Boolean bool) return bool;
                if (flag != null) return Boolean.parseBoolean(String.valueOf(flag));
            }
        } catch (EmptyResultDataAccessException ignored) {
        }
        return properties.getWebsite().isSubmissionListShowAll();
    }

    private Map<String, Object> findUserProfile(long userId) {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "select acm_problems_status::text from user_profile where user_id = ?", String.class, userId);
            return raw == null ? Map.of() : parseJsonMap(raw);
        } catch (EmptyResultDataAccessException ignored) {
            return Map.of();
        }
    }

    private boolean hasReviewMarker(Map<String, Object> statisticInfo) {
        return statisticInfo.containsKey("needs_human_review") || statisticInfo.containsKey("human_review");
    }

    private AuthUser resolveAuthUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, admin_type, problem_permission, is_disabled from \"user\" where username = ?",
                    (rs, rowNum) -> new AuthUser(
                            rs.getLong("id"), rs.getString("username"),
                            rs.getString("admin_type"), rs.getString("problem_permission"),
                            rs.getBoolean("is_disabled")),
                    authentication.getName());
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private void requireLogin(AuthUser user) {
        if (user == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (user.disabled()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "你的账号已被禁用");
        }
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            casted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return casted;
    }

    private String toIso(Timestamp timestamp) {
        if (timestamp == null) return null;
        return ISO.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseInt(String raw, Integer fallback) {
        String normalized = trimToNull(raw);
        if (normalized == null) return fallback;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Long parseLong(String raw, Long fallback) {
        String normalized = trimToNull(raw);
        if (normalized == null) return fallback;
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record AuthUser(Long id, String username, String adminType, String problemPermission, boolean disabled) {
        boolean isAdminRole() {
            return "Admin".equals(adminType) || "Teacher".equals(adminType);
        }
        boolean isAdminManager() {
            return "Admin".equals(adminType);
        }
        boolean canManageAllProblem() {
            return "All".equals(problemPermission);
        }
    }

    private record SubmissionRow(
            String id, Long problemId, Timestamp createTime, Long userId, String username,
            String code, int result, String infoJson, String language, boolean shared,
            String statisticInfoJson, String ip, String problemDisplayId, Long problemCreatedById) {
    }

    private record ProblemBaseInfo(Long id, String displayId, String title) {
    }
}
