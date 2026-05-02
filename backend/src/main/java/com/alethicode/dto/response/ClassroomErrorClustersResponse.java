package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClassroomErrorClustersResponse(
        @JsonProperty("clusters") List<ClassroomErrorClusterItemResponse> clusters,
        @JsonProperty("hint") String hint
) {
}
