package com.alethicode.config;

import com.alethicode.websocket.ClassroomHandshakeInterceptor;
import com.alethicode.websocket.TutorWorkflowWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册导学工作流 WebSocket 端点。
 *
 * <p>端点必须经过严格 origin 白名单和统一握手认证；消息级所有权仍由
 * {@link TutorWorkflowWebSocketHandler} 校验。</p>
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
