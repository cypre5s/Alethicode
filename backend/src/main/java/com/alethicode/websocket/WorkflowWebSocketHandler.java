package com.alethicode.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class WorkflowWebSocketHandler extends TextWebSocketHandler {

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowRealtimeSupport workflowRealtimeSupport;
    private final ObjectMapper objectMapper;

    public WorkflowWebSocketHandler(JdbcTemplate jdbcTemplate,
                                    WorkflowRealtimeSupport workflowRealtimeSupport,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.workflowRealtimeSupport = workflowRealtimeSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String workflowSessionId = tailId(session.getUri() == null ? "" : session.getUri().getPath());
        if (!isValidWorkflowSessionId(workflowSessionId)) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String username = String.valueOf(session.getAttributes().getOrDefault(ClassroomHandshakeInterceptor.ATTR_USERNAME, ""));
        Long userId = userIdByUsername(username);
        if (userId == null || !workflowSessionOwnedByUser(workflowSessionId, userId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put("workflow_session_id", workflowSessionId);
        session.getAttributes().put("user_id", userId);
        workflowRealtimeSupport.subscribe(workflowSessionId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String workflowSessionId = String.valueOf(session.getAttributes().getOrDefault("workflow_session_id", ""));
        if (!workflowSessionId.isBlank()) {
            workflowRealtimeSupport.unsubscribe(workflowSessionId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = parseJson(message.getPayload());
        String type = String.valueOf(payload.getOrDefault("type", ""));
        String workflowSessionId = String.valueOf(session.getAttributes().getOrDefault("workflow_session_id", ""));
        if (workflowSessionId.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if ("cancel".equals(type)) {
            if (workflowRealtimeSupport.cancelTask(workflowSessionId)) {
                workflowRealtimeSupport.broadcast(workflowSessionId, Map.of(
                        "type", "cancelled",
                        "session_id", workflowSessionId,
                        "ts", Instant.now().toEpochMilli()
                ));
            }
            return;
        }
        send(session, Map.of("type", "error", "message", "Unknown message type: " + type));
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private Map<String, Object> parseJson(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Long userIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id from \"user\" where username = ?",
                    Long.class,
                    username
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private boolean workflowSessionOwnedByUser(String workflowSessionId, Long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from ai_workflow_session
                where session_id = ? and user_id = ? and is_active = true
                """,
                Long.class,
                workflowSessionId,
                userId
        );
        return count != null && count > 0;
    }

    private String tailId(String path) {
        String normalized = path == null ? "" : path.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int index = normalized.lastIndexOf('/');
        if (index < 0) {
            return normalized;
        }
        return normalized.substring(index + 1);
    }

    private boolean isValidWorkflowSessionId(String workflowSessionId) {
        return workflowSessionId != null && workflowSessionId.matches("^[A-Za-z0-9]{16,64}$");
    }
}
