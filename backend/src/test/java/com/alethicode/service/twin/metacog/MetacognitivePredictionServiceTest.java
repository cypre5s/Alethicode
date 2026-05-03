package com.alethicode.service.twin.metacog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    // ===== classifyDiff: exact match =====
    @Test void classifyDiffExactMatch() { assertThat(service.classifyDiff("42", "42")).isEqualTo("exact_match"); }
    @Test void classifyDiffExactMatchWithWhitespace() { assertThat(service.classifyDiff("  42  ", "42")).isEqualTo("exact_match"); }
    @Test void classifyDiffExactMatchMultiline() { assertThat(service.classifyDiff("hello\nworld", "hello\nworld")).isEqualTo("exact_match"); }
    @Test void classifyDiffExactMatchEmpty() { assertThat(service.classifyDiff("", "")).isEqualTo("wrong_value"); }
    @Test void classifyDiffExactMatchSpaceOnly() { assertThat(service.classifyDiff("   ", "")).isEqualTo("wrong_value"); }

    // ===== classifyDiff: crash =====
    @Test void classifyDiffCrashTraceback() { assertThat(service.classifyDiff("42", "Traceback (most recent call last):")).isEqualTo("crash"); }
    @Test void classifyDiffCrashNameError() { assertThat(service.classifyDiff("hello", "NameError: name 'x' is not defined")).isEqualTo("crash"); }
    @Test void classifyDiffCrashTypeError() { assertThat(service.classifyDiff("1", "TypeError: unsupported operand")).isEqualTo("crash"); }
    @Test void classifyDiffCrashIndexError() { assertThat(service.classifyDiff("[1,2]", "IndexError: list index out of range")).isEqualTo("crash"); }
    @Test void classifyDiffCrashSyntaxError() { assertThat(service.classifyDiff("ok", "SyntaxError: invalid syntax")).isEqualTo("crash"); }
    @Test void classifyDiffCrashValueError() { assertThat(service.classifyDiff("5", "ValueError: invalid literal")).isEqualTo("crash"); }

    // ===== classifyDiff: partial =====
    @Test void classifyDiffPartialSubstring() { assertThat(service.classifyDiff("hello", "hello world")).isEqualTo("partial"); }
    @Test void classifyDiffPartialReverse() { assertThat(service.classifyDiff("hello world", "hello")).isEqualTo("partial"); }
    @Test void classifyDiffPartialOverlap() { assertThat(service.classifyDiff("[1, 2, 3]", "[1, 2, 3, 4]")).isEqualTo("partial"); }

    // ===== classifyDiff: wrong_value =====
    @Test void classifyDiffWrongValueNumbers() { assertThat(service.classifyDiff("42", "43")).isEqualTo("wrong_value"); }
    @Test void classifyDiffWrongValueStrings() { assertThat(service.classifyDiff("foo", "bar")).isEqualTo("wrong_value"); }
    @Test void classifyDiffWrongValueDifferentTypes() { assertThat(service.classifyDiff("True", "1")).isEqualTo("wrong_value"); }

    // ===== classifyDiff: null handling =====
    @Test void classifyDiffNullPredicted() { assertThat(service.classifyDiff(null, "42")).isEqualTo("wrong_value"); }
    @Test void classifyDiffNullActual() { assertThat(service.classifyDiff("42", null)).isEqualTo("wrong_value"); }
    @Test void classifyDiffBothNull() { assertThat(service.classifyDiff(null, null)).isEqualTo("wrong_value"); }

    // ===== classifyDiff: edge cases =====
    @Test void classifyDiffLongStrings() {
        String a = "x".repeat(10000); String b = "y".repeat(10000);
        assertThat(service.classifyDiff(a, b)).isEqualTo("wrong_value");
    }
    @Test void classifyDiffUnicode() { assertThat(service.classifyDiff("你好", "你好")).isEqualTo("exact_match"); }
    @Test void classifyDiffUnicodeDiff() { assertThat(service.classifyDiff("你好", "世界")).isEqualTo("wrong_value"); }
    @Test void classifyDiffFloatNumbers() { assertThat(service.classifyDiff("3.14", "3.15")).isEqualTo("wrong_value"); }
    @Test void classifyDiffNegativeNumbers() { assertThat(service.classifyDiff("-1", "-2")).isEqualTo("wrong_value"); }
    @Test void classifyDiffSpecialChars() { assertThat(service.classifyDiff("a\tb", "a b")).isEqualTo("wrong_value"); }

    // ===== recordPrediction =====
    @Test void recordPredictionInsertsRow() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong(), anyLong(), anyString(), any(), any(), any()))
                .thenReturn(99L);
        long id = service.recordPrediction(1L, 100L, "42", "test", null, null);
        assertThat(id).isEqualTo(99L);
    }

    @Test void recordPredictionWithAllFields() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(42L);
        long id = service.recordPrediction(1L, 100L, "42", "because", "print(42)", "session-1");
        assertThat(id).isEqualTo(42L);
    }

    // ===== verify =====
    @Test void verifyExactMatch() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(1L))).thenReturn("42");
        service.verify(1L, "42");
        verify(jdbcTemplate).update(anyString(), eq("42"), eq("exact_match"), eq(1L));
    }

    @Test void verifyCrash() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(2L))).thenReturn("hello");
        service.verify(2L, "Traceback ...");
        verify(jdbcTemplate).update(anyString(), eq("Traceback ..."), eq("crash"), eq(2L));
    }

    @Test void verifyPartial() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(3L))).thenReturn("hello");
        service.verify(3L, "hello world");
        verify(jdbcTemplate).update(anyString(), eq("hello world"), eq("partial"), eq(3L));
    }

    // ===== getMetacognitiveMap =====
    @Test void mapWithPredicts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(10).thenReturn(6);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of(Map.of("diff_kind", "wrong_value", "count", 3)));
        Map<String, Object> r = service.getMetacognitiveMap(42L);
        assertThat(r.get("total_predicts")).isEqualTo(10);
        assertThat(r.get("exact_match_rate")).isEqualTo(0.6);
    }

    @Test void mapWithZeroPredicts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(0).thenReturn(0);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of());
        Map<String, Object> r = service.getMetacognitiveMap(42L);
        assertThat(r.get("exact_match_rate")).isEqualTo(0.0);
    }

    @Test void mapWithNullCounts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(null).thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of());
        Map<String, Object> r = service.getMetacognitiveMap(42L);
        assertThat(r.get("total_predicts")).isEqualTo(0);
    }

    @Test void mapWith100PercentAccuracy() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(5).thenReturn(5);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L))).thenReturn(List.of());
        Map<String, Object> r = service.getMetacognitiveMap(42L);
        assertThat(r.get("exact_match_rate")).isEqualTo(1.0);
    }
}
