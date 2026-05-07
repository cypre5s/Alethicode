package com.alethicode.service.monitor;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.ClassroomErrorClusterItemResponse;
import com.alethicode.dto.response.ClassroomErrorClustersResponse;
import com.alethicode.service.monitor.ClassroomMonitorFacade;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClassroomMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomMonitorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ClassroomMonitorFacade classroomMonitorFacade;

    public ClassroomMonitorService(JdbcTemplate jdbcTemplate, ClassroomMonitorFacade classroomMonitorFacade) {
        this.jdbcTemplate = jdbcTemplate;
        this.classroomMonitorFacade = classroomMonitorFacade;
    }

    public ApiResponse<Object> monitorStats(String classroomId, Authentication authentication) {
        return classroomMonitorFacade.monitorStats(classroomId, authentication);
    }

    public ApiResponse<Object> monitorSnapshots(String classroomId, Authentication authentication) {
        return classroomMonitorFacade.monitorSnapshots(classroomId, authentication);
    }

    public ApiResponse<Object> monitorPlayback(String classroomId, Map<String, String> params, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!classroomExists(classroomId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "班级不存在");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可访问");
        }
        Long studentId = parseLongObj(params.get("student_id"));
        if (studentId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "缺少 student_id 参数");
        }
        Integer studentExists = jdbcTemplate.queryForObject(
                "select count(*) from \"user\" where id = ?",
                Integer.class,
                studentId
        );
        if (studentExists == null || studentExists == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "学生不存在");
        }
        Integer isStudentMember = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role = 'student'",
                Integer.class,
                classroomId,
                studentId
        );
        if (isStudentMember == null || isStudentMember == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "该学生不是班级成员");
        }

        List<Map<String, Object>> frames = jdbcTemplate.query(
                """
                select snapshot_time, code_snapshot, activity_status, error_taxonomy, edit_distance, submission_count, ac_count
                from student_monitoring_snapshot
                where classroom_id = ? and user_id = ?
                order by snapshot_time asc
                limit 200
                """,
                (rs, rowNum) -> {
                    int editDistance = rs.getInt("edit_distance");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("timestamp", toMillis(rs.getTimestamp("snapshot_time")));
                    row.put("code", trimToEmpty(rs.getString("code_snapshot")));
                    row.put("activity_status", trimToEmpty(rs.getString("activity_status")));
                    row.put("error_taxonomy", rs.getString("error_taxonomy"));
                    row.put("edit_distance", editDistance);
                    row.put("is_major_change", editDistance > 50);
                    row.put("submission_count", rs.getInt("submission_count"));
                    row.put("ac_count", rs.getInt("ac_count"));
                    row.put("result", null);
                    row.put("source", "snapshot");
                    return row;
                },
                classroomId,
                studentId
        );
        if (frames.isEmpty()) {
            List<Long> problemIds = classroomProblemObjectIds(classroomId);
            List<Map<String, Object>> submissions = fallbackSubmissionsForPlayback(studentId, problemIds);
            int acTotal = 0;
            int index = 0;
            for (Map<String, Object> sub : submissions) {
                index++;
                Integer result = parseIntObjNullable(sub.get("result"));
                if (result != null && result == 0) {
                    acTotal++;
                }
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("timestamp", toMillis((Timestamp) sub.get("create_time")));
                frame.put("code", trimToEmpty(stringValue(sub.get("code"))));
                frame.put("activity_status", result != null && result == 0 ? "completed" : "submitted");
                frame.put("edit_distance", null);
                frame.put("is_major_change", false);
                frame.put("submission_count", index);
                frame.put("ac_count", acTotal);
                frame.put("result", result);
                frame.put("source", "submission");
                frames.add(frame);
            }
        }
        return ApiResponse.success(Map.of("frames", frames));
    }

    public ApiResponse<Object> monitorCoach(String classroomId, Map<String, String> params, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!classroomExists(classroomId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "班级不存在");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可访问");
        }
        String action = trimToNull(params.get("action"));
        if (action == null) {
            action = "interventions";
        }
        int minutes = Math.max(1, Math.min(parseInt(params.get("minutes"), 30), 1440));
        Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(minutes * 60L));

        if ("clusters".equals(action)) {
            return ApiResponse.success(buildErrorClustersResponse(classroomId, cutoff, minutes));
        }

        List<Map<String, Object>> candidates = jdbcTemplate.query(
                """
                select distinct on (cm.user_id)
                       cm.user_id, u.username, s.activity_status, s.error_taxonomy, s.snapshot_time
                from classroom_member cm
                join "user" u on u.id = cm.user_id
                left join student_monitoring_snapshot s
                       on s.classroom_id = cm.classroom_id and s.user_id = cm.user_id
                where cm.classroom_id = ? and cm.role = 'student'
                order by cm.user_id, s.snapshot_time desc nulls last
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String activityStatus = trimToEmpty(rs.getString("activity_status"));
                    Timestamp time = rs.getTimestamp("snapshot_time");
                    long ageSeconds = time == null ? Long.MAX_VALUE : Math.max(0, Instant.now().getEpochSecond() - time.toInstant().getEpochSecond());
                    int priority = 0;
                    String reason = "";
                    if ("abnormal".equals(activityStatus)) {
                        priority = 90;
                        reason = "近期出现编译/运行异常";
                    } else if ("idle".equals(activityStatus) || ageSeconds > 120) {
                        priority = 70;
                        reason = "近期活跃度低，可能需要引导";
                    }
                    row.put("user_id", rs.getLong("user_id"));
                    row.put("username", rs.getString("username"));
                    row.put("priority", priority);
                    row.put("reason", reason);
                    row.put("activity_status", activityStatus);
                    row.put("error_taxonomy", rs.getString("error_taxonomy"));
                    return row;
                },
                classroomId
        );
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            if (parseIntObj(candidate.get("priority"), 0) > 0) {
                filtered.add(candidate);
            }
        }
        filtered.sort((a, b) -> Integer.compare(parseIntObj(b.get("priority"), 0), parseIntObj(a.get("priority"), 0)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("candidates", filtered);
        if (filtered.isEmpty()) {
            data.put("hint", "近 " + minutes + " 分钟暂无需干预对象");
        }
        return ApiResponse.success(data);
    }

    public ApiResponse<Object> monitorErrorClusters(String classroomId, Map<String, String> params, Authentication authentication) {
        return classroomMonitorFacade.monitorErrorClusters(classroomId, params, authentication);
    }

    public ApiResponse<Object> monitorInterventionCandidates(String classroomId, Map<String, String> params, Authentication authentication) {
        Map<String, String> coachParams = new HashMap<>(params);
        coachParams.put("action", "interventions");
        coachParams.putIfAbsent("minutes", String.valueOf(Math.max(1, parseInt(params.get("time_window"), 30))));
        return monitorCoach(classroomId, coachParams, authentication);
    }

    private List<Long> classroomProblemObjectIds(String classroomId) {
        return jdbcTemplate.query(
                "select problem_id from classroom_problem where classroom_id = ?",
                (rs, rowNum) -> rs.getLong("problem_id"),
                classroomId
        );
    }

    private List<Map<String, Object>> fallbackSubmissionsForPlayback(Long studentId, List<Long> problemIds) {
        String where = " where user_id = ?";
        if (!problemIds.isEmpty()) {
            String in = String.join(",", problemIds.stream().map(String::valueOf).toList());
            Integer inClassroom = jdbcTemplate.queryForObject(
                    "select count(*) from submission where user_id = ? and problem_id in (" + in + ")",
                    Integer.class,
                    studentId
            );
            if (inClassroom != null && inClassroom > 0) {
                where += " and problem_id in (" + in + ")";
            }
        }
        List<Map<String, Object>> descRows = jdbcTemplate.query(
                "select result, code, create_time from submission" + where + " order by create_time desc limit 200",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("result", rs.getInt("result"));
                    row.put("code", rs.getString("code"));
                    row.put("create_time", rs.getTimestamp("create_time"));
                    return row;
                },
                studentId
        );
        List<Map<String, Object>> asc = new ArrayList<>();
        for (int i = descRows.size() - 1; i >= 0; i--) {
            asc.add(descRows.get(i));
        }
        return asc;
    }

    private ClassroomErrorClustersResponse buildErrorClustersResponse(String classroomId, Timestamp cutoff, int minutes) {
        List<ClassroomErrorClusterItemResponse> clusters = jdbcTemplate.query(
                """
                select error_taxonomy, count(*) as total
                from student_monitoring_snapshot
                where classroom_id = ? and snapshot_time >= ?
                  and activity_status = 'abnormal' and error_taxonomy is not null
                group by error_taxonomy
                order by total desc
                """,
                (rs, rowNum) -> new ClassroomErrorClusterItemResponse(
                        rs.getString("error_taxonomy"),
                        rs.getLong("total")
                ),
                classroomId,
                cutoff
        );
        String hint = clusters.isEmpty() ? "近 " + minutes + " 分钟暂无可聚类的误区数据" : null;
        return new ClassroomErrorClustersResponse(clusters, hint);
    }

    private long toMillis(Timestamp timestamp) {
        return timestamp == null ? 0 : timestamp.toInstant().toEpochMilli();
    }

    private boolean classroomExists(String classroomId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from classroom where id = ?", Integer.class, classroomId);
        return count != null && count > 0;
    }

    private boolean isStaff(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role in ('owner','ta')",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false, null);
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        String adminType = trimToEmpty(rs.getString("admin_type"));
                        boolean admin = "Admin".equals(adminType) || "Teacher".equals(adminType);
                        boolean adminManager = "Admin".equals(adminType);
                        return new UserAuth(true, rs.getLong("id"), admin, adminManager, rs.getString("username"));
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false, null);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseInt failed for raw={}, using fallback {}", raw, fallback, e);
            return fallback;
        }
    }

    private int parseIntObj(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseIntObj failed for value={}, using fallback {}", value, fallback, e);
            return fallback;
        }
    }

    private Integer parseIntObjNullable(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseIntObjNullable failed for value={}", value, e);
            return null;
        }
    }

    private Long parseLongObj(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseLongObj failed for value={}", value, e);
            return null;
        }
    }

    private record UserAuth(boolean authenticated, Long userId, boolean admin, boolean adminManager, String username) {
    }
}
