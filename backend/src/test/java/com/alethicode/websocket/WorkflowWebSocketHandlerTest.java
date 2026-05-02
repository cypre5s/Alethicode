package com.alethicode.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowWebSocketHandlerTest {

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private WorkflowRealtimeSupport workflowRealtimeSupport;

    @Mock
    private WebSocketSession session;

    private WorkflowWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WorkflowWebSocketHandler(jdbcTemplate, workflowRealtimeSupport, new ObjectMapper());
    }

    @Test
    void connectionShouldSubscribeWhenSessionBelongsToCurrentUser() throws Exception {
        String workflowSessionId = "wf1234567890abcdef";
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ClassroomHandshakeInterceptor.ATTR_USERNAME, "student");
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/workflow/" + workflowSessionId));
        when(session.getAttributes()).thenReturn(attributes);
        when(jdbcTemplate.queryForObject(
                eq("select id from \"user\" where username = ?"),
                eq(Long.class),
                eq("student")
        )).thenReturn(7L);
        when(jdbcTemplate.queryForObject(
                contains("from ai_workflow_session"),
                eq(Long.class),
                eq(workflowSessionId),
                eq(7L)
        )).thenReturn(1L);

        handler.afterConnectionEstablished(session);

        verify(workflowRealtimeSupport).subscribe(workflowSessionId, session);
    }

    @Test
    void cancelMessageShouldBroadcastCancelledEvent() throws Exception {
        String workflowSessionId = "wf1234567890abcdef";
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("workflow_session_id", workflowSessionId);
        when(session.getAttributes()).thenReturn(attributes);
        when(workflowRealtimeSupport.cancelTask(workflowSessionId)).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"cancel\"}"));

        verify(workflowRealtimeSupport).broadcast(eq(workflowSessionId), argThat(cancelledPayload(workflowSessionId)));
    }

    private ArgumentMatcher<Map<String, Object>> cancelledPayload(String workflowSessionId) {
        return payload -> payload != null
                && "cancelled".equals(payload.get("type"))
                && workflowSessionId.equals(payload.get("session_id"))
                && payload.get("ts") != null;
    }
}
