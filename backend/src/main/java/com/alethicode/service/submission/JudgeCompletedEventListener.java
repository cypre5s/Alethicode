package com.alethicode.service.submission;

import com.alethicode.service.submission.SubmissionDataCollector;
import com.alethicode.service.aitutor.profile.LearnerMasteryServiceUnified;
import com.alethicode.service.career.bridging.CareerMilestoneEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class JudgeCompletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(JudgeCompletedEventListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final SubmissionDataCollector submissionDataCollector;
    private final LearnerMasteryServiceUnified masteryService;
    private final CareerMilestoneEventListener careerMilestoneEventListener;
    @Autowired(required = false)
    private com.alethicode.service.aitutor.review.ErrorReviewPackageService errorReviewPackageService;

    public JudgeCompletedEventListener(JdbcTemplate jdbcTemplate,
                                       com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                       SubmissionDataCollector submissionDataCollector,
                                       LearnerMasteryServiceUnified masteryService,
                                       CareerMilestoneEventListener careerMilestoneEventListener) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.submissionDataCollector = submissionDataCollector;
        this.masteryService = masteryService;
        this.careerMilestoneEventListener = careerMilestoneEventListener;
    }

    @Async
    @EventListener
    public void onJudgeCompleted(JudgeCompletedEvent event) {
        handleNotebook(event);
        handleReviewPackage(event);
        handleDataCollection(event);
        handleMasteryUpdate(event);
    }

    private void handleNotebook(JudgeCompletedEvent event) {
        int resultCode = event.finalResult();
        if (resultCode == 0 || resultCode == 6 || resultCode == 7) {
            return;
        }
        try {
            String category = notebookTaxonomyByResult(resultCode);
            String rootCause = sanitizeRootCause(event.errInfo(), category);

            Map<String, Object> evidence = new java.util.LinkedHashMap<>();
            evidence.put("submission_id", event.submissionId());
            evidence.put("problem_display_id", event.problemDisplayId());
            evidence.put("result_code", resultCode);
            evidence.put("captured_at", Instant.now().toString());
            if (event.errInfo() != null && !event.errInfo().isBlank()) {
                evidence.put("err_info", event.errInfo());
            }

            String existingId = jdbcTemplate.query("""
                    select id from ai_learner_notebook
                    where user_id = ? and problem_id = ? and error_taxonomy = ? and is_deleted = false
                    order by update_time desc limit 1
                    """,
                    (rs, rowNum) -> rs.getString("id"),
                    event.userId(), event.problemId(), category
            ).stream().findFirst().orElse(null);

            if (existingId != null) {
                jdbcTemplate.update("""
                        update ai_learner_notebook
                        set language = ?, root_cause = ?, evidence_ptr = cast(? as jsonb), update_time = now()
                        where id = ? and user_id = ?
                        """, event.language(), rootCause, objectMapper.writeValueAsString(evidence), existingId, event.userId());
            } else {
                String id = java.util.UUID.randomUUID().toString().replace("-", "");
                jdbcTemplate.update("""
                        insert into ai_learner_notebook(
                            id, user_id, problem_id, language, error_taxonomy,
                            root_cause, fix_outcome, student_reflection, tags, evidence_ptr,
                            is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, '', '', cast(? as jsonb), cast(? as jsonb), false, now(), now())
                        """, id, event.userId(), event.problemId(), event.language(), category,
                        rootCause, objectMapper.writeValueAsString(List.of(category)), objectMapper.writeValueAsString(evidence));
            }
        } catch (Exception e) {
            log.warn("Notebook auto-write failed for submission {}: {}", event.submissionId(), e.getMessage());
        }
    }

    private void handleReviewPackage(JudgeCompletedEvent event) {
        if (errorReviewPackageService == null) {
            return;
        }
        try {
            errorReviewPackageService.recordSubmission(event.userId(), event.problemId(), event.finalResult() == 0);
        } catch (Exception e) {
            log.warn("Review package record failed for submission {}: {}", event.submissionId(), e.getMessage());
        }
    }

    private void handleDataCollection(JudgeCompletedEvent event) {
        try {
            submissionDataCollector.collect(
                    event.submissionId(), event.userId(), event.problemId(),
                    event.problemDisplayId(), event.problemTitle(),
                    event.language(), event.code(),
                    event.finalResult(), event.judgeResponse(), event.statisticInfo(), Instant.now());
        } catch (Exception e) {
            log.warn("Data collection failed for submission {}: {}", event.submissionId(), e.getMessage());
        }
    }

    private void handleMasteryUpdate(JudgeCompletedEvent event) {
        try {
            List<Map<String, Object>> mappings = jdbcTemplate.queryForList("""
                SELECT lpm.language_pack_id, kc.value::bigint AS kc_id
                FROM language_pack_problem_mapping lpm
                JOIN problem p ON p.id = lpm.problem_id
                CROSS JOIN LATERAL jsonb_array_elements(
                    p.statistic_info->'language_pack_teaching'->'related_kc_ids'
                ) AS kc(value)
                WHERE lpm.problem_id = ?
                  AND p.statistic_info->'language_pack_teaching' IS NOT NULL
                """, event.problemId());
            for (Map<String, Object> m : mappings) {
                Long lpId = ((Number) m.get("language_pack_id")).longValue();
                Long kcId = ((Number) m.get("kc_id")).longValue();
                masteryService.updateMastery(event.userId(), lpId, kcId, event.finalResult() == 0);
                careerMilestoneEventListener.onMasteryUpdated(event.userId(), lpId, kcId);
            }
        } catch (Exception e) {
            log.warn("Mastery update failed for submission {}: {}", event.submissionId(), e.getMessage());
        }
    }

    private String notebookTaxonomyByResult(int resultCode) {
        return switch (resultCode) {
            case -2 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.SYNTAX_ERROR;
            case -1 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.LOGIC_ERROR;
            case 1, 2, 3 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.PERFORMANCE;
            case 4 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.RUNTIME_ERROR;
            default -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.UNKNOWN;
        };
    }

    private String sanitizeRootCause(String errInfo, String category) {
        if (errInfo != null) {
            String normalized = errInfo.strip();
            if (!normalized.isEmpty()
                    && normalized.length() >= 2
                    && !normalized.matches("^[0-9]+$")) {
                return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
            }
        }
        return com.alethicode.service.aitutor.contract.ErrorTaxonomy.label(category);
    }
}
