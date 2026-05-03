package com.alethicode.service.aitutor.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 面向 Admin 观测驾驶舱的 span 级追踪记录器。
 *
 * 设计目标（严格遵守 `todos-three-remaining` 阶段 2 的约束）：
 * 1. 粒度：span_type 必须是 {@link SpanType} 枚举之一（DISPATCH / EVIDENCE_ASSEMBLY /
 *    MEMORY_RECALL / LLM_CALL / TOOL_CALL / GUARDRAIL / OUTPUT），不允许自由字符串；
 * 2. 不污染 agent 状态机：只写 {@code ai_workflow_event} 的 {@code event_type='trace_span'}，
 *    不触碰 {@code agent_status / runtime_state} 字段；
 * 3. 失败不阻断主链路：写库异常只打 warn 日志，{@link SpanHandle} 仍正常关闭；
 * 4. 每个 span 记录 {@code trace_id / span_id / parent_span_id / duration_ms}，方便前端甘特图回放。
 */
@Component
public class AgentTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceRecorder.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentTraceRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public SpanHandle startSpan(String traceId, SpanType spanType, String sessionId, String parentSpanId, Map<String, Object> metadata) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        if (spanType == null) {
            throw new IllegalArgumentException("spanType is required");
        }
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant startedAt = Instant.now();
        return new SpanHandle(traceId, spanId, parentSpanId, spanType, sessionId, startedAt, metadata);
    }

    /**
     * 正常结束 span。
     *
     * @param handle  start 时返回的句柄
     * @param status  span 结束时的状态（OK / FAILED 等）；由调用方基于业务结果决定
     * @param summary 一句话摘要，面向 admin 看板直接展示
     * @param payload 额外结构化字段（可选），比如 LLM tokens、tool 输入输出摘要
     */
    public void endSpan(SpanHandle handle, SpanStatus status, String summary, Map<String, Object> payload) {
        if (handle == null) {
            return;
        }
        long durationMs = Math.max(0L, Instant.now().toEpochMilli() - handle.startedAt().toEpochMilli());
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("trace_id", handle.traceId());
        eventData.put("span_id", handle.spanId());
        if (handle.parentSpanId() != null && !handle.parentSpanId().isBlank()) {
            eventData.put("parent_span_id", handle.parentSpanId());
        }
        eventData.put("span_type", handle.spanType().name());
        eventData.put("status", (status == null ? SpanStatus.OK : status).name());
        eventData.put("duration_ms", durationMs);
        if (summary != null && !summary.isBlank()) {
            eventData.put("summary", summary);
        }
        if (handle.metadata() != null && !handle.metadata().isEmpty()) {
            eventData.put("metadata", handle.metadata());
        }
        if (payload != null && !payload.isEmpty()) {
            eventData.put("payload", payload);
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(eventData);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize trace span payload: trace={}, span={}, type={}, err={}",
                    handle.traceId(), handle.spanId(), handle.spanType(), e.getMessage());
            return;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_workflow_event(session_id, event_type, event_data, trace_id, created_at)
                    VALUES (?, 'trace_span', cast(? as jsonb), ?, now())
                    """,
                    handle.sessionId() == null ? "" : handle.sessionId(),
                    json,
                    handle.traceId()
            );
        } catch (Exception e) {
            log.warn("Failed to persist trace span: trace={}, span={}, type={}, err={}",
                    handle.traceId(), handle.spanId(), handle.spanType(), e.getMessage());
        }
    }

    public void endSpanOk(SpanHandle handle, String summary) {
        endSpan(handle, SpanStatus.OK, summary, null);
    }

    public void endSpanFailed(SpanHandle handle, String summary, Map<String, Object> payload) {
        endSpan(handle, SpanStatus.FAILED, summary, payload);
    }

    /**
     * Agent 观测阶段枚举，严格对齐 `todos-three-remaining` 阶段 2.1 提到的 7 种 span_type。
     */
    public enum SpanType {
        DISPATCH,
        EVIDENCE_ASSEMBLY,
        MEMORY_RECALL,
        LLM_CALL,
        TOOL_CALL,
        GUARDRAIL,
        OUTPUT
    }

    public enum SpanStatus {
        OK,
        FAILED,
        SKIPPED
    }

    /**
     * Span 句柄；调用方负责在 try/finally 中成对调用 {@link #endSpan(SpanHandle, SpanStatus, String, Map)}.
     */
    public record SpanHandle(
            String traceId,
            String spanId,
            String parentSpanId,
            SpanType spanType,
            String sessionId,
            Instant startedAt,
            Map<String, Object> metadata
    ) {
    }
}
