package com.alethicode.websocket;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class QaWebSocketHandler extends TextWebSocketHandler {

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowRealtimeSupport workflowRealtimeSupport;

    public QaWebSocketHandler(JdbcTemplate jdbcTemplate,
                              WorkflowRealtimeSupport workflowRealtimeSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.workflowRealtimeSupport = workflowRealtimeSupport;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String qaSessionId = tailId(session.getUri() == null ? "" : session.getUri().getPath());
        if (!isValidQaSessionId(qaSessionId)) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String username = String.valueOf(session.getAttributes().getOrDefault(ClassroomHandshakeInterceptor.ATTR_USERNAME, ""));
        Long userId = userIdByUsername(username);
        if (userId == null || !qaSessionOwnedByUser(qaSessionId, userId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String channelId = qaChannelId(qaSessionId);
        session.getAttributes().put("qa_session_id", qaSessionId);
        session.getAttributes().put("qa_channel_id", channelId);
        session.getAttributes().put("user_id", userId);
        workflowRealtimeSupport.subscribe(channelId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String channelId = String.valueOf(session.getAttributes().getOrDefault("qa_channel_id", ""));
        if (!channelId.isBlank()) {
            workflowRealtimeSupport.unsubscribe(channelId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    public static String qaChannelId(String qaSessionId) {
        return "qa:" + qaSessionId;
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

    private boolean qaSessionOwnedByUser(String qaSessionId, Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from language_pack_chat_session where id = ? and user_id = ?",
                Long.class,
                Long.parseLong(qaSessionId),
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

    private boolean isValidQaSessionId(String qaSessionId) {
        if (qaSessionId == null || qaSessionId.isBlank()) {
            return false;
        }
        try {
            Long.parseLong(qaSessionId);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
