package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ClassroomMonitorSnapshotsResponse(
        @JsonProperty("results") List<ClassroomMonitorSnapshotItemResponse> results,
        @JsonProperty("total") int total
) {
}
