package com.alethicode.service.aitutor.observability;

import java.util.Map;

/**
 * 在 Agent 执行链路中携带的观测上下文。
 *
 * <p>设计原则（严格遵循 todos-three-remaining 阶段 2.1）：
 * <ul>
 *   <li>不可变记录，传递成本低；</li>
 *   <li>null {@link #recorder} 表示当前调用链不要求 span 追踪，agent 应当原样跳过；</li>
 *   <li>{@link #parentSpanId()} 可空；顶层 DISPATCH span 的 parent 为 null；</li>
 *   <li>由 dispatcher（当前为 {@code AITutorWorkflowAdminServiceImpl.processWorkflowEvent}）在 session 级别创建一次，
 *       然后通过 {@code AgentContext.traceContext()} 传进各 agent。</li>
 * </ul>
 *
 * <p>子 span 的典型创建姿势：
 * <pre>{@code
 * AgentTraceContext tc = context.traceContext();
 * AgentTraceRecorder.SpanHandle handle = tc == null ? null : tc.recorder().startSpan(
 *     tc.traceId(), AgentTraceRecorder.SpanType.LLM_CALL,
 *     tc.sessionId(), tc.parentSpanId(), meta);
 * try {
 *     // ... LLM 调用 ...
 *     if (tc != null) tc.recorder().endSpanOk(handle, "llm ok");
 * } catch (Exception e) {
 *     if (tc != null) tc.recorder().endSpanFailed(handle, "llm failed", Map.of("err", e.getMessage()));
 *     throw e;
 * }
 * }</pre>
 */
public record AgentTraceContext(
        AgentTraceRecorder recorder,
        String traceId,
        String sessionId,
        String parentSpanId
) {

    public AgentTraceContext {
        if (recorder != null && (traceId == null || traceId.isBlank())) {
            throw new IllegalArgumentException("traceId is required when recorder is present");
        }
    }

    /**
     * 创建子上下文，把当前 parent 替换为给定 spanId。
     * 典型用途：在父 span 结束后、子 span 开始前保证 parent-child 链路。
     */
    public AgentTraceContext withParent(String newParentSpanId) {
        return new AgentTraceContext(recorder, traceId, sessionId, newParentSpanId);
    }

    /**
     * 便捷启动子 span；如果 recorder 为 null 返回 null，调用方需成对调用 {@link #endSpan}.
     */
    public AgentTraceRecorder.SpanHandle startSpan(AgentTraceRecorder.SpanType spanType,
                                                    Map<String, Object> metadata) {
        if (recorder == null) {
            return null;
        }
        return recorder.startSpan(traceId, spanType, sessionId, parentSpanId, metadata);
    }

    /**
     * 以 OK 状态关闭 span；null handle 会被静默跳过。
     */
    public void endSpanOk(AgentTraceRecorder.SpanHandle handle, String summary) {
        if (recorder == null || handle == null) {
            return;
        }
        recorder.endSpanOk(handle, summary);
    }

    /**
     * 以 FAILED 状态关闭 span；null handle 会被静默跳过。
     */
    public void endSpanFailed(AgentTraceRecorder.SpanHandle handle, String summary, Map<String, Object> payload) {
        if (recorder == null || handle == null) {
            return;
        }
        recorder.endSpanFailed(handle, summary, payload);
    }

    public void endSpan(AgentTraceRecorder.SpanHandle handle,
                        AgentTraceRecorder.SpanStatus status,
                        String summary,
                        Map<String, Object> payload) {
        if (recorder == null || handle == null) {
            return;
        }
        recorder.endSpan(handle, status, summary, payload);
    }
}
