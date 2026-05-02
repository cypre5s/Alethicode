package com.alethicode.service.admin;

import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Lazy
public class BulkPushReviewPackageService {

    private static final Logger log = LoggerFactory.getLogger(BulkPushReviewPackageService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ErrorReviewPackageService errorReviewPackageService;

    @Autowired
    public BulkPushReviewPackageService(JdbcTemplate jdbcTemplate, ErrorReviewPackageService errorReviewPackageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.errorReviewPackageService = errorReviewPackageService;
    }

    public Map<String, Object> pushToClassroom(String classroomId, String errorTaxonomy, Long languagePackId) {
        List<Long> targetUserIds = jdbcTemplate.query(
                """
                SELECT DISTINCT cm.user_id
                FROM classroom_member cm
                JOIN ai_learner_notebook aln ON aln.user_id = cm.user_id
                WHERE cm.classroom_id = ?
                  AND aln.error_taxonomy = ?
                  AND aln.is_deleted = FALSE
                  AND aln.entry_type = 'error'
                  AND NOT EXISTS (
                      SELECT 1 FROM ai_error_review_package pkg
                      WHERE pkg.user_id = cm.user_id
                        AND pkg.error_taxonomy = ?
                        AND pkg.mastery_reached = TRUE
                  )
                """,
                (rs, rowNum) -> rs.getLong("user_id"),
                classroomId, errorTaxonomy, errorTaxonomy
        );

        int successCount = 0;
        List<String> failures = new ArrayList<>();

        for (Long userId : targetUserIds) {
            try {
                errorReviewPackageService.createPackage(userId, errorTaxonomy, languagePackId, null, "teacher_push");
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to create review package for user={}, taxonomy={}: {}", userId, errorTaxonomy, e.getMessage());
                failures.add("user " + userId + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("target_count", targetUserIds.size());
        result.put("success_count", successCount);
        result.put("failures", failures);
        return result;
    }
}
