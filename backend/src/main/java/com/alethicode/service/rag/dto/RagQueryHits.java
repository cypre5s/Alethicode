package com.alethicode.service.rag.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured hits returned by every {@code /v1/rag/query/*} endpoint.
 *
 * <p>{@code rawContext} carries LightRAG's mix-mode markdown blob when
 * {@code only_need_context=true}; the structured collections may be
 * empty until alethicode-rag adds server-side parsing of that blob (see
 * Phase 1 CHANGELOG range note).
 */
public record RagQueryHits(
        List<RetrievedEntity> entities,
        List<RetrievedRelation> relations,
        List<RetrievedChunk> chunks,
        String rawContext
) {
    public RagQueryHits {
        entities = entities == null ? List.of() : entities;
        relations = relations == null ? List.of() : relations;
        chunks = chunks == null ? List.of() : chunks;
    }

    public record RetrievedEntity(
            String entityId,
            String entityType,
            String description
    ) {
    }

    public record RetrievedRelation(
            String srcId,
            String tgtId,
            String description,
            List<String> keywords
    ) {
        public RetrievedRelation {
            keywords = keywords == null ? List.of() : keywords;
        }
    }

    public record RetrievedChunk(
            String chunkId,
            String content,
            Double score,
            Map<String, Object> metadata
    ) {
        public RetrievedChunk {
            metadata = metadata == null ? Map.of() : metadata;
        }
    }
}
