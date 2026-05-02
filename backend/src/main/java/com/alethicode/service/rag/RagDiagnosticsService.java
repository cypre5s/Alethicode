package com.alethicode.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(RagDiagnosticsService.class);

    private final JdbcTemplate jdbcTemplate;
    private final WebClient ragWebClient;
    private final ObjectMapper objectMapper;
    private final RagHealthCheckService ragHealthCheckService;

    public RagDiagnosticsService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RagHealthCheckService ragHealthCheckService,
            @Value("${alethicode.rag.base-url:${RAG_SERVICE_URL:http://alethicode-rag:8200}}") String baseUrl,
            @Value("${alethicode.rag.internal-token:${RAG_INTERNAL_TOKEN:dev-internal-key}}") String token
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ragHealthCheckService = ragHealthCheckService;
        this.ragWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", token)
                .build();
    }

    public Map<String, Object> getRagStatus(Long languagePackId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("language_pack_id", languagePackId);

        Integer totalPages = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_page WHERE language_pack_id = ?",
                Integer.class, languagePackId
        );
        result.put("total_pages", totalPages == null ? 0 : totalPages);

        result.put("outbox", queryOutboxCounts(languagePackId));
        result.put("rag_pipeline", queryRagPipelineCounts(languagePackId));
        result.put("kg_summary", queryKgSummary(languagePackId));

        RagHealthCheckService.ReadinessResult readiness =
                ragHealthCheckService.assertReadyForPublish(languagePackId);
        result.put("ready_for_publish", readiness.ready());
        result.put("blockers", readiness.blockers());
        return result;
    }

    private Map<String, Object> queryOutboxCounts(Long languagePackId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT
                    coalesce(sum(case when o.indexed_at IS NULL AND o.given_up_at IS NULL then 1 else 0 end), 0) AS pending,
                    coalesce(sum(case when o.indexed_at IS NOT NULL then 1 else 0 end), 0) AS succeeded,
                    coalesce(sum(case when o.given_up_at IS NOT NULL then 1 else 0 end), 0) AS given_up
                FROM rag_index_outbox o
                JOIN language_pack_page p
                  ON o.entity_type='courseware_page' AND o.entity_id = p.id::text
                WHERE p.language_pack_id = ?
                """,
                languagePackId
        );
        Map<String, Object> outbox = new LinkedHashMap<>();
        outbox.put("pending", toInt(row.get("pending")));
        outbox.put("succeeded", toInt(row.get("succeeded")));
        outbox.put("given_up", toInt(row.get("given_up")));
        return outbox;
    }

    private Map<String, Object> queryRagPipelineCounts(Long languagePackId) {
        Map<String, Object> ragPipeline = new LinkedHashMap<>();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    """
                    SELECT
                        coalesce(sum(case when status='processed' then 1 else 0 end), 0) AS processed,
                        coalesce(sum(case when status='processing' then 1 else 0 end), 0) AS processing,
                        coalesce(sum(case when status='pending' then 1 else 0 end), 0) AS pending,
                        coalesce(sum(case when status='failed' then 1 else 0 end), 0) AS failed
                    FROM lightrag_doc_status
                    WHERE track_id IN (
                        SELECT 'courseware_page:' || p.id::text
                        FROM language_pack_page p
                        WHERE p.language_pack_id = ?
                    )
                    """,
                    languagePackId
            );
            ragPipeline.put("processed", toInt(row.get("processed")));
            ragPipeline.put("processing", toInt(row.get("processing")));
            ragPipeline.put("pending", toInt(row.get("pending")));
            ragPipeline.put("failed", toInt(row.get("failed")));
        } catch (Exception ex) {
            log.warn("lightrag_doc_status query failed: {}", ex.getMessage());
            ragPipeline.put("error", ex.getMessage());
        }
        return ragPipeline;
    }

    private Map<String, Object> queryKgSummary(Long languagePackId) {
        Map<String, Object> kgSummary = new LinkedHashMap<>();
        try {
            String response = ragWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/rag/diagnostics/track-stats")
                            .queryParam("language_pack_id", languagePackId)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();
            JsonNode node = objectMapper.readTree(response);
            kgSummary.put("entity_count", node.path("entity_count").asInt(0));
            kgSummary.put("relation_count", node.path("relation_count").asInt(0));
            kgSummary.put("track_count", node.path("track_count").asInt(0));
        } catch (Exception ex) {
            log.warn("KG counts via alethicode-rag failed: {}", ex.getMessage());
            kgSummary.put("error", ex.getMessage());
        }
        return kgSummary;
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
