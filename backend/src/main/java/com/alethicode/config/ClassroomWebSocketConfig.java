package com.alethicode.config;

import com.alethicode.websocket.ClassroomCollabWebSocketHandler;
import com.alethicode.websocket.ClassroomHandshakeInterceptor;
import com.alethicode.websocket.ClassroomMonitorWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ClassroomWebSocketConfig implements WebSocketConfigurer {

    private final ClassroomCollabWebSocketHandler classroomCollabWebSocketHandler;
    private final ClassroomMonitorWebSocketHandler classroomMonitorWebSocketHandler;
    private final ClassroomHandshakeInterceptor classroomHandshakeInterceptor;
    private final AlethicodeProperties properties;

    public ClassroomWebSocketConfig(ClassroomCollabWebSocketHandler classroomCollabWebSocketHandler,
                                    ClassroomMonitorWebSocketHandler classroomMonitorWebSocketHandler,
                                    ClassroomHandshakeInterceptor classroomHandshakeInterceptor,
                                    AlethicodeProperties properties) {
        this.classroomCollabWebSocketHandler = classroomCollabWebSocketHandler;
        this.classroomMonitorWebSocketHandler = classroomMonitorWebSocketHandler;
        this.classroomHandshakeInterceptor = classroomHandshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketOriginConfigurer.apply(
                registry.addHandler(classroomCollabWebSocketHandler, "/ws/classroom/collab/*")
                        .addInterceptors(classroomHandshakeInterceptor),
                properties);
        WebSocketOriginConfigurer.apply(
                registry.addHandler(classroomMonitorWebSocketHandler, "/ws/classroom/monitor/*")
                        .addInterceptors(classroomHandshakeInterceptor),
                properties);
    }
}
