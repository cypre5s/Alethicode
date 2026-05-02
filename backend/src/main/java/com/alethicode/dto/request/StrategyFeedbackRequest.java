package com.alethicode.dto.request;

public record StrategyFeedbackRequest(
        String strategyType,
        String rating
) {
}
