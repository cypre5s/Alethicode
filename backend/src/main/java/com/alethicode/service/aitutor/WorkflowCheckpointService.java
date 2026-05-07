package com.alethicode.service.aitutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.policy.TransitionPolicy;
import com.alethicode.service.aitutor.policy.TutorActionPolicy;
import com.alethicode.service.aitutor.profile.LearnerState;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class WorkflowCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCheckpointService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransitionPolicy transitionPolicy;
    private final TutorActionPolicy tutorActionPolicy;

    public WorkflowCheckpointService(JdbcTemplate jdbcTemplate,
                                     ObjectMapper objectMapper,
                                     TransitionPolicy transitionPolicy,
                                     TutorActionPolicy tutorActionPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transitionPolicy = transitionPolicy;
        this.tutorActionPolicy = tutorActionPolicy;
    }

    public ApiResponse<Object> workflowCheckpointList(Map<String, String> params, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String sessionId = trimToNull(params.get("session_id"));
        if (sessionId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "session_id is required");
        }
        if (!sessionOwnedByUser(user.userId(), sessionId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        List<Map<String, Object>> cps = jdbcTemplate.query(
                """
                select checkpoint_id, created_at, channel_values::text as channel_json
                from ai_workflow_checkpoint
                where session_id = ?
                order by created_at asc
                limit 50
                """,
                (rs, rowNum) -> {
                    Map<String, Object> channel = parseJsonMap(rs.getString("channel_json"));
                    String label = trimToNull(stringValue(channel.get("label")));
                    if (label == null || isNoisyCheckpointLabel(label)) {
                        return null;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("checkpoint_id", rs.getString("checkpoint_id"));
                    item.put("created_at", formatTime(rs.getTimestamp("created_at")));
                    item.put("label", label);
                    return item;
                },
                sessionId
        );
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> cp : cps) {
            if (cp != null) {
                filtered.add(cp);
            }
        }
        int fromIndex = Math.max(filtered.size() - 20, 0);
        List<Map<String, Object>> last20 = new ArrayList<>(filtered.subList(fromIndex, filtered.size()));
        java.util.Collections.reverse(last20);
        return ApiResponse.success(Map.of("session_id", sessionId, "checkpoints", last20));
    }

    public ApiResponse<Object> workflowCheckpointRestore(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String sessionId = trimToNull(stringValue(request.get("session_id")));
        String checkpointId = trimToNull(stringValue(request.get("checkpoint_id")));
        if (sessionId == null || checkpointId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "session_id and checkpoint_id are required");
        }
        Map<String, Object> session = findWorkflowSession(user.userId(), sessionId, null, true);
        if (session == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Active session not found");
        }
        Map<String, Object> channel;
        try {
            channel = jdbcTemplate.queryForObject(
                    """
                    select channel_values::text as channel_json
                    from ai_workflow_checkpoint
                    where session_id = ? and checkpoint_id = ?
                    order by created_at desc
                    limit 1
                    """,
                    (rs, rowNum) -> parseJsonMap(rs.getString("channel_json")),
                    sessionId,
                    checkpointId
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Checkpoint not found");
        }
        if (channel == null || channel.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Checkpoint not found");
        }
        String restoredPhase = normalizeWorkflowPhase(stringValue(channel.get("phase")));
        if (restoredPhase.isBlank()) {
            restoredPhase = normalizeWorkflowPhase(stringValue(session.get("phase")));
        }
        Map<String, Object> restoredOutputs = castMap(channel.get("node_outputs"));
        Map<String, Object> restoredMetrics = castMap(channel.get("behavior_metrics"));
        String restoredPending = trimToEmpty(stringValue(channel.get("pending_human_action")));
        String restoredSubmission = trimToEmpty(stringValue(channel.get("submission_id")));
        transitionPolicy.validateCheckpointRestoreOrThrow(
                normalizeWorkflowPhase(stringValue(session.get("phase"))),
                restoredPhase,
                restoredPending
        );

        jdbcTemplate.update(
                """
                update ai_workflow_session
                set phase = ?,
                    node_outputs = cast(? as jsonb),
                    behavior_metrics = cast(? as jsonb),
                    pending_human_action = ?,
                    submission_id = ?,
                    updated_at = now()
                where session_id = ?
                """,
                restoredPhase,
                toJson(restoredOutputs),
                toJson(restoredMetrics),
                restoredPending,
                restoredSubmission,
                sessionId
        );
        jdbcTemplate.update(
                """
                insert into ai_workflow_event(session_id, event_type, event_data, created_at)
                values (?, 'resume', cast(? as jsonb), now())
                """,
                sessionId,
                toJson(Map.of("restored_from", checkpointId, "phase", restoredPhase))
        );

        List<Map<String, Object>> trace = jdbcTemplate.query(
                """
                select event_type, event_data::text as event_data, created_at
                from ai_workflow_event
                where session_id = ?
                order by created_at asc
                limit 100
                """,
                (rs, rowNum) -> {
                    Map<String, Object> e = parseJsonMap(rs.getString("event_data"));
                    e.put("_event_type", rs.getString("event_type"));
                    e.put("_created_at", formatTime(rs.getTimestamp("created_at")));
                    return e;
                },
                sessionId
        );

        return ApiResponse.success(Map.of(
                "session_id", sessionId,
                "phase", restoredPhase,
                "node_outputs", restoredOutputs,
                "behavior_metrics", restoredMetrics,
                "pending_human_action", restoredPending,
                "submission_id", restoredSubmission,
                "restored_from_checkpoint", checkpointId,
                "execution_trace", trace,
                "available_actions", availableActions(restoredPhase, restoredPending)
        ));
    }

    private boolean sessionOwnedByUser(Long userId, String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from ai_workflow_session where session_id = ? and user_id = ?",
                Integer.class,
                sessionId,
                userId
        );
        return count != null && count > 0;
    }

    private boolean isNoisyCheckpointLabel(String label) {
        if (label.contains("回复格式出现了问题") || label.contains("请重新提问")) {
            return true;
        }
        if (label.startsWith("很好！") || label.startsWith("做得好！")) {
            return true;
        }
        return label.length() > 200;
    }

    private Map<String, Object> findWorkflowSession(Long userId, String sessionId, Long problemId, boolean onlyActive) {
        String where;
        List<Object> args = new ArrayList<>();
        if (sessionId != null) {
            where = "session_id = ? and user_id = ?";
            args.add(sessionId);
            args.add(userId);
        } else {
            where = "problem_id = ? and user_id = ?";
            args.add(problemId);
            args.add(userId);
        }
        if (onlyActive) {
            where += " and is_active = true";
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select session_id, thread_id, phase, node_outputs::text as node_outputs,
                           behavior_metrics::text as behavior_metrics,
                           pending_human_action, last_safe_response, submission_id,
                           is_active, problem_id
                    from ai_workflow_session
                    where """ + " " + where + " order by updated_at desc limit 1",
                    (rs, rowNum) -> {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("session_id", rs.getString("session_id"));
                        s.put("thread_id", rs.getString("thread_id"));
                        s.put("phase", normalizeWorkflowPhase(rs.getString("phase")));
                        s.put("node_outputs", parseJsonMap(rs.getString("node_outputs")));
                        s.put("behavior_metrics", parseJsonMap(rs.getString("behavior_metrics")));
                        s.put("pending_human_action", rs.getString("pending_human_action"));
                        s.put("last_safe_response", rs.getString("last_safe_response"));
                        s.put("submission_id", rs.getString("submission_id"));
                        s.put("is_active", rs.getBoolean("is_active"));
                        s.put("problem_id", rs.getLong("problem_id"));
                        return s;
                    },
                    args.toArray()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> availableActions(String phase, String pending) {
        return tutorActionPolicy.decide(
                normalizeWorkflowPhase(phase),
                trimToEmpty(pending),
                new LearnerState(false, Map.of(), List.of(), Map.of(), Map.of(), "low", "low", Map.of(), List.of(), "", true)
        ).availableActions();
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

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return data;
        }
        return new LinkedHashMap<>();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeWorkflowPhase(String phase) {
        String normalized = trimToEmpty(phase).trim().toUpperCase(Locale.ROOT);
        if ("SCAFFOLDING".equals(normalized)) {
            return "IDEATING";
        }
        return normalized;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private record UserAuth(boolean authenticated,
                            Long userId,
                            boolean admin,
                            boolean adminManager,
                            boolean teacher,
                            Set<Long> accessibleLanguagePackIds) {
    }
}
