package com.alethicode.service.rag.dto;

import java.util.Map;

/**
 * {@code POST /v1/rag/index/{entity_type}} 请求体。
 *
 * <p>字段语义对齐 {@code services/alethicode-rag/app/schemas.py::IndexRequest}。</p>
 */
public record RagIndexRequest(
        String entityId,
        String content,
        Map<String, Object> metadata
) {
}
