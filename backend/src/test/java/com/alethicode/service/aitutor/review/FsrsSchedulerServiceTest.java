package com.alethicode.service.aitutor.review;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FsrsSchedulerServiceTest {

    private final FsrsSchedulerService service = new FsrsSchedulerService();

    @Test
    void initializeShouldReturnDueNowState() {
        Instant now = Instant.parse("2026-04-23T10:00:00Z");

        FsrsSchedulerService.ScheduleState state = service.initialize(now);

        assertThat(state.state()).isEqualTo("new");
        assertThat(state.dueAt()).isEqualTo(now);
        assertThat(state.stability()).isPositive();
        assertThat(state.difficulty()).isPositive();
    }

    @Test
    void successfulReviewShouldPushDueDateForward() {
        Instant now = Instant.parse("2026-04-23T10:00:00Z");
        FsrsSchedulerService.ScheduleState initial = service.initialize(now);

        FsrsSchedulerService.ScheduleState next = service.review(
                initial,
                FsrsSchedulerService.ReviewRating.GOOD,
                now.plusSeconds(3600)
        );

        assertThat(next.state()).isEqualTo("review");
        assertThat(next.dueAt()).isAfter(now.plusSeconds(3600));
        assertThat(next.stability()).isGreaterThan(initial.stability());
        assertThat(next.reps()).isEqualTo(1);
    }

    @Test
    void failedReviewShouldIncreaseLapsesAndShortenInterval() {
        Instant now = Instant.parse("2026-04-23T10:00:00Z");
        FsrsSchedulerService.ScheduleState reviewed = service.review(
                service.initialize(now),
                FsrsSchedulerService.ReviewRating.GOOD,
                now.plusSeconds(3600)
        );

        FsrsSchedulerService.ScheduleState failed = service.review(
                reviewed,
                FsrsSchedulerService.ReviewRating.AGAIN,
                now.plusSeconds(7200)
        );

        assertThat(failed.state()).isEqualTo("relearning");
        assertThat(failed.lapses()).isEqualTo(1);
        assertThat(failed.dueAt()).isAfter(now.plusSeconds(7200));
        assertThat(failed.dueAt()).isBefore(reviewed.dueAt());
    }
}
