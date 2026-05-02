package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClassroomCurrentProblemResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("title") String title
) {
}
