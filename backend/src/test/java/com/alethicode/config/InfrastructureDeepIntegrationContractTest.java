package com.alethicode.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureDeepIntegrationContractTest {

    @Test
    void backendDefaultsShouldRouteJudgeDispatchThroughNats() throws Exception {
        String application = Files.readString(repoRoot().resolve("backend/src/main/resources/application.yml"));

        assertThat(application).contains("transport: ${JUDGE_DISPATCH_TRANSPORT:nats}");
        assertThat(application).contains("nats-url: ${NATS_URL:nats://127.0.0.1:4222}");
        assertThat(application).contains("enabled: ${TEMPORAL_ENABLED:true}");
        assertThat(application).contains("target: ${TEMPORAL_TARGET:127.0.0.1:7233}");
    }

    @Test
    void tutorWorkflowRateLimitShouldAllowOneHundredRequestsPerSecond() throws Exception {
        String application = Files.readString(repoRoot().resolve("backend/src/main/resources/application.yml"));

        assertThat(application).contains("""
      tutorWorkflow:
        base-config: default
        limit-for-period: 100
""");
    }

    @Test
    void localStartupShouldStartTemporalAndPassItsTargetToBackend() throws Exception {
        String startScript = Files.readString(repoRoot().resolve("start.sh"));
        String compose = Files.readString(repoRoot().resolve("deploy/docker-compose.yml"));

        assertThat(compose).contains("temporal:");
        assertThat(compose).contains("nc -z 127.0.0.1 4222");
        assertThat(compose).contains("DB: postgres12");
        assertThat(compose).contains("DYNAMIC_CONFIG_FILE_PATH: config/dynamicconfig/docker.yaml");
        assertThat(compose).contains("HTTP_PROXY: \"\"");
        assertThat(compose).contains("hostname -i):7233");
        assertThat(startScript).contains("ensure_compose_service_running temporal");
        assertThat(startScript).contains("up -d --no-deps \"$service_name\"");
        assertThat(startScript).contains("docker network connect --alias \"$service_name\" --alias \"$container_name\"");
        assertThat(startScript).contains("remove_nats_with_stale_healthcheck");
        assertThat(startScript).contains("remove_temporal_with_stale_config");
        assertThat(startScript).contains("ensure_compose_service_running jaeger");
        assertThat(startScript).contains("LOCAL_OTEL_EXPORTER_OTLP_ENDPOINT:-http://127.0.0.1:4318/v1/traces");
        assertThat(startScript).contains("TEMPORAL_ENABLED=\"${TEMPORAL_ENABLED:-true}\"");
        assertThat(startScript).contains("TEMPORAL_TARGET=\"${TEMPORAL_TARGET:-127.0.0.1:${TEMPORAL_PORT}}\"");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (Files.exists(current.resolve("backend/pom.xml"))) {
            return current;
        }
        if (Files.exists(current.resolve("pom.xml")) && "backend".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        throw new IllegalStateException("Cannot locate Alethicode repository root from " + current);
    }
}
