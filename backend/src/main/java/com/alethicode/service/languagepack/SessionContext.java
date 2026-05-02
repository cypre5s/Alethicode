package com.alethicode.service.languagepack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SessionContext(
        Long sessionId,
        String recentDialogue,
        String sessionSummary,
        List<Long> recentCitedPageIds
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_id", sessionId);
        map.put("recent_dialogue", recentDialogue);
        map.put("session_summary", sessionSummary);
        map.put("recent_cited_page_ids", recentCitedPageIds);
        return map;
    }
}
