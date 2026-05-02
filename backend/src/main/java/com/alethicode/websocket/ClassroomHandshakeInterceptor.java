package com.alethicode.websocket;

import com.alethicode.middleware.SessionAuthenticationFilter;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Shared WebSocket handshake interceptor that enforces session authentication and
 * stores {@code ws_username} / {@code ws_user_id} on the connection attributes so
 * downstream handlers (classroom collab, monitor, tutor workflow) can run
 * ownership checks without touching HTTP session again.
 */
@Component
public class ClassroomHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USERNAME = "ws_username";
    public static final String ATTR_USER_ID = "ws_user_id";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        if (session == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        Object username = session.getAttribute(SessionAuthenticationFilter.AUTH_USERNAME_KEY);
        if (username == null || String.valueOf(username).isBlank()) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_USERNAME, String.valueOf(username));
        Object userId = session.getAttribute(SessionAuthenticationFilter.AUTH_USER_ID_KEY);
        // Defense in depth: only propagate a positive numeric user id. A malformed
        // session attribute (null, negative, string) must never reach WS handlers as
        // a usable identity — downstream ownership checks would then authorize the
        // wrong user.
        if (userId instanceof Number n && n.longValue() > 0) {
            attributes.put(ATTR_USER_ID, n.longValue());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
