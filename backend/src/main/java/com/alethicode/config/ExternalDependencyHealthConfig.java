package com.alethicode.config;

import com.alethicode.service.aitutor.graph.TutorGraphClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Expose the status of non-JVM dependencies on {@code /actuator/health/readiness}.
 * The default Spring Boot probes only cover DB + Redis; we also need to know
 * whether {@code tutor_graph} is reachable, otherwise an orchestrator will
 * declare the pod ready even though every tutor workflow request would 503.
 *
 * <p>DB and Redis health indicators are already contributed by Spring Boot's
 * auto-configuration via {@code DataSourceHealthIndicator} / {@code RedisHealthIndicator}
 * and included in the {@code readiness} group via {@code management.endpoint.health.group}
 * configuration in {@code application.yml}.
 */
@Configuration
public class ExternalDependencyHealthConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalDependencyHealthConfig.class);

    /**
     * tutor-graph health probe. Uses the existing {@link TutorGraphClient#health()}
     * call with a tight 2-second timeout so the readiness probe never hangs.
     */
    @Bean
    HealthIndicator tutorGraphHealthIndicator(TutorGraphClient graphClient) {
        return () -> {
            long start = System.currentTimeMillis();
            try {
                graphClient.health().block(Duration.ofSeconds(2));
                long latency = System.currentTimeMillis() - start;
                return Health.up()
                        .withDetail("latency_ms", latency)
                        .build();
            } catch (Exception e) {
                log.debug("tutor-graph health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getClass().getSimpleName())
                        // intentionally not echoing e.getMessage() to avoid leaking URLs
                        .build();
            }
        };
    }
}
