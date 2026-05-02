package com.alethicode.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagRebuildService {

    private static final Logger log = LoggerFactory.getLogger(RagRebuildService.class);

    private final JdbcTemplate jdbcTemplate;
    private final RagServiceClient ragServiceClient;

    public RagRebuildService(JdbcTemplate jdbcTemplate, RagServiceClient ragServiceClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragServiceClient = ragServiceClient;
    }

    @Transactional
    public RebuildResult rebuildPack(Long languagePackId) {
        int outboxReset = jdbcTemplate.update(
                """
                UPDATE rag_index_outbox o
                SET attempts = 0,
                    last_error = NULL,
                    given_up_at = NULL,
                    indexed_at = NULL,
                    next_retry_at = now(),
                    updated_at = now()
                FROM language_pack_page p
                WHERE o.entity_type = 'courseware_page'
                  AND o.entity_id = p.id::text
                  AND p.language_pack_id = ?
                  AND (
                    o.given_up_at IS NOT NULL
                    OR EXISTS (
                        SELECT 1 FROM lightrag_doc_status s
                        WHERE s.track_id = 'courseware_page:' || o.entity_id
                          AND s.status = 'failed'
                    )
                    OR EXISTS (
                        SELECT 1 FROM lightrag_doc_status s
                        WHERE s.track_id = 'courseware_page:' || o.entity_id
                          AND s.status = 'processing'
                          AND s.updated_at < now() - interval '1 hour'
                    )
                  )
                """,
                languagePackId
        );

        int docStatusDropped = 0;
        try {
            docStatusDropped = jdbcTemplate.update(
                    """
                    DELETE FROM lightrag_doc_status
                    WHERE track_id IN (
                        SELECT 'courseware_page:' || p.id::text
                        FROM language_pack_page p
                        WHERE p.language_pack_id = ?
                    ) AND (
                        status = 'failed'
                        OR (status = 'processing' AND updated_at < now() - interval '1 hour')
                    )
                    """,
                    languagePackId
            );
        } catch (Exception ex) {
            log.warn("Failed to clean lightrag_doc_status: {}", ex.getMessage());
        }

        ragServiceClient.wakeUpPipeline();

        log.info("Rebuilt RAG for languagePackId={}: outboxReset={}, docStatusDropped={}",
                languagePackId, outboxReset, docStatusDropped);
        return new RebuildResult(outboxReset, docStatusDropped);
    }

    public record RebuildResult(int outboxRowsReset, int docStatusRowsDeleted) {}
}
