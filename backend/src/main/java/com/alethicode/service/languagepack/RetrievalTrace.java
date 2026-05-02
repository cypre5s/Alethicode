package com.alethicode.service.languagepack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RetrievalTrace(
        String originalQuery,
        String rewrittenQuery,
        List<PageRetrievalHit> hits,
        int candidateCount,
        long latencyMs,
        String strategy
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("original_query", originalQuery);
        map.put("rewritten_query", rewrittenQuery);
        map.put("hit_count", hits == null ? 0 : hits.size());
        map.put("candidate_count", candidateCount);
        map.put("latency_ms", latencyMs);
        map.put("strategy", strategy);
        if (hits != null) {
            map.put("hits", hits.stream().map(PageRetrievalHit::toMap).toList());
        }
        return map;
    }
}
