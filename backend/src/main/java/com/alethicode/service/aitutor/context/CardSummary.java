package com.alethicode.service.aitutor.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用于 Chat 证据和 {@code @card:<id>} 解析的轻量卡片摘要。
 *
 * <p>Design: <code>docs/plans/2026-04-25-unified-chat-context-design.md</code> §7.3</p>
 */
public record CardSummary(
        String cardId,
        String cardType,
        String modeWhenProduced,
        String shortText,
        Instant createdAt
) {

    /** 转为可传给 HTTP 和 prompt 的 snake_case 映射。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("card_id", cardId == null ? "" : cardId);
        m.put("card_type", cardType);
        m.put("mode_when_produced", modeWhenProduced == null ? "" : modeWhenProduced);
        m.put("short_text", shortText == null ? "" : shortText);
        m.put("created_at", createdAt == null ? "" : createdAt.toString());
        return m;
    }
}
