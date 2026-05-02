package com.alethicode.service.aitutor.profile;

import com.alethicode.service.rag.RagServiceClient;
import com.alethicode.service.rag.dto.RagMemoryQueryRequest;
import com.alethicode.service.rag.dto.RagQueryHits;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3 切流后契约：检索路径 100% 走 alethicode-rag 的 LightRAG mix-mode；
 * 服务层不再持有 16 维 embedding，而是从 ragClient 拿 chunks，再用 entity_id
 * （metadata.memory_key）反查业务表。
 */
class LearnerMemorySemanticRetrievalServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RagServiceClient ragClient = mock(RagServiceClient.class);
    private final LearnerMemorySemanticRetrievalService service =
            new LearnerMemorySemanticRetrievalService(jdbc, ragClient, new ObjectMapper());

    @Test
    void returnsEmptyWhenUserIdIsNull() {
        assertThat(service.retrieveByContext(null, List.of(), "", 5)).isEmpty();
        verify(ragClient, never()).queryMemory(any());
    }

    @Test
    void returnsEmptyWhenContextSignalsEmpty() {
        assertThat(service.retrieveByContext(7L, List.of(), "", 5)).isEmpty();
        verify(ragClient, never()).queryMemory(any());
    }

    @Test
    void mapsRagChunkToBusinessRowAndFiltersByConfidence() {
        when(ragClient.queryMemory(any(RagMemoryQueryRequest.class)))
                .thenReturn(new RagQueryHits(
                        List.of(),
                        List.of(),
                        List.of(
                                new RagQueryHits.RetrievedChunk(
                                        "chunk-a", "loop range error", 0.92,
                                        Map.of("memory_key", "notebook:1", "memory_type", "error_pattern")
                                )
                        ),
                        null
                ));
        when(jdbc.queryForList(anyString(), anyLong(), anyString()))
                .thenReturn(List.of(buildBizRow(1L, "notebook:1", 0.92, "loop range error", "{\"summary\":\"loop range error\"}")));

        List<Map<String, Object>> hits = service.retrieveByContext(
                42L, List.of("for_loop_boundary"), "IndexError: list index out of range", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0)).containsEntry("memory_key", "notebook:1");
        assertThat(hits.get(0)).containsEntry("memory_summary", "loop range error");
        assertThat(hits.get(0)).containsEntry("confidence", 0.92);
        assertThat((double) hits.get(0).get("distance")).isCloseTo(0.08, org.assertj.core.data.Offset.offset(0.001));
        verify(ragClient).queryMemory(any());
    }

    @Test
    void rejectsLowConfidenceMatches() {
        when(ragClient.queryMemory(any(RagMemoryQueryRequest.class)))
                .thenReturn(new RagQueryHits(
                        List.of(), List.of(),
                        List.of(new RagQueryHits.RetrievedChunk(
                                "chunk-low", "noise", 0.4,
                                Map.of("memory_key", "noise:1")
                        )),
                        null
                ));
        when(jdbc.queryForList(anyString(), anyLong(), anyString()))
                .thenReturn(List.of(buildBizRow(2L, "noise:1", 0.30, "noise", "{}")));

        List<Map<String, Object>> hits = service.retrieveByContext(42L, List.of(), "noise", 5);
        assertThat(hits).isEmpty();
    }

    @Test
    void clampsTopKToFive() {
        when(ragClient.queryMemory(any(RagMemoryQueryRequest.class)))
                .thenReturn(new RagQueryHits(List.of(), List.of(), List.of(), null));

        // request 100 → contract clamps to 5 inside the request DTO; assertion is "no NPE / no oversized fan-out"
        List<Map<String, Object>> hits = service.retrieveByContext(42L, List.of(), "some err", 100);
        assertThat(hits).isEmpty();
    }

    private Map<String, Object> buildBizRow(long id, String memoryKey, double confidence, String memoryValue, String payloadJson) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("memory_key", memoryKey);
        row.put("memory_type", "error_pattern");
        row.put("memory_value", memoryValue);
        row.put("payload_json", payloadJson);
        row.put("confidence", confidence);
        row.put("source_problem_id", null);
        return row;
    }
}
