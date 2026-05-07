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
 * 面向业务代码的 RAG outbox 写入入口。
 *
 * <p>调用约束：
 * <ol>
 *   <li>调用方先在自身事务内写业务表。</li>
 *   <li>同一事务内调用 {@link #enqueueIndex} 或 {@link #enqueueDelete}。</li>
 *   <li>业务提交成功时 outbox 行一起提交；业务回滚时 outbox 自动回滚。</li>
 * </ol>
 *
 * <p>{@code (entity_type, entity_id, action)} 唯一约束保证幂等，同一业务对象的重复索引请求会合并为
 * 带最新 payload 的一条待处理记录。</p>
 */
@Service
public class RagIndexQueueService {

    private static final Logger log = LoggerFactory.getLogger(RagIndexQueueService.class);

    /**
     * 供测试夹具和无 Spring 容器的管理端便捷路径使用的单例空实现。
     *
     * 生产流量必须经过 Spring 托管 bean。
     */
    public static final RagIndexQueueService NOOP = new RagIndexQueueService(null, null) {
        @Override
        public void enqueueIndex(RagEntityType entityType, String entityId, String content, Map<String, Object> metadata) {
        }

        @Override
        public void enqueueDelete(RagEntityType entityType, String entityId) {
        }

        @Override
        public void enqueueIndex(RagEntityType entityType, String entityId, String content) {
        }
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RagIndexQueueService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 入队 INDEX 请求，并将同一业务对象的重复请求合并为单条待处理行。
     *
     * <p>存在外层事务时加入外层事务；没有外层事务时由 Spring 为本次插入开启短事务。</p>
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
     * 仅包含内容的 INDEX 入队便捷方法。
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
