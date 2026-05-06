package com.alethicode.service.aitutor.eval;

import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Career Bridging Closure 离线评测器。
 *
 * <p>本评测器只读取已持久化产物，给出可复现的结构化指标：
 * Why 报告 grounding、Coding Lens drift、Studio 可解性、Path Map 解锁一致性。
 */
@Service
public class CareerEvalHarness {

    private static final Logger log = LoggerFactory.getLogger(CareerEvalHarness.class);

    private final JdbcTemplate jdbcTemplate;
    private final RolloutPolicyService rolloutPolicyService;

    public CareerEvalHarness(JdbcTemplate jdbcTemplate, RolloutPolicyService rolloutPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.rolloutPolicyService = rolloutPolicyService;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void scheduledEvaluation() {
        try {
            Map<String, Object> report = evaluateBatch(100, "scheduled");
            log.info("CareerEvalHarness: scheduled evaluation complete, sample_count={}", report.get("sample_count"));
        } catch (Exception e) {
            log.warn("CareerEvalHarness: scheduled evaluation failed: {}", e.getMessage());
        }
    }

    public Map<String, Object> evaluateBatch(int limit) {
        return evaluateBatch(limit, "manual");
    }

    private Map<String, Object> evaluateBatch(int limit, String source) {
        int careerBridgingTotal = count("""
                /* career_bridging_report_total */
                select count(*) from career_bridging_report
                """);
        int careerBridgingGrounded = count("""
                /* career_bridging_report_grounded */
                select count(*) from career_bridging_report
                where reflection_passed = true
                  and title is not null and length(trim(title)) > 0
                  and content_md is not null and length(trim(content_md)) > 0
                  and jsonb_typeof(citations) = 'array'
                  and jsonb_array_length(citations) > 0
                """);
        int careerBridgingInvalidRefusal = count("""
                /* career_bridging_report_invalid_refusal */
                select count(*) from career_bridging_report
                where major_code is null or length(trim(major_code)) = 0
                   or title is null or length(trim(title)) = 0
                   or content_md is null or length(trim(content_md)) = 0
                """);

        int domainVariantTotal = count("""
                /* problem_domain_variant_total */
                select count(*) from problem_domain_variant
                """);
        int domainVariantDrifted = count("""
                /* problem_domain_variant_drifted */
                select count(*) from problem_domain_variant
                where coalesce(semantic_drift_score, 1.0) > 0.05
                   or reflection_passed = false
                """);
        int domainVariantHelpful = count("""
                /* problem_domain_variant_helpful */
                select count(*) from problem_domain_variant
                where reflection_passed = true
                  and title is not null and length(trim(title)) > 0
                  and description_md is not null and length(trim(description_md)) > 0
                """);

        int microProjectTotal = count("""
                /* career_micro_project_total */
                select count(*) from career_micro_project
                """);
        int microProjectSolvable = count("""
                /* career_micro_project_solvable */
                select count(*) from career_micro_project
                where judge_problem_id is not null
                """);
        int microProjectAligned = count("""
                /* career_micro_project_aligned */
                select count(*) from career_micro_project
                where jsonb_typeof(related_kcs) = 'array'
                  and jsonb_array_length(related_kcs) > 0
                """);

        int pathNodeTotal = count("""
                /* career_path_node_total */
                select count(*) from career_path_node
                """);
        int pathNodeInvalidParent = count("""
                /* career_path_node_invalid_parent */
                select count(*) from career_path_node child
                where child.parent_kc_code is not null
                  and not exists (
                      select 1 from career_path_node parent
                      where parent.major_code = child.major_code
                        and parent.kc_code = child.parent_kc_code
                  )
                """);
        int pathNodeFactual = count("""
                /* career_path_node_factual */
                select count(*) from career_path_node
                where why_md is not null and length(trim(why_md)) > 0
                  and jsonb_typeof(typical_use_cases) = 'array'
                  and jsonb_array_length(typical_use_cases) > 0
                """);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sample_count", careerBridgingTotal + domainVariantTotal + microProjectTotal + pathNodeTotal);
        report.put("grounding_accuracy", rate(careerBridgingGrounded, careerBridgingTotal));
        report.put("refusal_accuracy", rate(careerBridgingTotal - careerBridgingInvalidRefusal, careerBridgingTotal));
        report.put("semantic_drift_rate", rate(domainVariantDrifted, domainVariantTotal));
        report.put("rewrite_helpfulness", rate(domainVariantHelpful, domainVariantTotal));
        report.put("solvability_rate", rate(microProjectSolvable, microProjectTotal));
        report.put("kc_alignment_accuracy", rate(microProjectAligned, microProjectTotal));
        report.put("unlock_consistency", rate(pathNodeTotal - pathNodeInvalidParent, pathNodeTotal));
        report.put("why_md_factuality", rate(pathNodeFactual, pathNodeTotal));
        report.put("limit", Math.max(1, Math.min(limit, 500)));
        report.put("methodology", "persisted_artifact_structural_metrics");

        rolloutPolicyService.evaluateHarnessGate("career", source, report);
        return report;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(((double) numerator / denominator) * 1000.0) / 1000.0;
    }
}
