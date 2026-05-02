package com.alethicode.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ClassroomMonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ClassroomMonitorWebSocketHandler.class);

    private static final long SLOW_SEND_THRESHOLD_MILLIS = 1200L;
    private static final int SEND_EXECUTOR_QUEUE_SIZE = 512;

    private final ClassroomWebSocketSupport support;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskScheduler scheduler;
    private final ThreadPoolExecutor sendExecutor;

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pushTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> roomPushRunning = new ConcurrentHashMap<>();

    public ClassroomMonitorWebSocketHandler(ClassroomWebSocketSupport support, ObjectMapper objectMapper) {
        this.support = support;
        this.objectMapper = objectMapper;
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(4);
        this.scheduler.setThreadNamePrefix("classroom-monitor-push-");
        this.scheduler.initialize();
        this.sendExecutor = new ThreadPoolExecutor(
                4,
                12,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(SEND_EXECUTOR_QUEUE_SIZE),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("classroom-monitor-send-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String classroomId = tailId(session.getUri() == null ? "" : session.getUri().getPath());
        if (!isValidClassroomId(classroomId)) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String username = String.valueOf(session.getAttributes().getOrDefault(ClassroomHandshakeInterceptor.ATTR_USERNAME, ""));
        Long userId = support.userIdByUsername(username);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        boolean staff = support.isClassroomStaff(classroomId, userId);
        boolean student = !staff && support.isClassroomStudent(classroomId, userId);
        if (!staff && !student) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put("classroom_id", classroomId);
        session.getAttributes().put("user_id", userId);
        session.getAttributes().put("is_staff", staff);

        rooms.computeIfAbsent(classroomId, key -> ConcurrentHashMap.newKeySet()).add(session);
        if (staff) {
            startPushTaskIfNeeded(classroomId);
            sendMonitorStatus(session, classroomId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String classroomId = String.valueOf(session.getAttributes().getOrDefault("classroom_id", ""));
        Set<WebSocketSession> members = rooms.getOrDefault(classroomId, Collections.emptySet());
        members.remove(session);
        if (members.isEmpty()) {
            rooms.remove(classroomId);
            ScheduledFuture<?> task = pushTasks.remove(classroomId);
            if (task != null) {
                task.cancel(true);
            }
            roomPushRunning.remove(classroomId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = parseJson(message.getPayload());
        String type = String.valueOf(payload.getOrDefault("type", ""));
        String classroomId = String.valueOf(session.getAttributes().getOrDefault("classroom_id", ""));
        Long userId = parseLong(session.getAttributes().get("user_id"));
        boolean isStaff = Boolean.TRUE.equals(session.getAttributes().get("is_staff"));

        if ("telemetry.heartbeat".equals(type)) {
            if (userId != null && !isStaff) {
                support.insertSnapshot(classroomId, userId, payload);
                broadcastTeachers(classroomId, Map.of(
                        "type", "telemetry.heartbeat",
                        "user_id", userId,
                        "username", support.usernameByUserId(userId),
                        "code_length", intValue(payload.get("code_length"), 0),
                        "current_line", intValue(payload.get("current_line"), 0),
                        "idle_seconds", intValue(payload.get("idle_seconds"), 0),
                        "error_count", intValue(payload.get("error_count"), 0),
                        "status_color", statusColor(intValue(payload.get("idle_seconds"), 0), intValue(payload.get("error_count"), 0)),
                        "timestamp", Instant.now().toEpochMilli()
                ));
            }
            return;
        }

        if ("monitor.request_snapshots".equals(type) && isStaff) {
            Long targetUserId = parseLong(payload.get("user_id"));
            if (targetUserId != null) {
                List<Map<String, Object>> snapshots = support.userSnapshots(classroomId, targetUserId, 20);
                send(session, Map.of(
                        "type", "monitor.snapshots_response",
                        "user_id", targetUserId,
                        "snapshots", snapshots,
                        "timestamp", Instant.now().toEpochMilli()
                ));
            }
            return;
        }

        send(session, Map.of("type", "error", "message", "Unknown message type: " + type));
    }

    private void startPushTaskIfNeeded(String classroomId) {
        AtomicBoolean runningFlag = roomPushRunning.computeIfAbsent(classroomId, key -> new AtomicBoolean(false));
        pushTasks.computeIfAbsent(classroomId, key -> scheduler.scheduleAtFixedRate(
                () -> {
                    if (!runningFlag.compareAndSet(false, true)) {
                        return;
                    }
                    try {
                        pushStatusToTeachers(classroomId);
                    } catch (Exception e) {
                        log.warn("pushStatusToTeachers failed for classroomId={}", classroomId, e);
                    } finally {
                        runningFlag.set(false);
                    }
                },
                Duration.ofSeconds(5)
        ));
    }

    private void pushStatusToTeachers(String classroomId) throws IOException {
        List<Map<String, Object>> students = support.monitorStudentStatus(classroomId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "monitor.student_status_update");
        payload.put("classroom_id", classroomId);
        payload.put("students", students);
        payload.put("timestamp", Instant.now().toEpochMilli());
        broadcastTeachers(classroomId, payload);
    }

    private void sendMonitorStatus(WebSocketSession session, String classroomId) throws IOException {
        List<Map<String, Object>> students = support.monitorStudentStatus(classroomId);
        send(session, Map.of(
                "type", "monitor.student_status_update",
                "classroom_id", classroomId,
                "students", students,
                "timestamp", Instant.now().toEpochMilli()
        ));
    }

    private void broadcastTeachers(String classroomId, Map<String, Object> payload) throws IOException {
        List<WebSocketSession> sessions = new ArrayList<>(rooms.getOrDefault(classroomId, Collections.emptySet()));
        for (WebSocketSession session : sessions) {
            if (Boolean.TRUE.equals(session.getAttributes().get("is_staff"))) {
                dispatchSend(classroomId, session, payload);
            }
        }
    }

    private void dispatchSend(String classroomId, WebSocketSession session, Map<String, Object> payload) {
        try {
            sendExecutor.execute(() -> safeSend(classroomId, session, payload));
        } catch (RejectedExecutionException rejectedExecutionException) {
            closeSessionFailfast(classroomId, session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private void safeSend(String classroomId, WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) {
            closeSessionFailfast(classroomId, session, CloseStatus.NORMAL);
            return;
        }
        long start = System.nanoTime();
        try {
            synchronized (session) {
                send(session, payload);
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (elapsedMillis > SLOW_SEND_THRESHOLD_MILLIS) {
                closeSessionFailfast(classroomId, session, CloseStatus.SESSION_NOT_RELIABLE);
            }
        } catch (IOException ignored) {
            closeSessionFailfast(classroomId, session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private void closeSessionFailfast(String classroomId, WebSocketSession session, CloseStatus closeStatus) {
        try {
            session.close(closeStatus);
        } catch (IOException ignored) {
        }
        Set<WebSocketSession> members = rooms.get(classroomId);
        if (members != null) {
            members.remove(session);
            if (members.isEmpty()) {
                rooms.remove(classroomId);
                ScheduledFuture<?> task = pushTasks.remove(classroomId);
                if (task != null) {
                    task.cancel(true);
                }
                roomPushRunning.remove(classroomId);
            }
        }
    }

    private String statusColor(int idleSeconds, int errorCount) {
        if (idleSeconds > 30 || errorCount > 3) {
            return "red";
        }
        if (idleSeconds > 15 || errorCount > 1) {
            return "yellow";
        }
        return "green";
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

    private boolean isValidClassroomId(String classroomId) {
        if (classroomId == null || classroomId.isBlank()) {
            return false;
        }
        return classroomId.matches("^[A-Za-z0-9]{8,64}$");
    }
}
