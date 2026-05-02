package com.alethicode.service.aitutor.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 学生提交结果回写到 review-package 的桥（Phase 3 抽离）。
 * 由 ErrorReviewPackageService 的 recordSubmission 委托调用，避免主类承担 SQL 模板。
 */
@Component
class ReviewSubmissionRecorder {

    private final JdbcTemplate jdbcTemplate;
    private final ReviewPackageFsrsAdvancer fsrsAdvancer;

    @Autowired
    ReviewSubmissionRecorder(JdbcTemplate jdbcTemplate, ReviewPackageFsrsAdvancer fsrsAdvancer) {
        this.jdbcTemplate = jdbcTemplate;
        this.fsrsAdvancer = fsrsAdvancer;
    }

    void recordSubmission(Long userId, Long problemId, boolean isCorrect) {
        List<String> packageIds = jdbcTemplate.queryForList(
                """
                select distinct rp.package_id
                from ai_error_review_problem rp
                join ai_error_review_package pkg on pkg.id = rp.package_id
                where rp.problem_id = ? and pkg.user_id = ?
                """,
                String.class, problemId, userId
        );
        jdbcTemplate.update(
                """
                update ai_error_review_problem
                set submitted = true, is_correct = ?
                where problem_id = ? and submitted = false
                  and package_id in (select id from ai_error_review_package where user_id = ?)
                """,
                isCorrect, problemId, userId
        );
        jdbcTemplate.update(
                """
                update ai_error_review_package
                set completed_count = (select count(*) from ai_error_review_problem where package_id = ai_error_review_package.id and submitted = true),
                    mastery_reached = (select count(*) from ai_error_review_problem where package_id = ai_error_review_package.id and submitted = true and is_correct = true) = problem_count and problem_count > 0,
                    all_ac = (select count(*) from ai_error_review_problem where package_id = ai_error_review_package.id and submitted = true and is_correct = true) = problem_count and problem_count > 0,
                    updated_at = now()
                where user_id = ?
                  and id in (select package_id from ai_error_review_problem where problem_id = ? and submitted = true)
                """,
                userId, problemId
        );
        for (String packageId : packageIds) {
            fsrsAdvancer.advance(packageId,
                    isCorrect ? FsrsSchedulerService.ReviewRating.GOOD : FsrsSchedulerService.ReviewRating.AGAIN,
                    Instant.now(), "submission_result", userId);
        }
    }
}
