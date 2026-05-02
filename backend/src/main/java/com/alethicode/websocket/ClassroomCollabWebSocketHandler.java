package com.alethicode.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClassroomCollabWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ClassroomCollabWebSocketHandler.class);

    private final ClassroomWebSocketSupport support;
    private final ObjectMapper objectMapper;

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Map<String, Object>>> roomPresence = new ConcurrentHashMap<>();

    public ClassroomCollabWebSocketHandler(ClassroomWebSocketSupport support, ObjectMapper objectMapper) {
        this.support = support;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = tailId(session.getUri() == null ? "" : session.getUri().getPath());
        String username = String.valueOf(session.getAttributes().getOrDefault(ClassroomHandshakeInterceptor.ATTR_USERNAME, ""));
        Long userId = support.userIdByUsername(username);
        Map<String, Object> collabSession = support.sessionRow(sessionId);
        if (userId == null || collabSession.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String classroomId = String.valueOf(collabSession.get("classroom_id"));
        if (!support.isClassroomMember(classroomId, userId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put("collab_session_id", sessionId);
        session.getAttributes().put("classroom_id", classroomId);
        session.getAttributes().put("user_id", userId);
        session.getAttributes().put("username", username);

        roomSessions.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(session);
        roomPresence.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>())
                .put(userId, userPayload(userId, username, false, false));
        support.updateSessionParticipantCount(sessionId, 1);

        broadcast(sessionId, Map.of("type", "user_join", "user", userPayload(userId, username, false, false)));
        sendOnlineUsers(sessionId);
        sendInitState(session, collabSession);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = String.valueOf(session.getAttributes().getOrDefault("collab_session_id", ""));
        Long userId = parseLong(session.getAttributes().get("user_id"));
        Set<WebSocketSession> sessions = roomSessions.getOrDefault(sessionId, Collections.emptySet());
        sessions.remove(session);
        if (sessions.isEmpty()) {
            roomSessions.remove(sessionId);
        }
        Map<Long, Map<String, Object>> presence = roomPresence.getOrDefault(sessionId, new ConcurrentHashMap<>());
        if (userId != null) {
            presence.remove(userId);
            broadcast(sessionId, Map.of("type", "user_leave", "user_id", userId));
        }
        if (presence.isEmpty()) {
            roomPresence.remove(sessionId);
        }
        support.updateSessionParticipantCount(sessionId, -1);
        sendOnlineUsers(sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = parseJson(message.getPayload());
        String type = String.valueOf(payload.getOrDefault("type", ""));
        String sessionId = String.valueOf(session.getAttributes().getOrDefault("collab_session_id", ""));
        Long userId = parseLong(session.getAttributes().get("user_id"));
        String username = String.valueOf(session.getAttributes().getOrDefault("username", ""));
        if (sessionId.isBlank() || userId == null) {
            return;
        }

        switch (type) {
            case "relay.request_token" -> handleRelayRequestToken(sessionId, userId, username, session);
            case "relay.cancel_request_token" -> handleRelayCancel(sessionId, userId, session);
            case "relay.release_token" -> handleRelayRelease(sessionId, userId);
            case "relay.edit" -> broadcastOthers(sessionId, session, Map.of(
                    "type", "relay.edit_synced",
                    "user_id", userId,
                    "yjs_update", payload.get("yjs_update"),
                    "timestamp", Instant.now().toEpochMilli()
            ));
            case "scaffolding.edit" -> broadcastOthers(sessionId, session, Map.of(
                    "type", "scaffolding.edit_synced",
                    "user_id", userId,
                    "user_name", username,
                    "yjs_update", payload.get("yjs_update"),
                    "timestamp", Instant.now().toEpochMilli()
            ));
            case "yjs.update" -> broadcastOthers(sessionId, session, Map.of(
                    "type", "yjs.update",
                    "update", payload.get("update")
            ));
            case "code_update" -> {
                markEditing(sessionId, userId);
                broadcastOthers(sessionId, session, Map.of(
                        "type", "code_update",
                        "user_id", userId,
                        "code", String.valueOf(payload.getOrDefault("code", "")),
                        "timestamp", Instant.now().toEpochMilli()
                ));
                sendOnlineUsers(sessionId);
            }
            case "chat_message" -> {
                String text = String.valueOf(payload.getOrDefault("text", "")).trim();
                if (!text.isEmpty()) {
                    if (text.length() > 1000) {
                        text = text.substring(0, 1000);
                    }
                    broadcast(sessionId, Map.of(
                            "type", "chat_message",
                            "message", Map.of(
                                    "id", Instant.now().toEpochMilli(),
                                    "user_id", userId,
                                    "username", username,
                                    "text", text,
                                    "timestamp", Instant.now().toEpochMilli()
                            )
                    ));
                }
            }
            default -> send(session, Map.of("type", "error", "message", "Unknown message type: " + type));
        }
    }

    private void handleRelayRequestToken(String sessionId, Long userId, String username, WebSocketSession session) throws IOException {
        Map<String, Object> result = support.relayRequestToken(sessionId, userId, username);
        boolean granted = Boolean.TRUE.equals(result.get("granted"));
        if (granted) {
            broadcast(sessionId, Map.of(
                    "type", "relay.token_granted",
                    "token_holder_id", userId,
                    "token_holder_name", username,
                    "timestamp", Instant.now().toEpochMilli()
            ));
        } else {
            send(session, Map.of(
                    "type", "relay.token_waiting",
                    "current_holder_id", result.get("token_holder_id"),
                    "queue_position", result.get("queue_position"),
                    "timestamp", Instant.now().toEpochMilli()
            ));
        }
        broadcastRelayStatus(sessionId, result.get("status"));
        sendOnlineUsers(sessionId);
    }

    private void handleRelayCancel(String sessionId, Long userId, WebSocketSession session) throws IOException {
        Map<String, Object> result = support.relayCancelRequest(sessionId, userId);
        send(session, Map.of(
                "type", "relay.token_cancelled",
                "removed", result.get("removed"),
                "queue_length", result.get("queue_length"),
                "timestamp", Instant.now().toEpochMilli()
        ));
        broadcastRelayStatus(sessionId, result.get("status"));
        sendOnlineUsers(sessionId);
    }

    private void handleRelayRelease(String sessionId, Long userId) throws IOException {
        Map<String, Object> result = support.relayReleaseToken(sessionId, userId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            broadcast(sessionId, Map.of(
                    "type", "relay.token_transferred",
                    "previous_holder_id", userId,
                    "new_holder_id", result.get("next_holder_id"),
                    "timestamp", Instant.now().toEpochMilli()
            ));
        }
        broadcastRelayStatus(sessionId, result.get("status"));
        sendOnlineUsers(sessionId);
    }

    private void sendInitState(WebSocketSession session, Map<String, Object> collabSession) throws IOException {
        String mode = String.valueOf(collabSession.getOrDefault("mode", ""));
        if ("scaffolding".equals(mode)) {
            Map<String, Object> config = castMap(collabSession.get("scaffolding_config"));
            send(session, Map.of(
                    "type", "scaffolding.init_response",
                    "template_code", String.valueOf(config.getOrDefault("template_code", "")),
                    "editable_ranges", config.getOrDefault("editable_ranges", List.of()),
                    "readonly_ranges", config.getOrDefault("readonly_ranges", List.of()),
                    "timestamp", Instant.now().toEpochMilli()
            ));
        } else if ("relay".equals(mode)) {
            Map<String, Object> relay = castMap(collabSession.get("relay_config"));
            Map<String, Object> status = support.relayStatus(relay);
            send(session, Map.of(
                    "type", "relay.status",
                    "token_holder_id", status.get("token_holder_id"),
                    "token_holder_name", status.get("token_holder_name"),
                    "waiting_queue", status.get("waiting_queue"),
                    "queue_length", status.get("queue_length"),
                    "token_timeout_seconds", status.get("token_timeout_seconds"),
                    "timestamp", Instant.now().toEpochMilli()
            ));
        }
    }

    private void broadcastRelayStatus(String sessionId, Object statusObj) throws IOException {
        Map<String, Object> status = castMap(statusObj);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "relay.status");
        payload.putAll(status);
        payload.put("timestamp", Instant.now().toEpochMilli());
        broadcast(sessionId, payload);
    }

    private void markEditing(String sessionId, Long userId) {
        Map<Long, Map<String, Object>> presence = roomPresence.get(sessionId);
        if (presence == null) {
            return;
        }
        Map<String, Object> payload = presence.get(userId);
        if (payload == null) {
            return;
        }
        payload.put("last_edit_ts", Instant.now().toEpochMilli());
        payload.put("is_editing", true);
    }

    private void sendOnlineUsers(String sessionId) throws IOException {
        Map<Long, Map<String, Object>> presence = roomPresence.getOrDefault(sessionId, Map.of());
        List<Map<String, Object>> users = new ArrayList<>();
        long now = Instant.now().toEpochMilli();
        for (Map<String, Object> value : presence.values()) {
            Map<String, Object> item = new LinkedHashMap<>(value);
            long lastEditTs = parseLong(item.get("last_edit_ts")) == null ? 0 : parseLong(item.get("last_edit_ts"));
            item.put("is_editing", lastEditTs > 0 && now - lastEditTs < 2500);
            users.add(item);
        }
        broadcast(sessionId, Map.of("type", "online_users", "users", users));
    }

    private void broadcast(String sessionId, Map<String, Object> payload) throws IOException {
        for (WebSocketSession target : roomSessions.getOrDefault(sessionId, Collections.emptySet())) {
            send(target, payload);
        }
    }

    private void broadcastOthers(String sessionId, WebSocketSession sender, Map<String, Object> payload) throws IOException {
        for (WebSocketSession target : roomSessions.getOrDefault(sessionId, Collections.emptySet())) {
            if (!target.getId().equals(sender.getId())) {
                send(target, payload);
            }
        }
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
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> userPayload(Long userId, String username, boolean hasToken, boolean isEditing) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", userId);
        payload.put("username", username);
        payload.put("avatar", "");
        payload.put("has_token", hasToken);
        payload.put("is_editing", isEditing);
        payload.put("last_edit_ts", 0L);
        return payload;
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

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseLong: parse failed for {}", value, e);
            return null;
        }
    }

    private String tailId(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return path;
        }
        return path.substring(index + 1);
    }
}
