package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReviewPackageStatsResponse(
        @JsonProperty("stats") List<TaxonomyStat> stats,
        @JsonProperty("total_packages") long totalPackages,
        @JsonProperty("total_completed") long totalCompleted,
        @JsonProperty("total_mastery") long totalMastery
) {

    public record TaxonomyStat(
            @JsonProperty("error_taxonomy") String errorTaxonomy,
            @JsonProperty("error_label") String errorLabel,
            @JsonProperty("generated") long generated,
            @JsonProperty("completed") long completed,
            @JsonProperty("mastery_reached") long masteryReached,
            @JsonProperty("pending") long pending
    ) {
    }
}
