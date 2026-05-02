package com.alethicode.service.aitutor.profile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record MemoryCandidate(
        String memoryKey,
        String summary,
        String memoryType,
        double confidence,
        String source,
        MemoryScope scope,
        Long sourceProblemId,
        Instant createdAt
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("memory_key", memoryKey);
        map.put("summary", summary);
        map.put("memory_type", memoryType);
        map.put("confidence", confidence);
        map.put("source", source);
        map.put("scope", scope == null ? null : scope.name());
        map.put("source_problem_id", sourceProblemId);
        map.put("created_at", createdAt == null ? null : createdAt.toString());
        return map;
    }
}
