package com.alethicode.dto.response.twin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record LearningTimelineEntry(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_kind") String eventKind,
        @JsonProperty("event_at") Instant eventAt,
        @JsonProperty("problem_id") Long problemId,
        @JsonProperty("problem_title") String problemTitle,
        String summary,
        @JsonProperty("replay_available") boolean replayAvailable,
        Map<String, Object> meta
) {
    public static final String KIND_SUBMISSION = "submission";
    public static final String KIND_MEMORY = "memory";
    public static final String KIND_AI_EVENT = "ai_event";
    public static final String KIND_NOTEBOOK = "notebook";
}
