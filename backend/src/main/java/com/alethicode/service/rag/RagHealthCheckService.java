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
import java.util.List;

@Service
public class RagHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(RagHealthCheckService.class);

    private final JdbcTemplate jdbcTemplate;
    private final WebClient ragWebClient;
    private final ObjectMapper objectMapper;

    public RagHealthCheckService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${alethicode.rag.base-url:${RAG_SERVICE_URL:http://alethicode-rag:8200}}") String baseUrl,
            @Value("${alethicode.rag.internal-token:${RAG_INTERNAL_TOKEN:dev-internal-key}}") String token
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ragWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", token)
                .build();
    }

    public ReadinessResult assertReadyForPublish(Long languagePackId) {
        List<String> blockers = new ArrayList<>();

        try {
            String healthJson = ragWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            JsonNode health = objectMapper.readTree(healthJson);
            if (!"ok".equals(health.path("status").asText())) {
                blockers.add("alethicode-rag /health status=" + health.path("status").asText());
            }
            if (!"ok".equals(health.path("postgres").asText())) {
                blockers.add("alethicode-rag postgres=" + health.path("postgres").asText());
            }
            if (!"ok".equals(health.path("memgraph").asText())) {
                blockers.add("alethicode-rag memgraph=" + health.path("memgraph").asText());
            }
            if (!health.path("llm_smoke_ok").asBoolean(false)) {
                blockers.add("alethicode-rag LLM smoke probe 未通过（启动期 LLM 401）");
            }
        } catch (Exception ex) {
            blockers.add("alethicode-rag /health 调用失败: " + ex.getMessage());
        }

        Integer givenUpCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM rag_index_outbox o
                JOIN language_pack_page p
                  ON o.entity_type='courseware_page' AND o.entity_id = p.id::text
                WHERE p.language_pack_id = ?
                  AND o.given_up_at IS NOT NULL
                """,
                Integer.class, languagePackId
        );
        if (givenUpCount != null && givenUpCount > 0) {
            blockers.add(givenUpCount + " 个 page 索引已 given_up，请使用 admin /rag-rebuild 一键重建");
        }

        Integer pendingCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM rag_index_outbox o
                JOIN language_pack_page p
                  ON o.entity_type='courseware_page' AND o.entity_id = p.id::text
                WHERE p.language_pack_id = ?
                  AND o.indexed_at IS NULL AND o.given_up_at IS NULL
                """,
                Integer.class, languagePackId
        );
        if (pendingCount != null && pendingCount > 0) {
            blockers.add(pendingCount + " 个 page 还在 RAG 索引队列，drain 完成前不能发布");
        }

        boolean ready = blockers.isEmpty();
        if (!ready) {
            log.warn("RAG readiness check FAILED for languagePackId={}: {}", languagePackId, blockers);
        }
        return new ReadinessResult(ready, blockers);
    }

    public record ReadinessResult(boolean ready, List<String> blockers) {}
}
