package com.alethicode.service.aitutor.rlhf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptVariantSelectorTest {

    private JdbcTemplate jdbcTemplate;
    private PromptVariantSelector selector;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        selector = new PromptVariantSelector(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
    }

    @Test
    void selectVariantRejectsEmptyCandidates() {
        assertThatThrownBy(() -> selector.selectVariant("error_diagnosis", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectVariantExploresNewVariantsFirst() {
        String chosen = selector.selectVariant("error_diagnosis", List.of("v1", "v2", "v3"));
        assertThat(chosen).isEqualTo("v1");
    }

    @Test
    void selectVariantPrefersHigherMeanWhenAllExplored() {
        // v1: 10 pulls / 8 positive (mean 0.8), v2: 10 pulls / 2 positive (mean 0.2)
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(
                        makeStatsRow("prompt_variant::error_diagnosis::v1", 10, 8, 2, 1100.0),
                        makeStatsRow("prompt_variant::error_diagnosis::v2", 10, 2, 8, 900.0)
                ));
        String chosen = selector.selectVariant("error_diagnosis", List.of("v1", "v2"));
        assertThat(chosen).isEqualTo("v1");
    }

    @Test
    void recordOutcomeUpdatesEloAndPersistsPayload() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(makeStatsRow("prompt_variant::error_diagnosis::v1", 3, 1, 2, 980.0)));

        selector.recordOutcome("error_diagnosis", "v1", true);

        verify(jdbcTemplate, times(1)).queryForList(contains("FOR UPDATE"), any(Object[].class));
        verify(jdbcTemplate, times(1)).update(
                contains("ON CONFLICT (user_id, memory_key) DO UPDATE SET"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
    }

    @Test
    void recordOutcomeIsTransactionalAndLocksVariantRow() throws Exception {
        Method method = PromptVariantSelector.class.getMethod(
                "recordOutcome", String.class, String.class, boolean.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(makeStatsRow("prompt_variant::error_diagnosis::v1", 0, 0, 0, 1000.0)));

        selector.recordOutcome("error_diagnosis", "v1", true);

        verify(jdbcTemplate).queryForList(contains("FOR UPDATE"), any(Object[].class));
    }

    @Test
    void eloAccumulatesCorrectlyAfterMultiplePositiveOutcomes() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(makeStatsRow("prompt_variant::error_diagnosis::v1", 0, 0, 0, 1000.0)))
                .thenReturn(List.of(makeStatsRow("prompt_variant::error_diagnosis::v1", 1, 1, 0, 1016.0)));

        selector.recordOutcome("error_diagnosis", "v1", true);
        selector.recordOutcome("error_diagnosis", "v1", true);

        ArgumentCaptor<String> memoryValueCaptor = forClass(String.class);
        verify(jdbcTemplate, times(2)).update(
                contains("ON CONFLICT (user_id, memory_key) DO UPDATE SET"),
                any(Object.class), any(Object.class), memoryValueCaptor.capture(), any(Object.class));

        List<String> memoryValues = memoryValueCaptor.getAllValues();
        assertThat(memoryValues.get(0)).startsWith("pulls=1,elo=");
        assertThat(memoryValues.get(1)).startsWith("pulls=2,elo=");
        assertThat(eloFrom(memoryValues.get(1))).isGreaterThan(eloFrom(memoryValues.get(0)));
    }

    @Test
    void listVariantsDecodesPayloadAndTrimsPrefix() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(
                        makeStatsRow("prompt_variant::diagnostics_agent::v1", 4, 3, 1, 1032.0),
                        makeStatsRow("prompt_variant::diagnostics_agent::v2", 2, 1, 1, 1000.0)
                ));

        List<Map<String, Object>> variants = selector.listVariants("diagnostics_agent");

        assertThat(variants).hasSize(2);
        assertThat(variants.get(0)).containsEntry("variant_id", "v1");
        assertThat(variants.get(0)).containsEntry("pulls", 4);
        assertThat(variants.get(0)).containsEntry("positive", 3);
        assertThat(variants.get(0)).containsEntry("elo", 1032.0);
        assertThat(variants.get(1)).containsEntry("variant_id", "v2");
    }

    private static Map<String, Object> makeStatsRow(String key, long pulls, long positive, long negative, double elo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("memory_key", key);
        row.put("payload_text",
                "{\"pulls\":" + pulls + ",\"positive\":" + positive
                        + ",\"negative\":" + negative + ",\"elo\":" + elo + "}");
        row.put("updated_at", "2026-04-17T10:00:00Z");
        return row;
    }

    private static double eloFrom(String memoryValue) {
        int idx = memoryValue.indexOf("elo=");
        return Double.parseDouble(memoryValue.substring(idx + "elo=".length()));
    }

}
