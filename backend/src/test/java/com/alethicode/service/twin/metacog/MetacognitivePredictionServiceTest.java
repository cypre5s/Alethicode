package com.alethicode.service.twin.metacog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetacognitivePredictionServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MetacognitivePredictionService service = new MetacognitivePredictionService(jdbcTemplate);

    @Test
    void classifyDiffExactMatch() {
        assertThat(service.classifyDiff("42", "42")).isEqualTo("exact_match");
    }

    @Test
    void classifyDiffExactMatchWithWhitespace() {
        assertThat(service.classifyDiff("  42  ", "42")).isEqualTo("exact_match");
    }

    @Test
    void classifyDiffCrashWhenActualHasError() {
        assertThat(service.classifyDiff("42", "Traceback (most recent call last):")).isEqualTo("crash");
    }

    @Test
    void classifyDiffCrashWhenActualHasErrorKeyword() {
        assertThat(service.classifyDiff("hello", "NameError: name 'x' is not defined")).isEqualTo("crash");
    }

    @Test
    void classifyDiffPartialWhenPredictedContainedInActual() {
        assertThat(service.classifyDiff("hello", "hello world")).isEqualTo("partial");
    }

    @Test
    void classifyDiffPartialWhenActualContainedInPredicted() {
        assertThat(service.classifyDiff("hello world", "hello")).isEqualTo("partial");
    }

    @Test
    void classifyDiffWrongValueForDifferentNumbers() {
        assertThat(service.classifyDiff("42", "43")).isEqualTo("wrong_value");
    }

    @Test
    void classifyDiffWrongValueForCompletelyDifferentStrings() {
        assertThat(service.classifyDiff("foo", "bar")).isEqualTo("wrong_value");
    }

    @Test
    void classifyDiffHandlesNullPredicted() {
        assertThat(service.classifyDiff(null, "42")).isEqualTo("wrong_value");
    }

    @Test
    void classifyDiffHandlesNullActual() {
        assertThat(service.classifyDiff("42", null)).isEqualTo("wrong_value");
    }

    @Test
    void classifyDiffEmptyPredicted() {
        assertThat(service.classifyDiff("", "42")).isEqualTo("wrong_value");
    }

    @Test
    void recordPredictionInsertsAndReturnsId() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class),
                anyLong(), anyLong(), anyString(), any(), any(), any()))
                .thenReturn(99L);

        long id = service.recordPrediction(1L, 100L, "42", "因为...", null, null);
        assertThat(id).isEqualTo(99L);
    }

    @Test
    void verifyFetchesPredictedAndClassifies() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(1L)))
                .thenReturn("42");

        service.verify(1L, "42");

        verify(jdbcTemplate).update(anyString(), eq("42"), eq("exact_match"), eq(1L));
    }

    @Test
    void verifyClassifiesCrashCorrectly() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(2L)))
                .thenReturn("hello");

        service.verify(2L, "Traceback (most recent call last): ...");

        verify(jdbcTemplate).update(anyString(),
                eq("Traceback (most recent call last): ..."), eq("crash"), eq(2L));
    }

    @Test
    void getMetacognitiveMapReturnsStatsAndMisconceptions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L)))
                .thenReturn(10)
                .thenReturn(6);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenReturn(List.of(
                        Map.of("diff_kind", "wrong_value", "count", 3),
                        Map.of("diff_kind", "crash", "count", 1)
                ));

        Map<String, Object> result = service.getMetacognitiveMap(42L);
        assertThat(result.get("total_predicts")).isEqualTo(10);
        assertThat(result.get("exact_match_rate")).isEqualTo(0.6);
        assertThat(result.get("hot_misconceptions")).isInstanceOf(List.class);
    }

    @Test
    void getMetacognitiveMapHandlesZeroPredicts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L)))
                .thenReturn(0)
                .thenReturn(0);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenReturn(List.of());

        Map<String, Object> result = service.getMetacognitiveMap(42L);
        assertThat(result.get("total_predicts")).isEqualTo(0);
        assertThat(result.get("exact_match_rate")).isEqualTo(0.0);
    }
}
