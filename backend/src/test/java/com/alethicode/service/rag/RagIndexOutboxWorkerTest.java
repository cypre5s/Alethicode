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
 * 验证 worker 的成功、失败重试和放弃状态机。
 *
 * <p>被测契约：
 * <ul>
 *   <li>成功行写入 {@code indexed_at = now()}，成功计数加 1。</li>
 *   <li>未达最大次数的失败行推进 {@code next_retry_at}，退避上限 1 小时。</li>
 *   <li>达到最大次数后写入 {@code given_up_at = now()}，放弃计数加 1。</li>
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
        assertThat((int) values.get(0)).isEqualTo(1);
        assertThat((long) values.get(2)).isEqualTo(60L); // 60s × 2^(1-1) = 60s
        assertThat((long) values.get(3)).isEqualTo(11L);
        assertThat(meterRegistry.counter("rag_outbox_failure_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("rag_outbox_giveup_total").count()).isEqualTo(0.0);
    }

    @Test
    void backoffCapsAtOneHour() {
        Map<String, Object> row = pendingIndexRow(13L, "memory", "1:bar", 20);
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenReturn(List.of(row));
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
        assertThat(backoff).isEqualTo(480L);
    }

    @Test
    void fifthFailureParksRowAndIncrementsGiveupCounter() {
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
