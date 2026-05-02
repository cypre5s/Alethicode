package com.alethicode.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.alethicode.service.aitutor.contract.ActivityStatus;
import com.alethicode.service.aitutor.contract.ErrorTaxonomy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ClassroomWebSocketSupport {

    private static final Logger log = LoggerFactory.getLogger(ClassroomWebSocketSupport.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ClassroomWebSocketSupport(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Long userIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return jdbcTemplate.query(
                "select id from \"user\" where lower(username) = ?",
                (rs, rowNum) -> rs.getLong("id"),
                username.toLowerCase()
        ).stream().findFirst().orElse(null);
    }

    public String usernameByUserId(Long userId) {
        if (userId == null) {
            return "";
        }
        return jdbcTemplate.query(
                "select username from \"user\" where id = ?",
                (rs, rowNum) -> rs.getString("username"),
                userId
        ).stream().findFirst().orElse("");
    }

    public Map<String, Object> sessionRow(String sessionId) {
        return jdbcTemplate.query(
                """
                select id, classroom_id, mode, relay_config::text as relay_config_json,
                       scaffolding_config::text as scaffolding_config_json, host_id
                from classroom_session
                where id = ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("classroom_id", rs.getString("classroom_id"));
                    row.put("mode", rs.getString("mode"));
                    row.put("relay_config", parseJsonMap(rs.getString("relay_config_json")));
                    row.put("scaffolding_config", parseJsonMap(rs.getString("scaffolding_config_json")));
                    row.put("host_id", rs.getLong("host_id"));
                    return row;
                },
                sessionId
        ).stream().findFirst().orElse(Map.of());
    }

    public boolean isClassroomMember(String classroomId, Long userId) {
        if (classroomId == null || userId == null) {
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

    public boolean isClassroomStaff(String classroomId, Long userId) {
        if (classroomId == null || userId == null) {
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

    public boolean isClassroomStudent(String classroomId, Long userId) {
        if (classroomId == null || userId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role = 'student'",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    public void updateSessionParticipantCount(String sessionId, int delta) {
        jdbcTemplate.update(
                """
                update classroom_session
                set participant_count = greatest(participant_count + ?, 0), update_time = now()
                where id = ?
                """,
                delta,
                sessionId
        );
    }

    public Map<String, Object> relayRequestToken(String sessionId, Long userId, String username) {
        Map<String, Object> session = sessionRow(sessionId);
        if (session.isEmpty()) {
            return Map.of("error", "session_not_found");
        }
        Map<String, Object> relay = castMap(session.get("relay_config"));
        Long holderId = longValue(relay.get("token_holder_id"));
        List<Object> queue = new ArrayList<>(castList(relay.get("waiting_queue")));
        if (holderId == null || holderId == 0) {
            relay.put("token_holder_id", userId);
            relay.put("token_holder_name", username);
            relay.put("token_acquired_at", Instant.now().toString());
            relay.put("waiting_queue", queue);
            saveRelayConfig(sessionId, relay);
            return Map.of("granted", true, "token_holder_id", userId, "queue_position", 0, "status", relayStatus(relay));
        }
        int pos = -1;
        for (int i = 0; i < queue.size(); i++) {
            Map<String, Object> item = castMap(queue.get(i));
            Long uid = longValue(item.get("user_id"));
            if (uid != null && uid.equals(userId)) {
                pos = i + 1;
                break;
            }
        }
        if (pos == -1) {
            queue.add(Map.of("user_id", userId, "username", username, "requested_at", Instant.now().toString()));
            pos = queue.size();
        }
        relay.put("waiting_queue", queue);
        saveRelayConfig(sessionId, relay);
        return Map.of("granted", false, "token_holder_id", holderId, "queue_position", pos, "status", relayStatus(relay));
    }

    public Map<String, Object> relayCancelRequest(String sessionId, Long userId) {
        Map<String, Object> session = sessionRow(sessionId);
        if (session.isEmpty()) {
            return Map.of("removed", false, "queue_length", 0, "status", Map.of());
        }
        Map<String, Object> relay = castMap(session.get("relay_config"));
        List<Object> queue = new ArrayList<>(castList(relay.get("waiting_queue")));
        int before = queue.size();
        queue.removeIf(item -> {
            Long uid = longValue(castMap(item).get("user_id"));
            return uid != null && uid.equals(userId);
        });
        relay.put("waiting_queue", queue);
        saveRelayConfig(sessionId, relay);
        return Map.of("removed", before != queue.size(), "queue_length", queue.size(), "status", relayStatus(relay));
    }

    public Map<String, Object> relayReleaseToken(String sessionId, Long userId) {
        Map<String, Object> session = sessionRow(sessionId);
        if (session.isEmpty()) {
            return Map.of("success", false, "status", Map.of());
        }
        Map<String, Object> relay = castMap(session.get("relay_config"));
        Long holder = longValue(relay.get("token_holder_id"));
        if (holder == null || !holder.equals(userId)) {
            return Map.of("success", false, "status", relayStatus(relay));
        }
        List<Object> queue = new ArrayList<>(castList(relay.get("waiting_queue")));
        Long nextHolder = null;
        String nextName = "";
        if (!queue.isEmpty()) {
            Map<String, Object> next = castMap(queue.remove(0));
            nextHolder = longValue(next.get("user_id"));
            nextName = stringValue(next.get("username"));
        }
        relay.put("token_holder_id", nextHolder);
        relay.put("token_holder_name", nextName);
        relay.put("token_acquired_at", Instant.now().toString());
        relay.put("waiting_queue", queue);
        saveRelayConfig(sessionId, relay);
        return Map.of("success", true, "next_holder_id", nextHolder, "status", relayStatus(relay));
    }

    public Map<String, Object> relayStatus(Map<String, Object> relayConfig) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token_holder_id", longValue(relayConfig.get("token_holder_id")));
        payload.put("token_holder_name", stringValue(relayConfig.get("token_holder_name")));
        payload.put("waiting_queue", castList(relayConfig.get("waiting_queue")));
        payload.put("queue_length", castList(relayConfig.get("waiting_queue")).size());
        payload.put("token_timeout_seconds", intValue(relayConfig.get("token_timeout_seconds"), 300));
        return payload;
    }

    public List<Map<String, Object>> monitorStudentStatus(String classroomId) {
        return jdbcTemplate.query(
                """
                select distinct on (cm.user_id) cm.user_id, u.username,
                       s.activity_status, s.error_taxonomy, s.snapshot_time, s.code_snapshot,
                       s.submission_count, s.ac_count, s.elapsed_time_seconds
                from classroom_member cm
                join "user" u on u.id = cm.user_id
                left join student_monitoring_snapshot s
                       on s.classroom_id = cm.classroom_id and s.user_id = cm.user_id
                where cm.classroom_id = ? and cm.role = 'student'
                order by cm.user_id, s.snapshot_time desc nulls last
                """,
                (rs, rowNum) -> {
                    Timestamp snapshotTime = rs.getTimestamp("snapshot_time");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("user_id", rs.getLong("user_id"));
                    row.put("username", rs.getString("username"));
                    row.put("activity_status", rs.getString("activity_status"));
                    row.put("error_taxonomy", rs.getString("error_taxonomy"));
                    row.put("last_edit_time", snapshotTime == null ? 0 : snapshotTime.toInstant().toEpochMilli());
                    row.put("code_length", rs.getString("code_snapshot") == null ? 0 : rs.getString("code_snapshot").length());
                    row.put("submission_count", rs.getInt("submission_count"));
                    row.put("ac_count", rs.getInt("ac_count"));
                    row.put("elapsed_time_seconds", rs.getInt("elapsed_time_seconds"));
                    return row;
                },
                classroomId
        );
    }

    public List<Map<String, Object>> userSnapshots(String classroomId, Long userId, int limit) {
        return jdbcTemplate.query(
                """
                select snapshot_time, code_snapshot, activity_status, error_taxonomy, edit_distance
                from student_monitoring_snapshot
                where classroom_id = ? and user_id = ?
                order by snapshot_time desc
                limit ?
                """,
                (rs, rowNum) -> {
                    int editDistance = rs.getInt("edit_distance");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("timestamp", rs.getTimestamp("snapshot_time").toInstant().toEpochMilli());
                    row.put("code", rs.getString("code_snapshot"));
                    row.put("activity_status", rs.getString("activity_status"));
                    row.put("error_taxonomy", rs.getString("error_taxonomy"));
                    row.put("edit_distance", editDistance);
                    row.put("is_major_change", editDistance > 50);
                    return row;
                },
                classroomId,
                userId,
                limit
        );
    }

    public void insertSnapshot(String classroomId, Long userId, Map<String, Object> payload) {
        String rawStatus = stringValue(payload.get("status"), "typing");
        String activityStatus;
        String errorTaxonomy;
        if (payload.containsKey("activity_status")) {
            activityStatus = stringValue(payload.get("activity_status"), ActivityStatus.TYPING);
            errorTaxonomy = payload.get("error_taxonomy") == null ? null : stringValue(payload.get("error_taxonomy"));
        } else {
            activityStatus = ActivityStatus.fromLegacyStatus(rawStatus);
            errorTaxonomy = ActivityStatus.errorTaxonomyFromLegacyStatus(rawStatus);
        }
        jdbcTemplate.update(
                """
                insert into student_monitoring_snapshot(id, classroom_id, user_id, activity_status, error_taxonomy,
                                                        code_snapshot, code_hash,
                                                        edit_distance, submission_count, ac_count, elapsed_time_seconds, snapshot_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                randomId(),
                classroomId,
                userId,
                activityStatus,
                errorTaxonomy,
                stringValue(payload.get("code"), ""),
                stringValue(payload.get("code_hash"), ""),
                intValue(payload.get("edit_distance"), 0),
                intValue(payload.get("submission_count"), 0),
                intValue(payload.get("ac_count"), 0),
                intValue(payload.get("elapsed_time_seconds"), 0)
        );
    }

    private void saveRelayConfig(String sessionId, Map<String, Object> relayConfig) {
        jdbcTemplate.update(
                "update classroom_session set relay_config = cast(? as jsonb), update_time = now() where id = ?",
                toJson(relayConfig),
                sessionId
        );
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }

    private List<Object> castList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return new LinkedHashMap<>();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.debug("longValue: parse failed for {}", value, e);
            return null;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("intValue: parse failed for {}, using fallback {}", value, fallback, e);
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String v = String.valueOf(value);
        return v.isBlank() ? fallback : v;
    }

    private String randomId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
