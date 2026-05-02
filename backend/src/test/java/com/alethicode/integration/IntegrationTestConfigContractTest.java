package com.alethicode.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTestConfigContractTest {

    @Test
    void integrationTestsShouldHaveDedicatedApplicationConfig() throws Exception {
        Path configPath = Path.of("src", "test", "resources", "application.yml");

        assertThat(Files.exists(configPath)).isTrue();
        String source = Files.readString(configPath);
        assertThat(source).contains("127.0.0.1:5435");
        assertThat(source).contains("test_aethicode");
    }
}
