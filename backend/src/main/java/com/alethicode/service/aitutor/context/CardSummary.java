package com.alethicode.service.aitutor.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact card representation used as Chat evidence and for {@code @card:<id>} resolution.
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

    /** Compact JSON map for HTTP / prompt injection (snake_case keys to match tutor_graph). */
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
