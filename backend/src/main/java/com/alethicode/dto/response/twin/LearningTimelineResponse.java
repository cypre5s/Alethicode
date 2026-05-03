package com.alethicode.dto.response.twin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LearningTimelineResponse(
        List<LearningTimelineEntry> events,
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("has_more") boolean hasMore
) {}
