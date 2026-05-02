package com.alethicode.service.aitutor.review;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class FsrsSchedulerService {

    private static final double MIN_STABILITY = 0.4;
    private static final double MAX_DIFFICULTY = 10.0;
    private static final double MIN_DIFFICULTY = 1.0;

    public ScheduleState initialize(Instant now) {
        return new ScheduleState(
                "new",
                now,
                MIN_STABILITY,
                5.0,
                1.0,
                0,
                0,
                null
        );
    }

    public ScheduleState review(ScheduleState current, ReviewRating rating, Instant reviewedAt) {
        ScheduleState safeCurrent = current == null ? initialize(reviewedAt) : current;
        double elapsedDays = safeCurrent.lastReviewAt() == null
                ? 0.0
                : Math.max(0.0, Duration.between(safeCurrent.lastReviewAt(), reviewedAt).toSeconds() / 86400.0);
        double retrievability = safeCurrent.lastReviewAt() == null
                ? 1.0
                : Math.exp(-elapsedDays / Math.max(safeCurrent.stability(), MIN_STABILITY));

        if (rating == ReviewRating.AGAIN) {
            double nextDifficulty = clamp(safeCurrent.difficulty() + 0.8, MIN_DIFFICULTY, MAX_DIFFICULTY);
            double nextStability = Math.max(MIN_STABILITY, safeCurrent.stability() * 0.45);
            return new ScheduleState(
                    "relearning",
                    reviewedAt.plus(Duration.ofHours(12)),
                    nextStability,
                    nextDifficulty,
                    retrievability,
                    safeCurrent.reps(),
                    safeCurrent.lapses() + 1,
                    reviewedAt
            );
        }

        double nextDifficulty = clamp(
                safeCurrent.difficulty() - ("new".equals(safeCurrent.state()) ? 0.6 : 0.25),
                MIN_DIFFICULTY,
                MAX_DIFFICULTY
        );
        double nextStability = "new".equals(safeCurrent.state())
                ? 1.5
                : safeCurrent.stability() * (1.3 + (1.0 - retrievability) * 1.4);
        long dueHours = Math.max(24L, Math.round(nextStability * 24.0));
        return new ScheduleState(
                "review",
                reviewedAt.plus(Duration.ofHours(dueHours)),
                nextStability,
                nextDifficulty,
                retrievability,
                safeCurrent.reps() + 1,
                safeCurrent.lapses(),
                reviewedAt
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum ReviewRating {
        AGAIN,
        GOOD;

        public static ReviewRating from(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("rating is required");
            }
            return switch (raw.trim().toLowerCase()) {
                case "again" -> AGAIN;
                case "good" -> GOOD;
                default -> throw new IllegalArgumentException("Unsupported FSRS review rating: " + raw);
            };
        }
    }

    public record ScheduleState(
            String state,
            Instant dueAt,
            double stability,
            double difficulty,
            double retrievability,
            int reps,
            int lapses,
            Instant lastReviewAt
    ) {
    }
}
