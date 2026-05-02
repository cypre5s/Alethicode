package com.alethicode.service.ai;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Centralized Resilience4j execution chain for outbound LLM calls.
 *
 * <p>All provider-facing operations run through the same `circuit breaker + retry +
 * bulkhead + time limiter` pipeline so policy is configured once in Spring config
 * instead of being reimplemented inside the gateway.
 */
@Component
public class AiCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(AiCircuitBreaker.class);
    private static final String INSTANCE_NAME = "llmProvider";

    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final BulkheadRegistry bulkheads;
    private final TimeLimiterRegistry timeLimiters;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduler;
    private final Set<String> loggingRegistered = ConcurrentHashMap.newKeySet();

    private MeterRegistry meterRegistry;

    @Autowired
    public AiCircuitBreaker(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            BulkheadRegistry bulkheads,
            TimeLimiterRegistry timeLimiters
    ) {
        this(circuitBreakers, retries, bulkheads, timeLimiters,
                Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2)));
    }

    AiCircuitBreaker(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            BulkheadRegistry bulkheads,
            TimeLimiterRegistry timeLimiters,
            ScheduledExecutorService scheduler
    ) {
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.bulkheads = bulkheads;
        this.timeLimiters = timeLimiters;
        this.circuitBreaker = circuitBreakers.circuitBreaker(INSTANCE_NAME);
        this.retry = retries.retry(INSTANCE_NAME);
        this.bulkhead = bulkheads.bulkhead(INSTANCE_NAME);
        this.timeLimiter = timeLimiters.timeLimiter(INSTANCE_NAME);
        this.scheduler = scheduler;
        registerCircuitBreakerLogging(INSTANCE_NAME, this.circuitBreaker);
    }

    AiCircuitBreaker(
            CircuitBreaker circuitBreaker,
            Retry retry,
            Bulkhead bulkhead,
            TimeLimiter timeLimiter,
            ScheduledExecutorService scheduler
    ) {
        this.circuitBreakers = null;
        this.retries = null;
        this.bulkheads = null;
        this.timeLimiters = null;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.bulkhead = bulkhead;
        this.timeLimiter = timeLimiter;
        this.scheduler = scheduler;
        registerCircuitBreakerLogging(INSTANCE_NAME, circuitBreaker);
    }

    @Autowired(required = false)
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T execute(String operation, CheckedSupplier<T> supplier) throws Exception {
        return executeWithComponents(INSTANCE_NAME, operation, supplier, circuitBreaker, retry, bulkhead, timeLimiter);
    }

    public <T> T executeWithInstance(String instanceName, String operation, CheckedSupplier<T> supplier) throws Exception {
        String dependencyName = sanitizeDependency(instanceName);
        if (INSTANCE_NAME.equals(dependencyName)) {
            return execute(operation, supplier);
        }
        if (circuitBreakers == null || retries == null || bulkheads == null || timeLimiters == null) {
            throw new IllegalStateException("Named outbound dependency execution requires registry-backed AiCircuitBreaker");
        }
        CircuitBreaker namedCircuitBreaker = circuitBreakers.circuitBreaker(dependencyName);
        Retry namedRetry = retries.retry(dependencyName);
        Bulkhead namedBulkhead = bulkheads.bulkhead(dependencyName);
        TimeLimiter namedTimeLimiter = timeLimiters.timeLimiter(dependencyName);
        registerCircuitBreakerLogging(dependencyName, namedCircuitBreaker);
        return executeWithComponents(dependencyName, operation, supplier, namedCircuitBreaker, namedRetry, namedBulkhead, namedTimeLimiter);
    }

    private <T> T executeWithComponents(String dependencyName,
                                        String operation,
                                        CheckedSupplier<T> supplier,
                                        CircuitBreaker circuitBreaker,
                                        Retry retry,
                                        Bulkhead bulkhead,
                                        TimeLimiter timeLimiter) throws Exception {
        long startNanos = System.nanoTime();
        String outcome = "failure";
        try {
            Callable<T> decorated = decorate(supplier, circuitBreaker, retry, bulkhead, timeLimiter);
            T result = decorated.call();
            outcome = "success";
            recordOutcome("success", dependencyName);
            return result;
        } catch (Throwable throwable) {
            recordOutcome("failure", dependencyName);
            throw unwrap(throwable, dependencyName);
        } finally {
            recordDuration(startNanos, outcome, operation, dependencyName);
        }
    }

    private <T> Callable<T> decorate(CheckedSupplier<T> supplier,
                                     CircuitBreaker circuitBreaker,
                                     Retry retry,
                                     Bulkhead bulkhead,
                                     TimeLimiter timeLimiter) {
        Callable<T> timeLimited = TimeLimiter.decorateFutureSupplier(
                timeLimiter,
                () -> scheduler.submit(() -> invokeSupplier(supplier))
        );
        Callable<T> bulkheaded = Bulkhead.decorateCallable(bulkhead, timeLimited);
        Callable<T> retried = Retry.decorateCallable(retry, bulkheaded);
        return CircuitBreaker.decorateCallable(circuitBreaker, retried);
    }

    private <T> T invokeSupplier(CheckedSupplier<T> supplier) throws Exception {
        try {
            return supplier.get();
        } catch (Exception exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new CompletionException(throwable);
        }
    }

    private Exception unwrap(Throwable throwable, String dependencyName) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        if (current instanceof Exception exception) {
            return exception;
        }
        if (current instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(sanitizeDependency(dependencyName) + " execution failed", current);
    }

    private void registerCircuitBreakerLogging(String dependencyName, CircuitBreaker circuitBreaker) {
        if (!loggingRegistered.add(dependencyName)) {
            return;
        }
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition transition = event.getStateTransition();
            if (transition.getToState() == CircuitBreaker.State.OPEN) {
                log.warn("AI circuit breaker OPENED — dependency={}, transition={}", dependencyName, transition);
                if (meterRegistry != null) {
                    meterRegistry.counter("ai.dependency.circuit_breaker.opens", "dependency", dependencyName).increment();
                }
                return;
            }
            if (transition.getToState() == CircuitBreaker.State.CLOSED) {
                log.info("AI circuit breaker CLOSED — dependency={}, transition={}", dependencyName, transition);
            }
        });
    }

    private void recordOutcome(String outcome, String dependencyName) {
        if (meterRegistry != null) {
            meterRegistry.counter("ai.outbound.calls", Tags.of("dependency", dependencyName, "outcome", outcome)).increment();
        }
    }

    private void recordDuration(long startNanos, String outcome, String operation, String dependencyName) {
        if (meterRegistry == null) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        meterRegistry.timer(
                        "ai.outbound.call.duration",
                        Tags.of(
                                "dependency", dependencyName,
                                "outcome", outcome,
                                "operation", sanitizeOperation(operation)
                        )
                )
                .record(Duration.ofMillis(durationMs));
    }

    private String sanitizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return "unknown";
        }
        return operation;
    }

    private String sanitizeDependency(String dependencyName) {
        if (dependencyName == null || dependencyName.isBlank()) {
            throw new IllegalArgumentException("dependency name is required");
        }
        return dependencyName.strip();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
