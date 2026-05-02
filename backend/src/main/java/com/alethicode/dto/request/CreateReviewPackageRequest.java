package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateReviewPackageRequest(
        @JsonProperty("error_taxonomy") String errorTaxonomy,
        @JsonProperty("language_pack_id") Long languagePackId,
        @JsonProperty("problem_id") Long problemId,
        @JsonProperty("trigger") String trigger
) {
}
