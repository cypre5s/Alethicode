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
 * 统一校验 WebSocket 握手登录态，并把用户身份写入连接属性。
 *
 * 下游处理器只信任 {@code ws_username} / {@code ws_user_id}，避免各自重复读取
 * HTTP session。
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
        // 只透传正数用户 ID，避免异常 session 值绕过下游所有权校验。
        if (userId instanceof Number n && n.longValue() > 0) {
            attributes.put(ATTR_USER_ID, n.longValue());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
