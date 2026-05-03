package com.alethicode.dto.response.twin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record KcGalaxyResponse(
        List<KcGalaxyNode> nodes,
        List<KcGalaxyEdge> edges
) {
    public record KcGalaxyNode(
            @JsonProperty("kc_id") long kcId,
            String name,
            double mastery,
            @JsonProperty("last_touched_at") Instant lastTouchedAt,
            @JsonProperty("recent_event_count") int recentEventCount,
            String category
    ) {}

    public record KcGalaxyEdge(
            @JsonProperty("from_kc_id") long fromKcId,
            @JsonProperty("to_kc_id") long toKcId,
            @JsonProperty("relation_type") String relationType,
            double weight
    ) {}
}
