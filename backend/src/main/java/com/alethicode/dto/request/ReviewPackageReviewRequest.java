package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewPackageReviewRequest(
        @JsonProperty("rating") String rating
) {
}
