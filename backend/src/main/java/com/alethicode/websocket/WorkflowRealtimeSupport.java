package com.alethicode.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.alethicode.service.aitutor.contract.RuntimeContract;
import com.alethicode.service.aitutor.contract.ServerEvent;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

@Component
public class WorkflowRealtimeSupport {

    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final Map<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> activeTasks = new ConcurrentHashMap<>();

    public WorkflowRealtimeSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.executorService = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("workflow-realtime-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void subscribe(String sessionId, WebSocketSession session) {
        subscribers.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unsubscribe(String sessionId, WebSocketSession session) {
        Set<WebSocketSession> sessions = subscribers.getOrDefault(sessionId, Collections.emptySet());
        sessions.remove(session);
        if (sessions.isEmpty()) {
            subscribers.remove(sessionId);
        }
    }

    public void broadcast(String sessionId, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = subscribers.getOrDefault(sessionId, Collections.emptySet());
        if (sessions.isEmpty()) {
            return;
        }
        final String encoded;
        try {
            encoded = objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize workflow websocket payload", exception);
        }
        for (WebSocketSession session : sessions) {
            safeSend(sessionId, session, encoded);
        }
    }

    public void broadcastEvent(String sessionId, ServerEvent event, RuntimeContract contract) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "runtime_event");
        payload.putAll(contract.toMap());
        payload.put("server_event", event.name());
        broadcast(sessionId, payload);
    }

    public void broadcastEvent(String sessionId, ServerEvent event, RuntimeContract contract, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "runtime_event");
        payload.putAll(contract.toMap());
        payload.put("server_event", event.name());
        payload.putAll(extra);
        broadcast(sessionId, payload);
    }

    public Future<?> submit(String sessionId, Runnable runnable) {
        return executorService.submit(runnable);
    }

    public Future<?> submitTrackedTask(String sessionId, Runnable runnable) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            runnable.run();
            return null;
        }) {
            @Override
            protected void done() {
                activeTasks.remove(sessionId, this);
            }
        };
        activeTasks.put(sessionId, task);
        executorService.execute(task);
        return task;
    }

    public void registerTask(String sessionId, Future<?> task) {
        activeTasks.put(sessionId, task);
        executorService.execute(() -> {
            try {
                task.get();
            } catch (CancellationException ignored) {
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ignored) {
            } finally {
                activeTasks.remove(sessionId, task);
            }
        });
    }

    public boolean hasRunningTask(String sessionId) {
        Future<?> current = activeTasks.get(sessionId);
        if (current == null) {
            return false;
        }
        if (current.isDone() || current.isCancelled()) {
            activeTasks.remove(sessionId, current);
            return false;
        }
        return true;
    }

    public boolean isTaskActive(String sessionId, Future<?> task) {
        if (task == null) {
            return false;
        }
        Future<?> current = activeTasks.get(sessionId);
        return current == task && !task.isCancelled() && !task.isDone();
    }

    public boolean cancelTask(String sessionId) {
        Future<?> current = activeTasks.remove(sessionId);
        if (current == null) {
            return false;
        }
        return current.cancel(true);
    }

    @PreDestroy
    public void shutdown() {
        for (Future<?> task : activeTasks.values()) {
            task.cancel(true);
        }
        activeTasks.clear();
        subscribers.clear();
        executorService.shutdownNow();
    }

    private void safeSend(String sessionId, WebSocketSession session, String encodedPayload) {
        if (!session.isOpen()) {
            unsubscribe(sessionId, session);
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(encodedPayload));
            }
        } catch (IOException ignored) {
            unsubscribe(sessionId, session);
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException ignoredAgain) {
            }
        }
    }
}
