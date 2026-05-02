package com.alethicode.service.aitutor.review;

import com.alethicode.service.aitutor.events.LearningEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 包级 FSRS 推进与发布事件的组合（Phase 3 抽离）。
 * 由 ErrorReviewPackageService 与 ReviewProblemRatingService 共用。
 */
@Component
class ReviewPackageFsrsAdvancer {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;
    private final FsrsSchedulerService fsrsSchedulerService;
    private final LearningEventPublisher learningEventPublisher;

    @Autowired
    ReviewPackageFsrsAdvancer(JdbcTemplate jdbcTemplate,
                              FsrsSchedulerService fsrsSchedulerService,
                              LearningEventPublisher learningEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.fsrsSchedulerService = fsrsSchedulerService;
        this.learningEventPublisher = learningEventPublisher;
    }

    ReviewPackageFsrsAdvancer(JdbcTemplate jdbcTemplate, FsrsSchedulerService fsrsSchedulerService) {
        this(jdbcTemplate, fsrsSchedulerService, LearningEventPublisher.NOOP);
    }

    FsrsSchedulerService.ScheduleState advance(String packageId,
                                               FsrsSchedulerService.ReviewRating rating,
                                               Instant reviewedAt, String reason, Long userId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                select fsrs_state, fsrs_due_at, fsrs_stability, fsrs_difficulty,
                       fsrs_retrievability, fsrs_reps, fsrs_lapses, fsrs_last_review_at
                from ai_error_review_package where id = ?
                """,
                packageId
        );
        FsrsSchedulerService.ScheduleState current = new FsrsSchedulerService.ScheduleState(
                stringValue(row.get("fsrs_state")).isBlank() ? "new" : stringValue(row.get("fsrs_state")),
                toInstant(row.get("fsrs_due_at")),
                asDouble(row.get("fsrs_stability"), 0.4),
                asDouble(row.get("fsrs_difficulty"), 5.0),
                asDouble(row.get("fsrs_retrievability"), 1.0),
                ((Number) row.getOrDefault("fsrs_reps", 0)).intValue(),
                ((Number) row.getOrDefault("fsrs_lapses", 0)).intValue(),
                toInstant(row.get("fsrs_last_review_at"))
        );
        FsrsSchedulerService.ScheduleState next = fsrsSchedulerService.review(current, rating, reviewedAt);
        jdbcTemplate.update(
                """
                update ai_error_review_package
                set fsrs_state = ?, fsrs_due_at = ?, fsrs_stability = ?, fsrs_difficulty = ?,
                    fsrs_retrievability = ?, fsrs_reps = ?, fsrs_lapses = ?, fsrs_last_review_at = ?, updated_at = now()
                where id = ?
                """,
                next.state(), Timestamp.from(next.dueAt()),
                next.stability(), next.difficulty(), next.retrievability(),
                next.reps(), next.lapses(), Timestamp.from(next.lastReviewAt()), packageId
        );
        learningEventPublisher.publishReviewPackageUpdated(userId, packageId, reason, fsrsPayload(next));
        return next;
    }

    private Map<String, Object> fsrsPayload(FsrsSchedulerService.ScheduleState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fsrs_state", state.state());
        payload.put("due_at", formatInstant(state.dueAt()));
        payload.put("stability", state.stability());
        payload.put("difficulty", state.difficulty());
        payload.put("retrievability", state.retrievability());
        payload.put("reps", state.reps());
        payload.put("lapses", state.lapses());
        if (state.lastReviewAt() != null) payload.put("last_review_at", formatInstant(state.lastReviewAt()));
        return payload;
    }

    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private double asDouble(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }
    private Instant toInstant(Object value) { return value instanceof Timestamp t ? t.toInstant() : null; }
    private String formatInstant(Instant instant) { return instant == null ? null : ISO_FORMATTER.format(instant.atOffset(ZoneOffset.UTC)); }
}
