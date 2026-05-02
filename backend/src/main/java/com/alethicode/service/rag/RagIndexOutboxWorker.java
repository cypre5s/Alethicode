package com.alethicode.service.rag;

import com.alethicode.service.rag.dto.RagEntityType;
import com.alethicode.service.rag.dto.RagIndexAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drains {@code rag_index_outbox} and forwards each pending row to
 * alethicode-rag.
 *
 * <p>Backoff and give-up policy:
 * <ul>
 *   <li>Each failure increments {@code attempts} and schedules
 *       {@code next_retry_at = now() + min(60s × 2^(attempts-1), 1h)}.</li>
 *   <li>After {@link #MAX_ATTEMPTS} failures the row is parked with
 *       {@code given_up_at = now()} and a Micrometer counter
 *       {@code rag_outbox_giveup_total} is incremented for alerting.</li>
 *   <li>Successful calls set {@code indexed_at = now()} so the row is no
 *       longer picked up.</li>
 * </ul>
 *
 * <p>Spring's {@code @EnableScheduling} is intentionally not enabled
 * project-wide (see {@code KcCoverageRegistry} for the rationale), so we
 * self-host a single-threaded {@link ScheduledExecutorService} the same
 * way that class does. The worker batch-fetches up to {@link #BATCH_SIZE}
 * pending rows per tick and processes them sequentially; if a single
 * row's HTTP call takes a long time (LightRAG insert can be 60-90s for
 * one chunk), the rest of the batch waits — that's fine because index
 * throughput is gated by alethicode-rag itself anyway.
 */
@Component
public class RagIndexOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(RagIndexOutboxWorker.class);

    static final int BATCH_SIZE = 100;
    static final int MAX_ATTEMPTS = 5;
    static final Duration BACKOFF_BASE = Duration.ofSeconds(60);
    static final Duration BACKOFF_CAP = Duration.ofHours(1);

    private final JdbcTemplate jdbcTemplate;
    private final RagServiceClient ragServiceClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long fixedDelayMs;
    private final Counter giveUpCounter;
    private final Counter successCounter;
    private final Counter failureCounter;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> drainTask;

    public RagIndexOutboxWorker(
            JdbcTemplate jdbcTemplate,
            RagServiceClient ragServiceClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${alethicode.rag.outbox.enabled:true}") boolean enabled,
            @Value("${alethicode.rag.outbox.fixed-delay-ms:30000}") long fixedDelayMs
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragServiceClient = ragServiceClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.fixedDelayMs = Math.max(1_000L, fixedDelayMs);
        this.giveUpCounter = Counter.builder("rag_outbox_giveup_total")
                .description("rag_index_outbox rows that exhausted retries and were parked")
                .register(meterRegistry);
        this.successCounter = Counter.builder("rag_outbox_success_total")
                .description("rag_index_outbox rows successfully forwarded to alethicode-rag")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("rag_outbox_failure_total")
                .description("rag_index_outbox rows that failed but will retry")
                .register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("RagIndexOutboxWorker disabled by alethicode.rag.outbox.enabled=false");
            return;
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rag-outbox-drain");
            t.setDaemon(true);
            return t;
        });
        this.drainTask = scheduler.scheduleWithFixedDelay(
                this::safeDrain, fixedDelayMs, fixedDelayMs, TimeUnit.MILLISECONDS
        );
        log.info("RagIndexOutboxWorker started; fixedDelayMs={} batchSize={} maxAttempts={}",
                fixedDelayMs, BATCH_SIZE, MAX_ATTEMPTS);
    }

    @PreDestroy
    public void shutdown() {
        if (drainTask != null) {
            drainTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safeDrain() {
        try {
            int processed = drainOnce();
            if (processed > 0) {
                log.debug("rag_outbox drain processed={}", processed);
            }
        } catch (RuntimeException ex) {
            // Top-level guard — never let the scheduler thread die. Individual row
            // failures are already isolated below.
            log.warn("rag_outbox drain unexpected failure", ex);
        }
    }

    int drainOnce() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, entity_type, entity_id, action, payload::text AS payload_json, attempts
                FROM rag_index_outbox
                WHERE indexed_at IS NULL
                  AND given_up_at IS NULL
                  AND next_retry_at <= now()
                ORDER BY next_retry_at ASC
                LIMIT ?
                """,
                BATCH_SIZE
        );
        for (Map<String, Object> row : rows) {
            processRow(row);
        }
        return rows.size();
    }

    private void processRow(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        String entityType = (String) row.get("entity_type");
        String entityId = (String) row.get("entity_id");
        String action = (String) row.get("action");
        int attempts = ((Number) row.get("attempts")).intValue();

        try {
            if (RagIndexAction.INDEX.name().equals(action)) {
                Map<String, Object> payload = parsePayload((String) row.get("payload_json"));
                String content = stringField(payload, "content");
                Map<String, Object> metadata = mapField(payload, "metadata");
                ragServiceClient.indexNow(RagEntityType.fromSlug(entityType), entityId, content, metadata);
            } else if (RagIndexAction.DELETE.name().equals(action)) {
                ragServiceClient.deleteNow(RagEntityType.fromSlug(entityType), entityId);
            } else {
                throw new IllegalStateException("Unknown rag_index_outbox.action: " + action);
            }
            markSuccess(id);
            successCounter.increment();
        } catch (RuntimeException ex) {
            int newAttempts = attempts + 1;
            if (newAttempts >= MAX_ATTEMPTS) {
                markGivenUp(id, ex.getMessage());
                giveUpCounter.increment();
                log.warn(
                        "rag_outbox give-up id={} type={} entity={} attempts={} cause={}",
                        id, entityType, entityId, newAttempts, ex.getMessage()
                );
            } else {
                long backoffSeconds = Math.min(
                        BACKOFF_BASE.toSeconds() * (1L << Math.min(newAttempts - 1, 30)),
                        BACKOFF_CAP.toSeconds()
                );
                markFailedRetry(id, newAttempts, backoffSeconds, ex.getMessage());
                failureCounter.increment();
                log.debug(
                        "rag_outbox retry id={} type={} entity={} attempts={} backoffSeconds={} cause={}",
                        id, entityType, entityId, newAttempts, backoffSeconds, ex.getMessage()
                );
            }
        }
    }

    private void markSuccess(long id) {
        try {
            jdbcTemplate.update(
                    "UPDATE rag_index_outbox SET indexed_at = now(), updated_at = now(), last_error = NULL WHERE id = ?",
                    id
            );
        } catch (DataAccessException ex) {
            log.warn("rag_outbox markSuccess failed id={}: {}", id, ex.getMessage());
        }
    }

    private void markFailedRetry(long id, int attempts, long backoffSeconds, String error) {
        try {
            jdbcTemplate.update(
                    """
                    UPDATE rag_index_outbox
                    SET attempts = ?, last_error = ?, next_retry_at = now() + (interval '1 second') * ?, updated_at = now()
                    WHERE id = ?
                    """,
                    attempts, truncate(error, 1000), backoffSeconds, id
            );
        } catch (DataAccessException ex) {
            log.warn("rag_outbox markFailedRetry failed id={}: {}", id, ex.getMessage());
        }
    }

    private void markGivenUp(long id, String error) {
        try {
            jdbcTemplate.update(
                    """
                    UPDATE rag_index_outbox
                    SET attempts = ?, last_error = ?, given_up_at = now(), updated_at = now()
                    WHERE id = ?
                    """,
                    MAX_ATTEMPTS, truncate(error, 1000), id
            );
        } catch (DataAccessException ex) {
            log.warn("rag_outbox markGivenUp failed id={}: {}", id, ex.getMessage());
        }
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("rag_outbox payload parse failed: " + ex.getMessage(), ex);
        }
    }

    private static String stringField(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalStateException("rag_outbox payload missing string field: " + key);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapField(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalStateException("rag_outbox payload field is not an object: " + key);
    }

    private static String truncate(String input, int max) {
        if (input == null) {
            return null;
        }
        return input.length() <= max ? input : input.substring(0, max);
    }
}
