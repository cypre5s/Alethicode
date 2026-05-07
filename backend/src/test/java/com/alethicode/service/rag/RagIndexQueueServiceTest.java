package com.alethicode.service.rag;

import com.alethicode.service.rag.dto.RagEntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 验证队列按约定形态写入 outbox 行。
 */
class RagIndexQueueServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private RagIndexQueueService queue;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        queue = new RagIndexQueueService(jdbcTemplate, objectMapper);
    }

    @Test
    void enqueueIndexInsertsWithExpectedColumnsAndCoalescesPayload() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("user_id", 42L);
        meta.put("memory_type", "tutor_conclusion");

        queue.enqueueIndex(RagEntityType.MEMORY, "42:event:99", "教学结论", meta);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate, times(1)).update(
                sql.capture(),
                params.capture(),
                params.capture(),
                params.capture()
        );
        String sqlText = sql.getValue();
        assertThat(sqlText).contains("INSERT INTO rag_index_outbox");
        assertThat(sqlText).contains("ON CONFLICT (entity_type, entity_id, action)");
        assertThat(sqlText).contains("attempts = 0");
        assertThat(sqlText).contains("given_up_at = NULL");

        java.util.List<Object> values = params.getAllValues();
        assertThat(values.get(0)).isEqualTo("memory");
        assertThat(values.get(1)).isEqualTo("42:event:99");
        String payloadJson = (String) values.get(2);
        assertThat(payloadJson).contains("\"content\":\"教学结论\"");
        assertThat(payloadJson).contains("\"memory_type\":\"tutor_conclusion\"");
        assertThat(payloadJson).contains("\"entity_id\":\"42:event:99\"");
    }

    @Test
    void enqueueDeleteWritesEmptyPayloadAndDeleteAction() {
        queue.enqueueDelete(RagEntityType.NOTEBOOK, "abc-123");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object.class), any(Object.class));
        String sqlText = sql.getValue();
        assertThat(sqlText).contains("'DELETE'");
        assertThat(sqlText).contains("ON CONFLICT (entity_type, entity_id, action)");
    }

    @Test
    void enqueueIndexRejectsBlankContent() {
        assertThatThrownBy(() ->
                queue.enqueueIndex(RagEntityType.MEMORY, "1:k", "  ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void enqueueIndexRejectsBlankEntityId() {
        assertThatThrownBy(() ->
                queue.enqueueIndex(RagEntityType.NOTEBOOK, "  ", "x", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityId");
    }
}
