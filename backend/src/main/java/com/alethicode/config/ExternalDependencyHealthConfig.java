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
 * 在 {@code /actuator/health/readiness} 暴露非 JVM 依赖状态。
 *
 * <p>DB 与 Redis 已由 Spring Boot 自动探测；这里补充 tutor_graph，避免导学工作流不可用时
 * 编排器仍判定服务 ready。</p>
 */
@Configuration
public class ExternalDependencyHealthConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalDependencyHealthConfig.class);

    /**
     * 使用 2 秒超时探测 tutor-graph，避免 readiness 检查挂起。
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
                        // 不回显异常消息，避免泄露内部 URL。
                        .build();
            }
        };
    }
}
