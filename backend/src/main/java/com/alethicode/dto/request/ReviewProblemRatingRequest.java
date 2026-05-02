package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phase 3.3：单题级评分请求体。学生在 review-package 上对每道题点「我会了 / 再练一题」。
 */
public record ReviewProblemRatingRequest(
        @JsonProperty("rating") String rating
) {
}
