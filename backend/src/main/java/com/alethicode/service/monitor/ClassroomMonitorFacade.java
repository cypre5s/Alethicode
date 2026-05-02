package com.alethicode.service.monitor;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.ClassroomCurrentProblemResponse;
import com.alethicode.dto.response.ClassroomMonitorSnapshotItemResponse;
import com.alethicode.dto.response.ClassroomMonitorSnapshotsResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Lazy
public class ClassroomMonitorFacade {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;
    private final ClassroomMonitorQueryService classroomMonitorQueryService;

    public ClassroomMonitorFacade(JdbcTemplate jdbcTemplate,
                                  ClassroomMonitorQueryService classroomMonitorQueryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.classroomMonitorQueryService = classroomMonitorQueryService;
    }

    public ApiResponse<Object> monitorStats(String classroomId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        ensureMonitorAccess(classroomId, user);
        return ApiResponse.success(classroomMonitorQueryService.queryStats(classroomId));
    }

    public ApiResponse<Object> monitorSnapshots(String classroomId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        ensureMonitorAccess(classroomId, user);
        List<ClassroomMonitorSnapshotItemResponse> results = classroomMonitorQueryService.querySnapshotRows(classroomId)
                .stream()
                .map(row -> {
                    ClassroomCurrentProblemResponse currentProblem = row.currentProblemId() == null
                            ? null
                            : new ClassroomCurrentProblemResponse(row.currentProblemId(), row.currentProblemTitle());
                    return new ClassroomMonitorSnapshotItemResponse(
                            row.userId(),
                            row.username(),
                            row.realName(),
                            row.activityStatus(),
                            row.errorTaxonomy(),
                            currentProblem,
                            row.codeLength(),
                            formatTime(row.lastActivity()),
                            row.activeTime(),
                            row.submissionCount(),
                            row.acCount(),
                            row.progress()
                    );
                })
                .toList();
        return ApiResponse.success(new ClassroomMonitorSnapshotsResponse(results, results.size()));
    }

    public ApiResponse<Object> monitorErrorClusters(String classroomId,
                                                    Map<String, String> params,
                                                    Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        ensureMonitorAccess(classroomId, user);
        int minutes = Math.max(1, parseInt(params == null ? null : params.get("time_window"), 1440));
        return ApiResponse.success(classroomMonitorQueryService.queryErrorClusters(classroomId, minutes));
    }

    private void ensureMonitorAccess(String classroomId, UserAuth user) {
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!classroomExists(classroomId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "班级不存在");
        }
        if (!isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅班级成员可访问该班级监控数据");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可查看监控数据");
        }
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null);
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return new UserAuth(false, null);
        }
        Long userId = jdbcTemplate.query(
                "select id from \"user\" where lower(username) = ?",
                (rs, rowNum) -> rs.getLong("id"),
                username.toLowerCase()
        ).stream().findFirst().orElse(null);
        return new UserAuth(userId != null, userId);
    }

    private boolean classroomExists(String classroomId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom where id = ?",
                Integer.class,
                classroomId
        );
        return count != null && count > 0;
    }

    private boolean isMember(String classroomId, Long userId) {
        if (userId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ?",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    private boolean isStaff(String classroomId, Long userId) {
        if (userId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role in ('owner','ta')",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record UserAuth(boolean authenticated, Long userId) {
    }
}
