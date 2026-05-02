package com.alethicode.service.aitutor.events;

import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.service.aitutor.contract.ErrorTaxonomy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 班级作业提交事件的订阅者（aitutor 包内）。
 *
 * 关键解耦：classroom 域不引用 {@link MasteryService} / {@link ErrorReviewPackageService}，
 * 由 {@link com.alethicode.service.aitutor.events.LearningEventPublisher#publishAssignmentSubmissionGraded}
 * 触发后，本类作为同进程订阅者：
 *   - AC：调 {@link MasteryService#applyEvidence} 写 ai_learning_event，让 mastery 聚合自动反映；
 *   - WA + errorTaxonomy 命中：调 {@link ErrorReviewPackageService#createPackage} 建复习包并把
 *     packageId 写回 classroom_assignment_problem_submission.review_package_id。
 *
 * failfast：异常用 try/catch + 日志 + 计数（不静默吞），但**不**阻断作业提交主链路。
 */
@Service
public class ClassroomAssignmentEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAssignmentEventSubscriber.class);
    private static final String SOURCE = "classroom_assignment";

    private final MasteryService masteryService;
    private final ErrorReviewPackageService errorReviewPackageService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ClassroomAssignmentEventSubscriber(MasteryService masteryService,
                                              @Lazy ErrorReviewPackageService errorReviewPackageService,
                                              JdbcTemplate jdbcTemplate) {
        this.masteryService = masteryService;
        this.errorReviewPackageService = errorReviewPackageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 把作业提交结果落到 mastery / 错题复习包。在 classroom 主事务外的独立事务中执行，
     * 任一步失败不应回滚 classroom 提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Outcome onAssignmentSubmissionGraded(Long userId, String assignmentId, Long problemId,
                                                boolean isCorrect, String errorTaxonomy, Long languagePackId,
                                                String submissionDetailId) {
        if (userId == null || problemId == null || assignmentId == null) {
            log.warn("classroom_assignment_event ignored due to missing identifiers user={} assignment={} problem={}",
                    userId, assignmentId, problemId);
            return Outcome.ignored();
        }
        boolean masteryUpdated = false;
        String reviewPackageId = null;
        try {
            masteryService.applyEvidence(userId, problemId, isCorrect, SOURCE, errorTaxonomy);
            masteryUpdated = true;
        } catch (RuntimeException exception) {
            log.warn("classroom_assignment_event mastery update failed user={} problem={}: {}",
                    userId, problemId, exception.getMessage());
        }

        if (!isCorrect && errorTaxonomy != null && !errorTaxonomy.isBlank()
                && ErrorTaxonomy.isValid(errorTaxonomy) && languagePackId != null) {
            try {
                ReviewPackageResponse pkg = errorReviewPackageService.createPackage(
                        userId, errorTaxonomy, languagePackId, problemId, "wrong_answer");
                if (pkg != null && pkg.id() != null && submissionDetailId != null && !submissionDetailId.isBlank()) {
                    reviewPackageId = pkg.id();
                    jdbcTemplate.update(
                            "update classroom_assignment_problem_submission set review_package_id = ?, error_taxonomy = ? where id = ?",
                            reviewPackageId, errorTaxonomy, submissionDetailId);
                }
            } catch (RuntimeException exception) {
                log.warn("classroom_assignment_event review package creation failed user={} problem={} taxonomy={}: {}",
                        userId, problemId, errorTaxonomy, exception.getMessage());
            }
        } else if (errorTaxonomy != null && !errorTaxonomy.isBlank() && submissionDetailId != null && !submissionDetailId.isBlank()) {
            // 写一笔 error_taxonomy 也算 evidence，但不创建复习包
            try {
                jdbcTemplate.update(
                        "update classroom_assignment_problem_submission set error_taxonomy = ? where id = ?",
                        errorTaxonomy, submissionDetailId);
            } catch (RuntimeException exception) {
                log.warn("classroom_assignment_event taxonomy stamp failed detail={}: {}",
                        submissionDetailId, exception.getMessage());
            }
        }

        return new Outcome(masteryUpdated, reviewPackageId);
    }

    public record Outcome(boolean masteryUpdated, String reviewPackageId) {
        static Outcome ignored() {
            return new Outcome(false, null);
        }
    }
}
