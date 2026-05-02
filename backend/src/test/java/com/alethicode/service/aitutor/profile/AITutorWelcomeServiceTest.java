package com.alethicode.service.aitutor.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AITutorWelcomeServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AITutorWelcomeService service = new AITutorWelcomeService(jdbcTemplate, new ObjectMapper());

    @Test
    void tagsMapNewTaxonomyFieldToChineseLabel() {
        mockQueries(
                List.of(memoryRow("{\"error_taxonomy\":\"logic_error\"}", 0.85)),
                List.of(),
                List.of()
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat(welcome.get("memory_tags")).isEqualTo(List.of("逻辑错误"));
        assertThat(welcome.get("has_personalization")).isEqualTo(true);
        assertThat((String) welcome.get("greeting")).contains("逻辑错误").doesNotContain("notebook:");
    }

    @Test
    void tagsFallBackToLegacyErrorCategoryField() {
        mockQueries(
                List.of(memoryRow("{\"error_category\":\"wrong_answer\",\"summary\":\"dummy\"}", 0.9)),
                List.of(),
                List.of()
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat(welcome.get("memory_tags")).isEqualTo(List.of("逻辑错误"));
    }

    @Test
    void tagsDeduplicateSameTaxonomyButKeepInsertionOrder() {
        mockQueries(
                List.of(
                        memoryRow("{\"error_taxonomy\":\"logic_error\"}", 0.95),
                        memoryRow("{\"error_taxonomy\":\"boundary_condition\"}", 0.9),
                        memoryRow("{\"error_category\":\"wrong_answer\"}", 0.85)
                ),
                List.of(),
                List.of()
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat(welcome.get("memory_tags")).isEqualTo(List.of("逻辑错误", "边界条件"));
    }

    @Test
    void payloadMissingTaxonomyIsSkippedNotLeakedAsId() {
        mockQueries(
                List.of(memoryRow("{\"summary\":\"错误类型：wrong_answer；根因：0；反思：；修复结果：\"}", 0.85)),
                List.of(),
                List.of()
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat(welcome.get("memory_tags")).isNull();
        assertThat(welcome.get("has_personalization")).isEqualTo(false);
        assertThat((String) welcome.get("greeting"))
                .doesNotContain("notebook:")
                .doesNotContain("根因：0")
                .doesNotContain("反思：");
    }

    @Test
    void unknownTaxonomyIsSkipped() {
        mockQueries(
                List.of(memoryRow("{\"error_taxonomy\":\"totally_made_up_value\"}", 0.85)),
                List.of(),
                List.of()
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat(welcome.get("memory_tags")).isNull();
    }

    @Test
    void malformedPayloadIsSkipped() {
        mockQueries(
                List.of(memoryRow("{not-a-valid-json", 0.85)),
                List.of(),
                List.of()
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat(welcome.get("memory_tags")).isNull();
    }

    @Test
    void starterActionsIncludeKnowledgeReviewWhenWeakKcsExist() {
        mockQueries(
                List.of(),
                List.of(Map.of("name", "循环结构", "mastery", 0.2)),
                List.of()
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat(welcome.get("memory_tags")).isNull();
        assertThat((String) welcome.get("greeting")).contains("循环结构").contains("20%");

        List<Map<String, Object>> starterActions = extractStarterActions(welcome);
        assertThat(starterActions).hasSize(2);
        assertThat(starterActions.get(0))
                .containsEntry("key", "knowledge_review")
                .containsEntry("label", "帮我回顾相关知识点")
                .containsEntry("event", "KNOWLEDGE_REVIEW");
        assertThat(starterActions.get(1))
                .containsEntry("key", "problem_guide")
                .containsEntry("label", "分析这道题的思路")
                .containsEntry("event", "READING");
    }

    @Test
    void starterActionsOmitKnowledgeReviewWhenNoWeakKcs() {
        mockQueries(List.of(), List.of(), List.of());

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        assertThat((String) welcome.get("greeting")).isEqualTo("准备好了吗？有任何疑问随时可以问我～");
        List<Map<String, Object>> starterActions = extractStarterActions(welcome);
        assertThat(starterActions).hasSize(1);
        assertThat(starterActions.get(0))
                .containsEntry("key", "problem_guide")
                .containsEntry("label", "分析这道题的思路")
                .containsEntry("event", "READING");
    }

    @Test
    void starterActionsIncludeErrorChainWhenRecentFailedSubmissionExists() {
        mockQueries(
                List.of(),
                List.of(),
                List.of(Map.of("id", "abc123def456xyz"))
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        List<Map<String, Object>> starterActions = extractStarterActions(welcome);
        assertThat(starterActions).hasSize(2);
        Map<String, Object> errorAction = starterActions.get(1);
        assertThat(errorAction)
                .containsEntry("key", "error_chain")
                .containsEntry("label", "我遇到了错误，帮我看看")
                .containsEntry("event", "ERROR_FEEDBACK");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) errorAction.get("payload");
        assertThat(payload).containsEntry("submission_id", "abc123def456xyz");
    }

    @Test
    void starterActionsIncludeAllThreeWhenWeakKcsAndFailedSubmissionBothExist() {
        mockQueries(
                List.of(),
                List.of(Map.of("name", "循环结构", "mastery", 0.2)),
                List.of(Map.of("id", "sub-uuid-9876"))
        );

        Map<String, Object> welcome = service.getWelcome(42L, 100L);

        List<Map<String, Object>> starterActions = extractStarterActions(welcome);
        assertThat(starterActions).hasSize(3);
        assertThat(starterActions)
                .extracting(a -> a.get("event"))
                .containsExactly("KNOWLEDGE_REVIEW", "READING", "ERROR_FEEDBACK");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractStarterActions(Map<String, Object> welcome) {
        return (List<Map<String, Object>>) welcome.get("starter_actions");
    }

    private void mockQueries(
            List<Map<String, Object>> memories,
            List<Map<String, Object>> weakKcs,
            List<Map<String, Object>> failedSubmissions
    ) {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("ai_learner_memory")) {
                        return memories;
                    }
                    if (sql.contains("ai_problem_kc_mapping")) {
                        return weakKcs;
                    }
                    if (sql.contains("FROM submission")) {
                        return failedSubmissions;
                    }
                    throw new AssertionError("unexpected SQL: " + sql);
                });
    }

    private static Map<String, Object> memoryRow(String payloadJson, double confidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("memory_payload_json", payloadJson);
        row.put("confidence", confidence);
        return row;
    }
}
