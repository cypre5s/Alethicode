package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClassroomMonitorStatsResponse(
        @JsonProperty("total_members") int totalMembers,
        @JsonProperty("online_count") int onlineCount,
        @JsonProperty("active_count") int activeCount,
        @JsonProperty("coding_count") int codingCount,
        @JsonProperty("idle_count") int idleCount,
        @JsonProperty("active_coding") int activeCoding,
        @JsonProperty("abnormal_count") int abnormalCount,
        @JsonProperty("avg_progress") double avgProgress
) {
}
