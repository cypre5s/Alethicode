package com.alethicode.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewPackageResponse(
        @JsonProperty("id") String id,
        @JsonProperty("error_taxonomy") String errorTaxonomy,
        @JsonProperty("error_label") String errorLabel,
        @JsonProperty("evidence_summary") Map<String, Object> evidenceSummary,
        @JsonProperty("problem_count") int problemCount,
        @JsonProperty("completed_count") int completedCount,
        @JsonProperty("mastery_reached") boolean masteryReached,
        @JsonProperty("problems") List<ReviewProblemItem> problems,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("fsrs_state") String fsrsState,
        @JsonProperty("due_at") String dueAt,
        @JsonProperty("stability") Double stability,
        @JsonProperty("difficulty") Double difficulty,
        @JsonProperty("retrievability") Double retrievability
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewProblemItem(
            @JsonProperty("id") String id,
            @JsonProperty("problem_id") Long problemId,
            @JsonProperty("problem_key") String problemKey,
            @JsonProperty("title") String title,
            @JsonProperty("sequence") int sequence,
            @JsonProperty("submitted") boolean submitted,
            @JsonProperty("is_correct") Boolean isCorrect,
            @JsonProperty("is_ai_generated") boolean isAiGenerated,
            @JsonProperty("card_type") String cardType,
            @JsonProperty("education_goal") String educationGoal,
            @JsonProperty("why_this_now") String whyThisNow,
            @JsonProperty("target_kcs") List<String> targetKcs,
            @JsonProperty("user_rating") String userRating,
            @JsonProperty("is_unavailable") boolean isUnavailable
    ) {
    }
}
