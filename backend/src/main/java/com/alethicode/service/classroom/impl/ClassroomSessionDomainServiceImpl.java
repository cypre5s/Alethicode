package com.alethicode.service.classroom.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomAccessHelper;
import com.alethicode.service.classroom.ClassroomAccessHelper.UserAuth;
import com.alethicode.service.classroom.ClassroomSessionDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.alethicode.util.ServiceParseUtils.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClassroomSessionDomainServiceImpl implements ClassroomSessionDomainService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ClassroomAccessHelper access;

    public ClassroomSessionDomainServiceImpl(JdbcTemplate jdbcTemplate,
                                             ObjectMapper objectMapper,
                                             ClassroomAccessHelper access) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.access = access;
    }

    @Override
    public ApiResponse<Object> sessionList(String classroomId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            return ApiResponse.success(Map.of("results", List.of(), "total", 0));
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select s.id, s.classroom_id, s.classroom_problem_id, s.title, s.description, s.mode,
                       s.yjs_doc_name, s.relay_config::text as relay_config_json,
                       s.scaffolding_config::text as scaffolding_config_json,
                       s.host_id, u.username as host_username,
                       s.is_active, s.started_at, s.ended_at, s.participant_count, s.create_time
                from classroom_session s
                join "user" u on u.id = s.host_id
                where s.classroom_id = ?
                order by s.create_time desc
                """,
                (rs, rowNum) -> mapSessionRow(rs),
                classroomId
        );
        return ApiResponse.success(Map.of("results", rows, "total", rows.size()));
    }

    @Override
    public ApiResponse<Object> sessionCreate(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.classroomExists(classroomId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Classroom not found");
        }
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        String title = trimToNull(stringValue(request.get("title")));
        if (title == null) {
            title = trimToNull(stringValue(request.get("name")));
        }
        if (title == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "title is required");
        }
        String mode = normalizeSessionMode(stringValue(request.get("mode")));
        String classroomProblemId = resolveClassroomProblemId(classroomId, request);

        Map<String, Object> relayConfig = castMap(request.get("relay_config"));
        if (relayConfig.isEmpty()) {
            relayConfig = defaultRelayConfig();
        }
        Map<String, Object> scaffoldingConfig = castMap(request.get("scaffolding_config"));
        String sessionId = randomId();
        jdbcTemplate.update(
                """
                insert into classroom_session(id, classroom_id, classroom_problem_id, title, description,
                                              mode, yjs_doc_name, relay_config, scaffolding_config,
                                              host_id, is_active, started_at, participant_count, create_time, update_time)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, true, now(), 0, now(), now())
                """,
                sessionId,
                classroomId,
                classroomProblemId,
                title,
                trimToNull(stringValue(request.get("description"))),
                mode,
                "classroom:" + classroomId + ":session:" + randomId().substring(0, 12),
                toJson(objectMapper, relayConfig),
                toJson(objectMapper, scaffoldingConfig),
                user.userId()
        );
        return sessionRetrieve(classroomId, sessionId, authentication);
    }

    @Override
    public ApiResponse<Object> sessionRetrieve(String classroomId, String sessionId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        Map<String, Object> row = jdbcTemplate.query(
                """
                select s.id, s.classroom_id, s.classroom_problem_id, s.title, s.description, s.mode,
                       s.yjs_doc_name, s.relay_config::text as relay_config_json,
                       s.scaffolding_config::text as scaffolding_config_json,
                       s.host_id, u.username as host_username,
                       s.is_active, s.started_at, s.ended_at, s.participant_count, s.create_time
                from classroom_session s
                join "user" u on u.id = s.host_id
                where s.classroom_id = ? and s.id = ?
                """,
                (rs, rowNum) -> mapSessionRow(rs),
                classroomId,
                sessionId
        ).stream().findFirst().orElse(null);
        if (row == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        return ApiResponse.success(row);
    }

    @Override
    public ApiResponse<Object> sessionDelete(String classroomId, String sessionId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        int deleted = jdbcTemplate.update("delete from classroom_session where classroom_id = ? and id = ?", classroomId, sessionId);
        if (deleted == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        return ApiResponse.success("Session deleted");
    }

    @Override
    public ApiResponse<Object> sessionEnd(String classroomId, String sessionId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        Map<String, Object> session = jdbcTemplate.query(
                "select host_id from classroom_session where classroom_id = ? and id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("host_id", rs.getLong("host_id"));
                    return row;
                },
                classroomId,
                sessionId
        ).stream().findFirst().orElse(null);
        if (session == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        Long hostId = parseLongObj(session.get("host_id"));
        if (!(access.isStaff(classroomId, user.userId()) || (hostId != null && hostId.equals(user.userId())))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        jdbcTemplate.update(
                "update classroom_session set is_active = false, ended_at = now(), update_time = now() where classroom_id = ? and id = ?",
                classroomId, sessionId);
        return ApiResponse.success("Session ended");
    }

    @Override
    public ApiResponse<Object> sessionTransferToken(String classroomId, String sessionId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Map<String, Object> session = jdbcTemplate.query(
                "select mode, relay_config::text as relay_config_json from classroom_session where classroom_id = ? and id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("mode", rs.getString("mode"));
                    row.put("relay_config_json", rs.getString("relay_config_json"));
                    return row;
                },
                classroomId,
                sessionId
        ).stream().findFirst().orElse(null);
        if (session == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        if (!"relay".equals(trimToEmpty(stringValue(session.get("mode"))))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Only relay mode supports token transfer");
        }
        Long targetUserId = parseLongObj(request.get("target_user_id"));
        if (targetUserId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "target_user_id is required");
        }
        String targetUsername = jdbcTemplate.query(
                "select username from \"user\" where id = ?",
                (rs, rowNum) -> rs.getString("username"),
                targetUserId
        ).stream().findFirst().orElse(null);
        if (targetUsername == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Target user not found");
        }
        Map<String, Object> relayConfig = parseJsonMap(objectMapper, stringValue(session.get("relay_config_json")));
        List<Object> waiting = new ArrayList<>();
        for (Object item : castList(relayConfig.get("waiting_queue"))) {
            Map<String, Object> node = castMap(item);
            Long uid = parseLongObj(node.get("user_id"));
            if (uid == null || !uid.equals(targetUserId)) {
                waiting.add(node);
            }
        }
        relayConfig.put("waiting_queue", waiting);
        relayConfig.put("token_holder_id", targetUserId);
        relayConfig.put("token_holder_name", targetUsername);
        relayConfig.put("token_acquired_at", nowIso());
        jdbcTemplate.update(
                "update classroom_session set relay_config = cast(? as jsonb), update_time = now() where classroom_id = ? and id = ?",
                toJson(objectMapper, relayConfig), classroomId, sessionId);
        return ApiResponse.success("Token transferred");
    }

    private String resolveClassroomProblemId(String classroomId, Map<String, Object> request) {
        String classroomProblemId = trimToNull(stringValue(request.get("classroom_problem")));
        if (classroomProblemId == null) {
            Long problemId = parseLongObj(request.get("problem_id"));
            if (problemId != null) {
                classroomProblemId = jdbcTemplate.query(
                        "select id from classroom_problem where classroom_id = ? and problem_id = ?",
                        (rs, rowNum) -> rs.getString("id"),
                        classroomId, problemId
                ).stream().findFirst().orElse(null);
            }
        } else {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from classroom_problem where classroom_id = ? and id = ?",
                    Integer.class, classroomId, classroomProblemId);
            if (count == null || count == 0) {
                classroomProblemId = null;
            }
        }
        return classroomProblemId;
    }

    private String normalizeSessionMode(String rawMode) {
        String mode = trimToNull(rawMode);
        if (mode == null) return "free";
        String normalized = mode.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "free", "relay", "scaffolding" -> normalized;
            case "pair", "group", "teacher_demo" -> "free";
            default -> normalized;
        };
    }

    private Map<String, Object> defaultRelayConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("token_holder_id", null);
        config.put("token_timeout_seconds", 300);
        config.put("auto_next_enabled", true);
        config.put("waiting_queue", List.of());
        return config;
    }

    private Map<String, Object> mapSessionRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("id", rs.getLong("host_id"));
        host.put("username", rs.getString("host_username"));
        host.put("avatar", null);

        String mode = trimToEmpty(rs.getString("mode"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("classroom", rs.getString("classroom_id"));
        row.put("classroom_problem", rs.getString("classroom_problem_id"));
        row.put("title", rs.getString("title"));
        row.put("description", rs.getString("description"));
        row.put("mode", mode);
        row.put("mode_display", modeDisplay(mode));
        row.put("yjs_doc_name", rs.getString("yjs_doc_name"));
        row.put("relay_config", parseJsonMap(objectMapper, rs.getString("relay_config_json")));
        row.put("scaffolding_config", parseJsonMap(objectMapper, rs.getString("scaffolding_config_json")));
        row.put("host", host);
        row.put("is_active", rs.getBoolean("is_active"));
        row.put("started_at", formatTime(rs.getTimestamp("started_at")));
        row.put("ended_at", formatTime(rs.getTimestamp("ended_at")));
        row.put("participant_count", rs.getInt("participant_count"));
        row.put("create_time", formatTime(rs.getTimestamp("create_time")));
        return row;
    }

    private String modeDisplay(String mode) {
        return switch (trimToEmpty(mode)) {
            case "relay" -> "代码接力";
            case "scaffolding" -> "编程填空";
            case "free" -> "自由协作";
            default -> mode;
        };
    }
}
