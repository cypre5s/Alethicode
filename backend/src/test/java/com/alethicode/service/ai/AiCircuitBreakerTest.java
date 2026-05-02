package com.alethicode.service.ai;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCircuitBreakerTest {

    private ScheduledExecutorService scheduler;
    private AiCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(2);
        breaker = new AiCircuitBreaker(
                CircuitBreakerRegistry.of(
                        CircuitBreakerConfig.custom()
                                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                .slidingWindowSize(2)
                                .minimumNumberOfCalls(2)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofMillis(250))
                                .build()
                ),
                RetryRegistry.of(
                        RetryConfig.custom()
                                .maxAttempts(2)
                                .waitDuration(Duration.ofMillis(5))
                                .retryExceptions(IOException.class, TimeoutException.class)
                                .build()
                ),
                BulkheadRegistry.of(
                        BulkheadConfig.custom()
                                .maxConcurrentCalls(1)
                                .maxWaitDuration(Duration.ZERO)
                                .build()
                ),
                TimeLimiterRegistry.of(
                        TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofMillis(50))
                                .cancelRunningFuture(true)
                                .build()
                ),
                scheduler
        );
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void executeShouldRetryTransientIoFailures() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        String result = breaker.execute("json", () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("upstream unavailable");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void executeShouldFailFastWhenTimeLimiterExpires() {
        assertThatThrownBy(() -> breaker.execute("json", () -> {
            Thread.sleep(200);
            return "slow";
        }))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("llmProvider");
    }

    @Test
    void executeShouldRejectWhenBulkheadIsSaturated() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> inFlight = CompletableFuture.supplyAsync(() -> {
            try {
                return breaker.execute("json", () -> {
                    entered.countDown();
                    release.await(1, TimeUnit.SECONDS);
                    return "first";
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> breaker.execute("json", () -> "second"))
                .isInstanceOf(BulkheadFullException.class);

        release.countDown();
        assertThat(inFlight.get(1, TimeUnit.SECONDS)).isEqualTo("first");
    }

    @Test
    void executeShouldOpenCircuitAfterRepeatedFailures() {
        assertThatThrownBy(() -> breaker.execute("json", () -> {
            throw new IOException("boom-1");
        })).isInstanceOf(IOException.class);

        assertThatThrownBy(() -> breaker.execute("json", () -> {
            throw new IOException("boom-2");
        })).isInstanceOf(IOException.class);

        assertThatThrownBy(() -> breaker.execute("json", () -> "never"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void executeWithInstanceShouldUseNamedDependencyPipeline() throws Exception {
        AiCircuitBreaker namedBreaker = new AiCircuitBreaker(
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()),
                RetryRegistry.of(RetryConfig.ofDefaults()),
                BulkheadRegistry.of(BulkheadConfig.ofDefaults()),
                TimeLimiterRegistry.of(TimeLimiterConfig.ofDefaults()),
                scheduler
        );

        String result = namedBreaker.executeWithInstance("judgeServer", "judge ping", () -> "pong");

        assertThat(result).isEqualTo("pong");
    }
}
