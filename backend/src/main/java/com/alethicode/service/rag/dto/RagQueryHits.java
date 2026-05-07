package com.alethicode.service.rag.dto;

import java.util.List;
import java.util.Map;

/**
 * {@code /v1/rag/query/*} 端点返回的结构化命中结果。
 *
 * <p>{@code rawContext} 承载 LightRAG 在 {@code only_need_context=true} 时返回的
 * mix-mode markdown；在 Python 服务解析该 blob 前，结构化集合可能为空。</p>
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
