package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClassroomErrorClusterItemResponse(
        @JsonProperty("error_taxonomy") String errorTaxonomy,
        @JsonProperty("count") long count
) {
}
