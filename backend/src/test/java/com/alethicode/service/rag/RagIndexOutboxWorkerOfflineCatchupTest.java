package com.alethicode.service.rag;

import com.alethicode.service.rag.dto.RagEntityType;
import com.alethicode.service.rag.dto.RagIndexAcceptedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证计划 Phase 1 验收 2：「alethicode-rag 离线时 outbox 堆积，attempts
 * 累加；上线后 worker 一轮内追平」。
 *
 * <p>这是一个集成视角的状态机测试 — 用 JdbcTemplate mock 模拟 outbox 表的内存版，
 * 用 RagServiceClient mock 模拟 alethicode-rag 离线/上线切换，验证 worker 在
 * 跨 drain 调用之间能正确累加 attempts、退避调度、上线后一次 drain 全部追平。
 *
 * <p>不接真 PG / 真 alethicode-rag 是因为：（A）单元测试不应依赖外部容器；（B）
 * outbox 状态转移逻辑本身就是 worker 的内聚行为，HTTP/SQL 双侧的契约由
 * `HttpRagServiceClientTest` 与 `RagIndexQueueServiceTest` 各自负责。
 */
class RagIndexOutboxWorkerOfflineCatchupTest {

    private final List<Map<String, Object>> outbox = new ArrayList<>();
    private JdbcTemplate jdbcTemplate;
    private RagServiceClient ragServiceClient;
    private SimpleMeterRegistry meterRegistry;
    private RagIndexOutboxWorker worker;
    private final AtomicBoolean ragDown = new AtomicBoolean(true);
    private final AtomicInteger nextId = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ragServiceClient = mock(RagServiceClient.class);
        meterRegistry = new SimpleMeterRegistry();
        worker = new RagIndexOutboxWorker(
                jdbcTemplate, ragServiceClient, new ObjectMapper(),
                meterRegistry, true, 30_000
        );
        when(jdbcTemplate.queryForList(anyString(), eq(RagIndexOutboxWorker.BATCH_SIZE)))
                .thenAnswer(inv -> outbox.stream()
                        .filter(r -> r.get("indexed_at") == null && r.get("given_up_at") == null)
                        .filter(r -> {
                            Long nextRetryAtMs = (Long) r.get("next_retry_at_epoch_ms");
                            return nextRetryAtMs == null || nextRetryAtMs <= System.currentTimeMillis();
                        })
                        .map(LinkedHashMap::new)
                        .map(m -> {
                            m.put("payload_json", m.get("payload_json"));
                            return (Map<String, Object>) m;
                        })
                        .limit(RagIndexOutboxWorker.BATCH_SIZE)
                        .toList());
        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            Object[] args = inv.getArguments();
            if (sql.contains("indexed_at = now()") && args.length == 2) {
                long id = ((Number) args[1]).longValue();
                find(id).ifPresent(r -> r.put("indexed_at", System.currentTimeMillis()));
                return 1;
            }
            if (sql.contains("next_retry_at = now() + (interval '1 second') * ?")) {
                int attempts = (int) args[1];
                String error = (String) args[2];
                long backoffSeconds = ((Number) args[3]).longValue();
                long id = ((Number) args[4]).longValue();
                find(id).ifPresent(r -> {
                    r.put("attempts", attempts);
                    r.put("last_error", error);
                    r.put("next_retry_at_epoch_ms", System.currentTimeMillis() + backoffSeconds * 1000L);
                });
                return 1;
            }
            if (sql.contains("given_up_at = now()")) {
                int attempts = (int) args[1];
                String error = (String) args[2];
                long id = ((Number) args[3]).longValue();
                find(id).ifPresent(r -> {
                    r.put("attempts", attempts);
                    r.put("last_error", error);
                    r.put("given_up_at", System.currentTimeMillis());
                });
                return 1;
            }
            return 0;
        }).when(jdbcTemplate).update(anyString(), any(Object[].class));
        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            Object[] args = inv.getArguments();
            Object[] params = new Object[args.length - 1];
            System.arraycopy(args, 1, params, 0, args.length - 1);
            if (sql.contains("indexed_at = now()") && params.length == 1) {
                long id = ((Number) params[0]).longValue();
                find(id).ifPresent(r -> r.put("indexed_at", System.currentTimeMillis()));
                return 1;
            }
            if (sql.contains("next_retry_at = now() + (interval '1 second') * ?")) {
                int attempts = (int) params[0];
                String error = (String) params[1];
                long backoffSeconds = ((Number) params[2]).longValue();
                long id = ((Number) params[3]).longValue();
                find(id).ifPresent(r -> {
                    r.put("attempts", attempts);
                    r.put("last_error", error);
                    r.put("next_retry_at_epoch_ms", System.currentTimeMillis() + backoffSeconds * 1000L);
                });
                return 1;
            }
            if (sql.contains("given_up_at = now()")) {
                int attempts = (int) params[0];
                String error = (String) params[1];
                long id = ((Number) params[2]).longValue();
                find(id).ifPresent(r -> {
                    r.put("attempts", attempts);
                    r.put("last_error", error);
                    r.put("given_up_at", System.currentTimeMillis());
                });
                return 1;
            }
            return 0;
        }).when(jdbcTemplate).update(anyString(), any(Object.class), any(Object.class));

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            Object[] args = inv.getArguments();
            Object[] params = new Object[args.length - 1];
            System.arraycopy(args, 1, params, 0, args.length - 1);
            if (sql.contains("next_retry_at = now() + (interval '1 second') * ?") && params.length == 4) {
                int attempts = (int) params[0];
                String error = (String) params[1];
                long backoffSeconds = ((Number) params[2]).longValue();
                long id = ((Number) params[3]).longValue();
                find(id).ifPresent(r -> {
                    r.put("attempts", attempts);
                    r.put("last_error", error);
                    r.put("next_retry_at_epoch_ms", System.currentTimeMillis() + backoffSeconds * 1000L);
                });
                return 1;
            }
            if (sql.contains("given_up_at = now()") && params.length == 3) {
                int attempts = (int) params[0];
                String error = (String) params[1];
                long id = ((Number) params[2]).longValue();
                find(id).ifPresent(r -> {
                    r.put("attempts", attempts);
                    r.put("last_error", error);
                    r.put("given_up_at", System.currentTimeMillis());
                });
                return 1;
            }
            return 0;
        }).when(jdbcTemplate).update(anyString(), any(Object.class), any(Object.class), any(Object.class), any(Object.class));
        when(ragServiceClient.indexNow(any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    if (ragDown.get()) {
                        throw new RagServiceException("alethicode-rag offline (simulated)");
                    }
                    return new RagIndexAcceptedResponse(
                            "task-" + nextId.getAndIncrement(),
                            ((RagEntityType) inv.getArgument(0)).slug(),
                            inv.getArgument(1)
                    );
                });
    }

    @Test
    void offlineThenOnline_outboxBacksOffThenCatchesUpInOnePass() {
        seedPending("p1", 0);
        seedPending("p2", 0);
        seedPending("p3", 0);
        ragDown.set(true);
        int processed = worker.drainOnce();
        assertThat(processed).isEqualTo(3);
        for (Map<String, Object> r : outbox) {
            assertThat(r.get("attempts")).isEqualTo(1);
            assertThat(r.get("indexed_at")).isNull();
            assertThat(r.get("given_up_at")).isNull();
            assertThat((Long) r.get("next_retry_at_epoch_ms")).isGreaterThan(System.currentTimeMillis());
        }
        assertThat(meterRegistry.counter("rag_outbox_failure_total").count()).isEqualTo(3.0);
        assertThat(meterRegistry.counter("rag_outbox_giveup_total").count()).isEqualTo(0.0);
        assertThat(meterRegistry.counter("rag_outbox_success_total").count()).isEqualTo(0.0);
        for (Map<String, Object> r : outbox) {
            r.put("next_retry_at_epoch_ms", System.currentTimeMillis() - 1L);
        }
        ragDown.set(false);
        int processedAfter = worker.drainOnce();
        assertThat(processedAfter).isEqualTo(3);
        for (Map<String, Object> r : outbox) {
            assertThat(r.get("indexed_at")).isNotNull();
            assertThat(r.get("given_up_at")).isNull();
        }
        assertThat(meterRegistry.counter("rag_outbox_success_total").count()).isEqualTo(3.0);
    }

    @Test
    void persistentOutage_eventuallyMovesAllRowsToGivenUp() {
        seedPending("dead-1", 0);
        ragDown.set(true);
        for (int i = 0; i < 5; i++) {
            for (Map<String, Object> r : outbox) {
                r.put("next_retry_at_epoch_ms", System.currentTimeMillis() - 1L);
            }
            worker.drainOnce();
        }

        Map<String, Object> row = outbox.get(0);
        assertThat(row.get("attempts")).isEqualTo(RagIndexOutboxWorker.MAX_ATTEMPTS);
        assertThat(row.get("given_up_at")).isNotNull();
        assertThat(row.get("indexed_at")).isNull();
        assertThat(meterRegistry.counter("rag_outbox_giveup_total").count()).isEqualTo(1.0);
    }

    private void seedPending(String entityId, int attempts) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", (long) outbox.size() + 1);
        row.put("entity_type", "courseware-page");
        row.put("entity_id", entityId);
        row.put("action", "INDEX");
        row.put("payload_json", "{\"content\":\"t\",\"metadata\":{}}");
        row.put("attempts", attempts);
        row.put("indexed_at", null);
        row.put("given_up_at", null);
        row.put("next_retry_at_epoch_ms", null);
        outbox.add(row);
    }

    private java.util.Optional<Map<String, Object>> find(long id) {
        return outbox.stream().filter(r -> ((Number) r.get("id")).longValue() == id).findFirst();
    }
}
