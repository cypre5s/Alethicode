package com.alethicode.service.aitutor.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTraceRecorderTest {

    private JdbcTemplate jdbcTemplate;
    private AgentTraceRecorder recorder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        recorder = new AgentTraceRecorder(jdbcTemplate, objectMapper);
    }

    @Test
    void startSpanRejectsBlankTraceId() {
        assertThatThrownBy(() ->
                recorder.startSpan(" ", AgentTraceRecorder.SpanType.DISPATCH, "sess-1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startSpanRejectsNullSpanType() {
        assertThatThrownBy(() ->
                recorder.startSpan("trace-1", null, "sess-1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startSpanProducesUniqueSpanIds() {
        AgentTraceRecorder.SpanHandle h1 = recorder.startSpan(
                "trace-1", AgentTraceRecorder.SpanType.LLM_CALL, "sess-1", null, null);
        AgentTraceRecorder.SpanHandle h2 = recorder.startSpan(
                "trace-1", AgentTraceRecorder.SpanType.LLM_CALL, "sess-1", null, null);

        assertThat(h1.spanId()).isNotEqualTo(h2.spanId());
        assertThat(h1.traceId()).isEqualTo("trace-1");
        assertThat(h1.spanType()).isEqualTo(AgentTraceRecorder.SpanType.LLM_CALL);
    }

    @Test
    void endSpanWritesExpectedEventDataFields() throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("agent", "diagnostics_v1");
        AgentTraceRecorder.SpanHandle handle = recorder.startSpan(
                "trace-abc", AgentTraceRecorder.SpanType.LLM_CALL, "sess-42", "parent-span-1", metadata);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tokens", 420);
        recorder.endSpan(handle, AgentTraceRecorder.SpanStatus.OK, "llm call succeeded", payload);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                anyString(),
                any(Object.class),
                jsonCaptor.capture(),
                any(Object.class)
        );

        Map<String, Object> recorded = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
        assertThat(recorded)
                .containsEntry("trace_id", "trace-abc")
                .containsEntry("span_type", "LLM_CALL")
                .containsEntry("status", "OK")
                .containsEntry("summary", "llm call succeeded")
                .containsEntry("parent_span_id", "parent-span-1");
        assertThat(recorded.get("span_id")).isNotNull();
        assertThat(recorded.get("duration_ms")).isNotNull();
        Map<String, Object> meta = (Map<String, Object>) recorded.get("metadata");
        assertThat(meta).containsEntry("agent", "diagnostics_v1");
        Map<String, Object> pl = (Map<String, Object>) recorded.get("payload");
        assertThat(pl).containsEntry("tokens", 420);
    }

    @Test
    void endSpanDoesNotBlockOnJdbcFailure() {
        when(jdbcTemplate.update(anyString(), any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new RuntimeException("db down"));

        AgentTraceRecorder.SpanHandle handle = recorder.startSpan(
                "trace-err", AgentTraceRecorder.SpanType.GUARDRAIL, "sess-err", null, null);

        // Should NOT throw even if JDBC blows up
        recorder.endSpan(handle, AgentTraceRecorder.SpanStatus.FAILED, "db error", null);
    }

    @Test
    void endSpanIgnoresNullHandle() {
        recorder.endSpan(null, AgentTraceRecorder.SpanStatus.OK, "ignored", null);
        verify(jdbcTemplate, never())
                .update(anyString(), any(Object.class), any(Object.class), any(Object.class));
    }

    @Test
    void endSpanOkAndFailedHelpersDelegateToEndSpan() throws Exception {
        AgentTraceRecorder.SpanHandle handle = recorder.startSpan(
                "trace-1", AgentTraceRecorder.SpanType.TOOL_CALL, "sess-1", null, null);
        recorder.endSpanOk(handle, "ok");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), any(Object.class), jsonCaptor.capture(), any(Object.class));
        Map<String, Object> recorded = objectMapper.readValue(jsonCaptor.getValue(), Map.class);
        assertThat(recorded).containsEntry("status", "OK");
    }
}
