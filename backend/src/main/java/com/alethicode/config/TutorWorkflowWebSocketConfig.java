package com.alethicode.config;

import com.alethicode.websocket.ClassroomHandshakeInterceptor;
import com.alethicode.websocket.TutorWorkflowWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the tutor workflow WebSocket endpoint under a strict origin whitelist
 * (via {@link WebSocketOriginConfigurer}) and behind the shared handshake
 * interceptor so every connection carries an authenticated username / userId.
 *
 * <p>The previous configuration used {@code setAllowedOrigins("*")} without any
 * authentication check — any remote origin could open the socket and observe
 * another student's tutor events. Ownership still must be validated per message
 * by {@link TutorWorkflowWebSocketHandler}; this config only enforces the
 * connection prerequisites.
 */
@Configuration
@EnableWebSocket
public class TutorWorkflowWebSocketConfig implements WebSocketConfigurer {

    private final TutorWorkflowWebSocketHandler handler;
    private final ClassroomHandshakeInterceptor handshakeInterceptor;
    private final AlethicodeProperties properties;

    public TutorWorkflowWebSocketConfig(TutorWorkflowWebSocketHandler handler,
                                        ClassroomHandshakeInterceptor handshakeInterceptor,
                                        AlethicodeProperties properties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketOriginConfigurer.apply(
                registry.addHandler(handler, "/ws/tutor-workflow-sessions/**")
                        .addInterceptors(handshakeInterceptor),
                properties);
    }
}
