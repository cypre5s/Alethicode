package com.alethicode.service.aitutor.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact courseware (language pack) reference summary used as AI tutor chat evidence
 * for {@code @courseware:<lpId>} resolution.
 *
 * <p>Holds the top-k retrieved page chunks scoped to the current question so the LLM
 * can ground its answer in the specific courseware the student opted-in to.</p>
 *
 * <p>Design: <code>docs/plans/2026-05-03-courseware-reference-token-design.md</code></p>
 */
public record CoursewareSummary(
        Long languagePackId,
        String packName,
        List<RetrievedChunk> chunks,
        Instant retrievedAt
) {

    public CoursewareSummary {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    /** Compact JSON map for HTTP / prompt injection (snake_case keys to match tutor_graph). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("language_pack_id", languagePackId);
        m.put("pack_name", packName == null ? "" : packName);
        m.put("retrieved_at", retrievedAt == null ? "" : retrievedAt.toString());
        List<Map<String, Object>> chunkMaps = new ArrayList<>(chunks.size());
        for (RetrievedChunk chunk : chunks) {
            chunkMaps.add(chunk.toMap());
        }
        m.put("chunks", chunkMaps);
        return m;
    }

    /**
     * One retrieved page chunk with its location for citation-friendly LLM output.
     *
     * @param documentId   PDF document id within the language pack
     * @param documentTitle human-readable document title
     * @param pageNumber   page number inside the document (1-indexed)
     * @param text         the actual chunk text used as LLM context
     * @param score        the retrieval relevance score (higher is better, comparable within
     *                     a single retrieval call only)
     */
    public record RetrievedChunk(
            Long documentId,
            String documentTitle,
            Integer pageNumber,
            String text,
            double score
    ) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("document_id", documentId);
            m.put("document_title", documentTitle == null ? "" : documentTitle);
            m.put("page_number", pageNumber);
            m.put("text", text == null ? "" : text);
            m.put("score", score);
            return m;
        }
    }
}
