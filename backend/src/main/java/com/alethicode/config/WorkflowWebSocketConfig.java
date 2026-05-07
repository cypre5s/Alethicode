package com.alethicode.config;

import com.alethicode.websocket.ClassroomHandshakeInterceptor;
import com.alethicode.websocket.QaWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * QA WebSocket 配置。
 *
 * 旧工作流 WS 已移除；导学工作流改由 {@link TutorWorkflowWebSocketConfig} 注册。
 */
@Configuration
@EnableWebSocket
public class WorkflowWebSocketConfig implements WebSocketConfigurer {

    private final QaWebSocketHandler qaWebSocketHandler;
    private final ClassroomHandshakeInterceptor classroomHandshakeInterceptor;
    private final AlethicodeProperties properties;

    public WorkflowWebSocketConfig(QaWebSocketHandler qaWebSocketHandler,
                                   ClassroomHandshakeInterceptor classroomHandshakeInterceptor,
                                   AlethicodeProperties properties) {
        this.qaWebSocketHandler = qaWebSocketHandler;
        this.classroomHandshakeInterceptor = classroomHandshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketOriginConfigurer.apply(
                registry.addHandler(qaWebSocketHandler, "/ws/qa/*")
                        .addInterceptors(classroomHandshakeInterceptor),
                properties);
    }
}
