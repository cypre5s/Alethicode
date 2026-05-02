package com.alethicode.dto.request;

import java.util.List;
import java.util.Map;

/**
 * 公测前端遥测事件批次。前端 telemetry 模块每 5s 或满 20 条 flush，
 * 关闭页面时通过 navigator.sendBeacon 发送同样结构。
 */
public record BetaTelemetryBatchRequest(List<TelemetryEvent> events) {

    public record TelemetryEvent(
            String eventType,
            String route,
            Long problemId,
            String sessionId,
            Map<String, Object> payload,
            String occurredAt
    ) {
    }
}
