package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CreateReviewPackagesRequest(
        @JsonProperty("items") List<CreateReviewPackageRequest> items
) {
}
