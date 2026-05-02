package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClassroomMonitorSnapshotItemResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("username") String username,
        @JsonProperty("real_name") String realName,
        @JsonProperty("activity_status") String activityStatus,
        @JsonProperty("error_taxonomy") String errorTaxonomy,
        @JsonProperty("current_problem") ClassroomCurrentProblemResponse currentProblem,
        @JsonProperty("code_length") int codeLength,
        @JsonProperty("last_activity") String lastActivity,
        @JsonProperty("active_time") long activeTime,
        @JsonProperty("submission_count") int submissionCount,
        @JsonProperty("ac_count") int acCount,
        @JsonProperty("progress") double progress
) {
}
