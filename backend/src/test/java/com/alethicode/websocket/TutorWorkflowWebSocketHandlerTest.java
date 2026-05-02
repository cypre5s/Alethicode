package com.alethicode.websocket;

import com.alethicode.service.aitutor.graph.TutorGraphClient;
import com.alethicode.service.aitutor.graph.TutorWorkflowProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

/**
 * Targets the handshake-time guarantees of {@link TutorWorkflowWebSocketHandler}:
 * only an authenticated user that owns the session projection may receive runtime
 * events. Anything else is closed before the first message is buffered.
 */
class TutorWorkflowWebSocketHandlerTest {

    private TutorGraphClient graphClient;
    private TutorWorkflowProjectionService projectionService;
    private TutorWorkflowWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        graphClient = mock(TutorGraphClient.class);
        projectionService = mock(TutorWorkflowProjectionService.class);
        handler = new TutorWorkflowWebSocketHandler(graphClient, projectionService);
    }

    @Test
    void afterConnectionEstablished_missingSessionIdInPath_closes() throws Exception {
        WebSocketSession session = mockSession("/ws/tutor-workflow-sessions/", 7L);
        handler.afterConnectionEstablished(session);
        verify(session).close(any(CloseStatus.class));
        verify(projectionService, never()).isSessionOwnedByUser(any(), any(Long.class));
    }

    @Test
    void afterConnectionEstablished_missingUserId_closes() throws Exception {
        WebSocketSession session = mockSession("/ws/tutor-workflow-sessions/twf_x", null);
        handler.afterConnectionEstablished(session);
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionEstablished_nonOwner_closes() throws Exception {
        WebSocketSession session = mockSession("/ws/tutor-workflow-sessions/twf_x", 7L);
        when(projectionService.isSessionOwnedByUser("twf_x", 7L)).thenReturn(false);

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionEstablished_owner_keepsSessionOpen() throws Exception {
        WebSocketSession session = mockSession("/ws/tutor-workflow-sessions/twf_x", 7L);
        when(projectionService.isSessionOwnedByUser("twf_x", 7L)).thenReturn(true);
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        // sendRuntimeEvent is the only legal way for the handler to reach the session,
        // so verifying that it now addresses this session proves the sessionMap slot
        // was populated by afterConnectionEstablished.
        handler.sendRuntimeEvent("twf_x", Map.of(
                "type", "runtime_event",
                "session_id", "twf_x",
                "server_event", "TASK_STARTED"));
        verify(session, atLeastOnce()).sendMessage(any());
    }

    @Test
    void afterConnectionEstablished_duplicateSession_replacesPrevious() throws Exception {
        when(projectionService.isSessionOwnedByUser("twf_x", 7L)).thenReturn(true);

        WebSocketSession first = mockSession("/ws/tutor-workflow-sessions/twf_x", 7L);
        when(first.isOpen()).thenReturn(true);
        WebSocketSession second = mockSession("/ws/tutor-workflow-sessions/twf_x", 7L);
        when(second.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        verify(first).close(any(CloseStatus.class));
    }

    private static WebSocketSession mockSession(String path, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost" + path));
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) {
            attrs.put("ws_user_id", userId);
            attrs.put("ws_username", "user_" + userId);
        }
        when(session.getAttributes()).thenReturn(attrs);
        return session;
    }
}
