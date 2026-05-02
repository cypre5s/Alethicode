package com.alethicode.websocket;

import com.alethicode.service.aitutor.contract.RuntimeContract;
import com.alethicode.service.aitutor.contract.RuntimeState;
import com.alethicode.service.aitutor.contract.ServerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRealtimeSupportTest {

    private final WorkflowRealtimeSupport support = new WorkflowRealtimeSupport(new ObjectMapper());

    @AfterEach
    void tearDown() {
        support.shutdown();
    }

    @Test
    void broadcastShouldDeliverJsonPayloadToSubscribers() throws Exception {
        FakeWebSocketSession session = new FakeWebSocketSession("s1");

        support.subscribe("wf-1", session);
        support.broadcast("wf-1", Map.of("type", "node_start", "event", "CODING"));

        assertThat(session.messages).hasSize(1);
        assertThat(session.messages.getFirst()).contains("\"type\":\"node_start\"");
        assertThat(session.messages.getFirst()).contains("\"event\":\"CODING\"");
    }

    @Test
    void broadcastEventShouldPreserveExplicitServerEvent() {
        FakeWebSocketSession session = new FakeWebSocketSession("s-runtime");

        support.subscribe("wf-runtime", session);
        support.broadcastEvent(
                "wf-runtime",
                ServerEvent.TASK_COMPLETED,
                RuntimeContract.builder()
                        .sessionId("wf-runtime")
                        .runtimeState(RuntimeState.COMPLETED)
                        .build(),
                Map.of("data", Map.of("phase", "READING"))
        );

        assertThat(session.messages).hasSize(1);
        assertThat(session.messages.getFirst()).contains("\"type\":\"runtime_event\"");
        assertThat(session.messages.getFirst()).contains("\"server_event\":\"TASK_COMPLETED\"");
        assertThat(session.messages.getFirst()).doesNotContain("\"server_event\":null");
    }

    @Test
    void cancelTaskShouldStopRegisteredFutureAndClearRunningState() {
        Future<?> future = support.submit("wf-2", () -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        });
        support.registerTask("wf-2", future);

        assertThat(support.hasRunningTask("wf-2")).isTrue();
        assertThat(support.cancelTask("wf-2")).isTrue();
        assertThat(future.isCancelled()).isTrue();
        assertThat(support.hasRunningTask("wf-2")).isFalse();
    }

    @Test
    void taskCompletionShouldClearRunningStateAutomatically() throws Exception {
        Future<?> future = support.submit("wf-3", () -> {
        });
        support.registerTask("wf-3", future);

        future.get(3, TimeUnit.SECONDS);

        assertThat(support.hasRunningTask("wf-3")).isFalse();
    }

    @Test
    void submitTrackedTaskShouldRegisterBeforeExecution() throws Exception {
        Future<?> future = support.submitTrackedTask("wf-4", () -> {
        });

        assertThat(support.hasRunningTask("wf-4")).isTrue();
        future.get(3, TimeUnit.SECONDS);
        assertThat(support.hasRunningTask("wf-4")).isFalse();
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private final String id;
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private volatile boolean open = true;

        private FakeWebSocketSession(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return URI.create("ws://localhost/ws/workflow/" + id);
        }

        @Override
        public org.springframework.http.HttpHeaders getHandshakeHeaders() {
            return new org.springframework.http.HttpHeaders();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Collections.emptyMap();
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 0;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 0;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return List.of();
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            if (!open) {
                throw new IOException("session closed");
            }
            if (message instanceof TextMessage textMessage) {
                messages.add(textMessage.getPayload());
                return;
            }
            throw new IOException("unexpected message type");
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void close(CloseStatus status) {
            open = false;
        }
    }
}
