package com.alethicode.service.ai;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiCircuitBreakerContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, AiCircuitBreaker.class);

    @Test
    void shouldCreateAiCircuitBreakerBeanFromResilienceRegistries() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiCircuitBreaker.class);
            assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
            assertThat(context).hasSingleBean(RetryRegistry.class);
            assertThat(context).hasSingleBean(BulkheadRegistry.class);
            assertThat(context).hasSingleBean(TimeLimiterRegistry.class);
        });
    }

    @Configuration
    static class TestConfig {

        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() {
            return CircuitBreakerRegistry.of(
                    CircuitBreakerConfig.custom()
                            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                            .slidingWindowSize(2)
                            .minimumNumberOfCalls(2)
                            .failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofMillis(250))
                            .build()
            );
        }

        @Bean
        RetryRegistry retryRegistry() {
            return RetryRegistry.of(
                    RetryConfig.custom()
                            .maxAttempts(2)
                            .waitDuration(Duration.ofMillis(5))
                            .build()
            );
        }

        @Bean
        BulkheadRegistry bulkheadRegistry() {
            return BulkheadRegistry.of(
                    BulkheadConfig.custom()
                            .maxConcurrentCalls(1)
                            .maxWaitDuration(Duration.ZERO)
                            .build()
            );
        }

        @Bean
        TimeLimiterRegistry timeLimiterRegistry() {
            return TimeLimiterRegistry.of(
                    TimeLimiterConfig.custom()
                            .timeoutDuration(Duration.ofMillis(50))
                            .cancelRunningFuture(true)
                            .build()
            );
        }
    }
}
