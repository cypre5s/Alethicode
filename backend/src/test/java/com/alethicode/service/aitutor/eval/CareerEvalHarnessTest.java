package com.alethicode.service.aitutor.eval;

import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerEvalHarnessTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private RolloutPolicyService rolloutPolicyService;

    @Test
    void evaluateBatchComputesCareerClosureMetricsFromPersistedRows() {
        CareerEvalHarness harness = new CareerEvalHarness(jdbcTemplate, rolloutPolicyService);
        stubCount("career_bridging_report_total", 4);
        stubCount("career_bridging_report_grounded", 3);
        stubCount("career_bridging_report_invalid_refusal", 1);
        stubCount("problem_domain_variant_total", 5);
        stubCount("problem_domain_variant_drifted", 1);
        stubCount("problem_domain_variant_helpful", 4);
        stubCount("career_micro_project_total", 2);
        stubCount("career_micro_project_solvable", 2);
        stubCount("career_micro_project_aligned", 1);
        stubCount("career_path_node_total", 5);
        stubCount("career_path_node_invalid_parent", 0);
        stubCount("career_path_node_factual", 4);

        Map<String, Object> report = harness.evaluateBatch(50);

        assertThat(report).containsEntry("sample_count", 16);
        assertThat(report).containsEntry("grounding_accuracy", 0.75);
        assertThat(report).containsEntry("refusal_accuracy", 0.75);
        assertThat(report).containsEntry("semantic_drift_rate", 0.2);
        assertThat(report).containsEntry("rewrite_helpfulness", 0.8);
        assertThat(report).containsEntry("solvability_rate", 1.0);
        assertThat(report).containsEntry("kc_alignment_accuracy", 0.5);
        assertThat(report).containsEntry("unlock_consistency", 1.0);
        assertThat(report).containsEntry("why_md_factuality", 0.8);
        verify(rolloutPolicyService).evaluateHarnessGate(eq("career"), eq("manual"), eq(report));
    }

    private void stubCount(String marker, int value) {
        when(jdbcTemplate.queryForObject(argThat(sql -> sql != null && sql.contains(marker)), eq(Integer.class)))
                .thenReturn(value);
    }
}
