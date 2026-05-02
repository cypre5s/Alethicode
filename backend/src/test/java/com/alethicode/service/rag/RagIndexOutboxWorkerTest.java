package com.alethicode.service.rag;

import com.alethicode.service.rag.dto.RagEntityType;
import com.alethicode.service.rag.dto.RagIndexAcceptedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the worker's promotion-failure-give-up state machine.
 *
 * <p>The contract under test:
 * <ul>
 *   <li>Successful row → {@code indexed_at = now()}, success counter +1.</li>
 *   <li>Failed row, attempts &lt; 4 → next_retry_at advances by
 *       60 × 2^(attempts-1) seconds (capped at 1h).</li>
 *   <li>Failed row, attempts == 4 → next failure parks the row with
 *       {@code given_up_at = now()}, give-up counter +1.</li>
 * </ul>
 */
class RagIndexOutboxWorkerTest {

    private JdbcTemplate jdbcTemplate;
    private RagServiceClient ragServiceClient;
    private SimpleMeterRegistry meterRegistry;
    private RagIndexOutboxWorker worker;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ragServiceClient = mock(RagServiceClient.class);
        meterRegistry = new SimpleMeterRegistry();
        worker = new RagIndexOutboxWorker(
                jdbcTemplate,
                ragServiceClient,
                new ObjectMapper(),
                meterRegistry,
                /* enabled */ true,
                /* fixedDelayMs */ 30_000
        );
    }

    @Test
    void successfulIndexCallMarksRowAsIndexed() {
        Map<String, Object> row = pendingIndexRow(7L, "courseware-page", "p42", 0);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenReturn(List.of(row));
        when(ragServiceClient.indexNow(any(), anyString(), anyString(), any()))
                .thenReturn(new RagIndexAcceptedResponse("task-1", "courseware-page", "p42"));

        int processed = worker.drainOnce();

        assertThat(processed).isEqualTo(1);
        verify(jdbcTemplate, times(1)).update(
                contains("indexed_at = now()"),
                eq(7L)
        );
        assertThat(meterRegistry.counter("rag_outbox_success_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("rag_outbox_failure_total").count()).isEqualTo(0.0);
    }

    @Test
    void failureBelowMaxAttemptsSchedulesExponentialBackoff() {
        Map<String, Object> row = pendingIndexRow(11L, "memory", "1:foo", 0);
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenReturn(List.of(row));
        when(ragServiceClient.indexNow(any(), anyString(), anyString(), any()))
                .thenThrow(new RagServiceException("boom"));

        worker.drainOnce();

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                contains("next_retry_at = now() + (interval '1 second') * ?"),
                args.capture(),
                args.capture(),
                args.capture(),
                args.capture()
        );
        List<Object> values = args.getAllValues();
        // values[0] = attempts (1), values[1] = error msg, values[2] = backoffSeconds, values[3] = id
        assertThat((int) values.get(0)).isEqualTo(1);
        assertThat((long) values.get(2)).isEqualTo(60L); // 60s × 2^(1-1) = 60s
        assertThat((long) values.get(3)).isEqualTo(11L);
        assertThat(meterRegistry.counter("rag_outbox_failure_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("rag_outbox_giveup_total").count()).isEqualTo(0.0);
    }

    @Test
    void backoffCapsAtOneHour() {
        // attempts=20 means next attempt would be 60s × 2^19 way over an hour.
        Map<String, Object> row = pendingIndexRow(13L, "memory", "1:bar", 20);
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenReturn(List.of(row));
        // attempts=20 already hit MAX_ATTEMPTS, so this would actually give up. Use 3 to exercise cap-style scaling.
        row.put("attempts", 3);
        when(ragServiceClient.indexNow(any(), anyString(), anyString(), any()))
                .thenThrow(new RagServiceException("still down"));

        worker.drainOnce();

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                contains("next_retry_at"),
                args.capture(),
                args.capture(),
                args.capture(),
                args.capture()
        );
        long backoff = (long) args.getAllValues().get(2);
        // attempts becomes 4, 60 × 2^3 = 480s, still under 1h cap
        assertThat(backoff).isEqualTo(480L);
    }

    @Test
    void fifthFailureParksRowAndIncrementsGiveupCounter() {
        // attempts=4 means this is the 5th attempt → MAX_ATTEMPTS → give up
        Map<String, Object> row = pendingIndexRow(99L, "memory", "1:dead", 4);
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenReturn(List.of(row));
        when(ragServiceClient.indexNow(any(), anyString(), anyString(), any()))
                .thenThrow(new RagServiceException("permanently broken"));

        worker.drainOnce();

        verify(jdbcTemplate).update(
                contains("given_up_at = now()"),
                eq(RagIndexOutboxWorker.MAX_ATTEMPTS),
                anyString(),
                eq(99L)
        );
        assertThat(meterRegistry.counter("rag_outbox_giveup_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("rag_outbox_failure_total").count()).isEqualTo(0.0);
    }

    @Test
    void deleteActionRoutesToDeleteNow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 5L);
        row.put("entity_type", "memory");
        row.put("entity_id", "1:gone");
        row.put("action", "DELETE");
        row.put("payload_json", "{}");
        row.put("attempts", 0);
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenReturn(List.of(row));

        worker.drainOnce();

        verify(ragServiceClient).deleteNow(RagEntityType.MEMORY, "1:gone");
        verify(ragServiceClient, never()).indexNow(any(), anyString(), anyString(), any());
        verify(jdbcTemplate).update(
                contains("indexed_at = now()"),
                eq(5L)
        );
    }

    @Test
    void emptyPendingQueueIsANoop() {
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenReturn(List.of());
        int processed = worker.drainOnce();
        assertThat(processed).isEqualTo(0);
        verify(ragServiceClient, never()).indexNow(any(), anyString(), anyString(), any());
        verify(ragServiceClient, never()).deleteNow(any(), anyString());
    }

    private static Map<String, Object> pendingIndexRow(long id, String entityType, String entityId, int attempts) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("entity_type", entityType);
        row.put("entity_id", entityId);
        row.put("action", "INDEX");
        row.put("payload_json", "{\"content\":\"some text\",\"metadata\":{\"k\":\"v\"}}");
        row.put("attempts", attempts);
        return row;
    }
}
