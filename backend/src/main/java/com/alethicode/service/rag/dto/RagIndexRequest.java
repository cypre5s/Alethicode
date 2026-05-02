package com.alethicode.service.rag.dto;

import java.util.Map;

/**
 * Body sent to {@code POST /v1/rag/index/{entity_type}}.
 *
 * <p>Mirrors {@code services/alethicode-rag/app/schemas.py::IndexRequest}.
 * Field names are spelled in snake_case at the JSON level via Jackson's
 * default property naming for records (we let the {@code ObjectMapper}
 * configured globally in {@code AppConfig} apply the snake_case naming).
 */
public record RagIndexRequest(
        String entityId,
        String content,
        Map<String, Object> metadata
) {
}
