package com.alethicode.websocket;

import com.alethicode.service.aitutor.graph.TutorGraphClient;
import com.alethicode.service.aitutor.graph.TutorWorkflowProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Component
public class TutorWorkflowWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TutorWorkflowWebSocketHandler.class);
    private static final Set<String> TERMINAL_EVENTS = Set.of("TASK_COMPLETED", "TASK_FAILED", "TASK_EXPIRED");
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    /**
     * Hard deadline for any single tutor run's event poller. A real tutor run finishes
     * in seconds to a few minutes; anything longer is either an interrupt waiting for
     * the student (handled via explicit {@code INTERRUPT_TIMEOUT_SECONDS} on the
     * Python side) or a stuck run we shouldn't leak virtual threads for. 10 minutes
     * keeps resource usage bounded without biting legitimate long completions.
     */
    private static final Duration MAX_RUN_DURATION = Duration.ofMinutes(10);

    private final TutorGraphClient graphClient;
    private final TutorWorkflowProjectionService projectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    /**
     * Tracks the active event-poller thread per runId. A second subscription (e.g. an
     * interrupt resume) must interrupt the previous poller so the frontend never
     * receives duplicate runtime events.
     */
    private final ConcurrentHashMap<String, Thread> runPollers = new ConcurrentHashMap<>();
    private volatile BiConsumer<String, String> runCompletionCallback = (sessionId, runId) -> {};

    public TutorWorkflowWebSocketHandler(
            TutorGraphClient graphClient,
            TutorWorkflowProjectionService projectionService
    ) {
        this.graphClient = graphClient;
        this.projectionService = projectionService;
    }

    public void setRunCompletionCallback(BiConsumer<String, String> callback) {
        this.runCompletionCallback = callback != null ? callback : (s, r) -> {};
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            closeQuietly(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }
        Long userId = extractUserId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("Unauthenticated"));
            return;
        }
        if (!projectionService.isSessionOwnedByUser(sessionId, userId)) {
            log.warn("Tutor workflow WS ownership denied: sessionId={} userId={}", sessionId, userId);
            closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("Session not owned by user"));
            return;
        }
        // Same session id may be opened from another tab; replace the previous handle and
        // close it so runtime events don't fan-out to stale tabs.
        WebSocketSession previous = sessionMap.put(sessionId, session);
        if (previous != null && previous != session) {
            closeQuietly(previous, CloseStatus.NORMAL.withReason("Replaced by newer connection"));
        }
        log.info("Tutor workflow WS connected: sessionId={} userId={}", sessionId, userId);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("Close failed: {}", e.getMessage());
        }
    }

    private Long extractUserId(WebSocketSession session) {
        Object raw = session.getAttributes().get("ws_user_id");
        if (raw instanceof Number n) return n.longValue();
        return null;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId == null) return;

        Map<String, Object> msg;
        try {
            msg = objectMapper.readValue(message.getPayload(), Map.class);
        } catch (Exception e) {
            log.warn("Invalid WS payload from {}: {}", sessionId, e.getMessage());
            return;
        }
        String type = (String) msg.get("type");

        if ("cancel".equals(type)) {
            String runId = (String) msg.get("run_id");
            if (runId != null) {
                graphClient.cancelRun(runId).subscribe(
                        result -> log.info("Run cancelled: {}", runId),
                        error -> log.warn("Cancel failed: {}", error.getMessage())
                );
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = extractSessionId(session);
        if (sessionId == null) return;
        // Only remove when the closing connection is still the current one, so a race
        // between the old tab closing and a newer tab replacing it cannot drop the live
        // entry.
        sessionMap.remove(sessionId, session);
        log.info("Tutor workflow WS disconnected: {}", sessionId);
    }

    public void subscribeToRunEvents(String sessionId, String runId) {
        Thread next = Thread.ofVirtual()
                .name("tutor-graph-poll-" + runId)
                .uncaughtExceptionHandler((t, e) ->
                        log.error("Uncaught exception in event poller for run {}: {}", runId, e.getMessage(), e))
                .unstarted(() -> {
                    try {
                        pollRunEvents(sessionId, runId);
                    } finally {
                        runPollers.remove(runId, Thread.currentThread());
                    }
                });
        Thread previous = runPollers.put(runId, next);
        if (previous != null && previous.isAlive()) {
            previous.interrupt();
        }
        next.start();
    }

    /**
     * Stop the poller attached to {@code runId}, if any. Called by
     * {@code TutorWorkflowController.deleteSession} to release virtual-thread /
     * WebClient resources immediately instead of waiting for the run's terminal
     * event (which may never arrive for a cancelled session).
     */
    public void interruptPoller(String runId) {
        if (runId == null) return;
        Thread poller = runPollers.remove(runId);
        if (poller != null && poller.isAlive()) {
            poller.interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private void pollRunEvents(String sessionId, String runId) {
        long deadlineNanos = System.nanoTime() + MAX_RUN_DURATION.toNanos();
        int sentCount = 0;
        boolean terminalSeen = false;

        while (System.nanoTime() < deadlineNanos) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            // Bail out early if the user disconnected — otherwise we keep polling
            // tutor-graph for up to MAX_RUN_DURATION for a session nobody is watching.
            if (!sessionMap.containsKey(sessionId)) {
                log.debug("Run poller {}: websocket gone for session {}, stopping", runId, sessionId);
                break;
            }
            try {
                Map<String, Object> response = graphClient.getRunEvents(runId)
                        .block(Duration.ofSeconds(10));
                if (response == null) {
                    Thread.sleep(POLL_INTERVAL.toMillis());
                    continue;
                }

                List<Map<String, Object>> events = (List<Map<String, Object>>) response.get("events");
                if (events == null) {
                    Thread.sleep(POLL_INTERVAL.toMillis());
                    continue;
                }

                while (sentCount < events.size()) {
                    Map<String, Object> evt = events.get(sentCount);
                    sentCount++;
                    sendRuntimeEvent(sessionId, evt);

                    String serverEvent = (String) evt.get("server_event");
                    if (serverEvent != null && TERMINAL_EVENTS.contains(serverEvent)) {
                        terminalSeen = true;
                        break;
                    }
                }

                if (terminalSeen) break;
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Poll run events failed for {}/{}: {}", sessionId, runId, e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        try {
            runCompletionCallback.accept(sessionId, runId);
        } catch (Exception e) {
            log.warn("Run completion callback failed for {}/{}: {}", sessionId, runId, e.getMessage());
        }
    }

    public void sendRuntimeEvent(String sessionId, Map<String, Object> event) {
        WebSocketSession ws = sessionMap.get(sessionId);
        if (ws == null || !ws.isOpen()) return;
        try {
            String payload = objectMapper.writeValueAsString(event);
            synchronized (ws) {
                if (ws.isOpen()) {
                    ws.sendMessage(new TextMessage(payload));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send runtime event to session {}: {}", sessionId, e.getMessage());
        }
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String prefix = "/ws/tutor-workflow-sessions/";
        int idx = path.indexOf(prefix);
        if (idx < 0) return null;
        String rest = path.substring(idx + prefix.length()).replaceAll("/+$", "");
        return rest.isEmpty() ? null : rest;
    }
}
