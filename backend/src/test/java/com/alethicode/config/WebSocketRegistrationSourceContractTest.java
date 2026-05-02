package com.alethicode.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketRegistrationSourceContractTest {

    @Test
    void websocketConfigsAndHandlersShouldNotDependOnConditionalJdbcTemplateRegistration() throws IOException {
        assertThat(readSource("src/main/java/com/alethicode/config/WorkflowWebSocketConfig.java"))
                .doesNotContain("@ConditionalOnBean(JdbcTemplate.class)");
        assertThat(readSource("src/main/java/com/alethicode/config/ClassroomWebSocketConfig.java"))
                .doesNotContain("@ConditionalOnBean(JdbcTemplate.class)");
        assertThat(readSource("src/main/java/com/alethicode/websocket/WorkflowWebSocketHandler.java"))
                .doesNotContain("@ConditionalOnBean(JdbcTemplate.class)");
        assertThat(readSource("src/main/java/com/alethicode/websocket/ClassroomCollabWebSocketHandler.java"))
                .doesNotContain("@ConditionalOnBean(JdbcTemplate.class)");
        assertThat(readSource("src/main/java/com/alethicode/websocket/ClassroomMonitorWebSocketHandler.java"))
                .doesNotContain("@ConditionalOnBean(JdbcTemplate.class)");
        assertThat(readSource("src/main/java/com/alethicode/websocket/ClassroomWebSocketSupport.java"))
                .doesNotContain("@ConditionalOnBean(JdbcTemplate.class)");
    }

    private String readSource(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
