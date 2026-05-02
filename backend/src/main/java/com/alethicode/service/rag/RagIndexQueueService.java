package com.alethicode.service.rag;

import com.alethicode.service.rag.dto.RagEntityType;
import com.alethicode.service.rag.dto.RagIndexAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application-facing entry point that writes a row to {@code rag_index_outbox}.
 *
 * <p>The contract:
 * <ol>
 *   <li>Caller writes the business table (page / notebook / memory) inside its own
 *       transaction.</li>
 *   <li>Same transaction calls {@link #enqueueIndex} or {@link #enqueueDelete}.</li>
 *   <li>If the business commit succeeds, the outbox row is committed alongside it
 *       and {@link RagIndexOutboxWorker} picks it up on its next tick. If the
 *       business commit fails, the outbox row is rolled back automatically.
 *       Result: the index never gets ahead of the business state, and the
 *       business write never depends on alethicode-rag being up.</li>
 * </ol>
 *
 * <p>Idempotency is provided by the unique constraint on
 * {@code (entity_type, entity_id, action)}; calling enqueueIndex twice for the
 * same row coalesces into a single pending entry with the latest payload.
 */
@Service
public class RagIndexQueueService {

    private static final Logger log = LoggerFactory.getLogger(RagIndexQueueService.class);

    /**
     * Singleton no-op used by manual {@code new}-instantiated services in
     * test fixtures and the workflow admin convenience path that lacks a
     * Spring bean container. Production traffic always goes through the
     * Spring-managed bean, never this NOOP.
     */
    public static final RagIndexQueueService NOOP = new RagIndexQueueService(null, null) {
        @Override
        public void enqueueIndex(RagEntityType entityType, String entityId, String content, Map<String, Object> metadata) {
            // intentionally no-op
        }

        @Override
        public void enqueueDelete(RagEntityType entityType, String entityId) {
            // intentionally no-op
        }

        @Override
        public void enqueueIndex(RagEntityType entityType, String entityId, String content) {
            // intentionally no-op
        }
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RagIndexQueueService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueue an INDEX request. Always coalesces into a single pending row
     * keyed by (entity_type, entity_id, INDEX): repeated calls for the same
     * business object overwrite the payload, reset attempts, and reset the
     * indexed/given-up markers so the next worker pass re-indexes with the
     * latest content.
     *
     * <p>Joins the surrounding transaction when present; if the caller is
     * not transactional, Spring opens a short tx for this single insert.
     * Either way, a business write that rolls back also rolls back the
     * outbox row, so the index never gets ahead of business state.
     */
    @Transactional
    public void enqueueIndex(RagEntityType entityType, String entityId, String content, Map<String, Object> metadata) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("metadata", metadata == null ? Map.of() : metadata);
        payload.put("entity_id", entityId);

        String payloadJson = serialize(payload);

        int updated = jdbcTemplate.update(
                """
                INSERT INTO rag_index_outbox(entity_type, entity_id, action, payload, attempts,
                                             last_error, next_retry_at, indexed_at, given_up_at,
                                             created_at, updated_at)
                VALUES (?, ?, 'INDEX', cast(? as jsonb), 0,
                        NULL, now(), NULL, NULL,
                        now(), now())
                ON CONFLICT (entity_type, entity_id, action) DO UPDATE
                SET payload = excluded.payload,
                    attempts = 0,
                    last_error = NULL,
                    next_retry_at = now(),
                    indexed_at = NULL,
                    given_up_at = NULL,
                    updated_at = now()
                """,
                entityType.slug(), entityId, payloadJson
        );
        log.debug("rag_outbox enqueueIndex type={} id={} updated_rows={}", entityType.slug(), entityId, updated);
    }

    @Transactional
    public void enqueueDelete(RagEntityType entityType, String entityId) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId is required");
        }
        jdbcTemplate.update(
                """
                INSERT INTO rag_index_outbox(entity_type, entity_id, action, payload, attempts,
                                             last_error, next_retry_at, indexed_at, given_up_at,
                                             created_at, updated_at)
                VALUES (?, ?, 'DELETE', '{}'::jsonb, 0,
                        NULL, now(), NULL, NULL,
                        now(), now())
                ON CONFLICT (entity_type, entity_id, action) DO UPDATE
                SET attempts = 0,
                    last_error = NULL,
                    next_retry_at = now(),
                    indexed_at = NULL,
                    given_up_at = NULL,
                    updated_at = now()
                """,
                entityType.slug(), entityId
        );
        log.debug("rag_outbox enqueueDelete type={} id={}", entityType.slug(), entityId);
    }

    /**
     * Convenience for {@link RagIndexAction#INDEX} only. Used by a few callers
     * (DocumentParsingServiceImpl) where the metadata block is built ad-hoc.
     */
    public void enqueueIndex(RagEntityType entityType, String entityId, String content) {
        enqueueIndex(entityType, entityId, content, Map.of());
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("rag_outbox payload serialize failed", ex);
        }
    }
}
