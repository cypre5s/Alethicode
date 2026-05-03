package com.alethicode.service.twin.timeline;

import com.alethicode.dto.response.twin.LearningTimelineEntry;
import com.alethicode.dto.response.twin.LearningTimelineResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningTimelineServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final LearningTimelineService service = new LearningTimelineService(jdbcTemplate);

    private LearningTimelineEntry makeEntry(String id, String kind) {
        return new LearningTimelineEntry(id, kind, Instant.now(), 1L, "P1", "s", false, Map.of());
    }

    // ===== 正常查询 =====

    @Test void queryReturnsSubmissionEvents() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", "submission")));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of("submission"), 200);
        assertThat(r.events()).hasSize(1);
        assertThat(r.events().get(0).eventKind()).isEqualTo("submission");
    }

    @Test void queryReturnsMemoryEvents() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", "memory")));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of("memory"), 200);
        assertThat(r.events()).hasSize(1);
        assertThat(r.events().get(0).eventKind()).isEqualTo("memory");
    }

    @Test void queryReturnsAiEventEvents() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", "ai_event")));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of("ai_event"), 200);
        assertThat(r.events()).hasSize(1);
    }

    @Test void queryReturnsNotebookEvents() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", "notebook")));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of("notebook"), 200);
        assertThat(r.events()).hasSize(1);
    }

    @Test void queryReturnsMixedEvents() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", "submission"), makeEntry("2", "memory"), makeEntry("3", "ai_event")));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null, 200);
        assertThat(r.events()).hasSize(3);
    }

    // ===== 空结果 =====

    @Test void queryReturnsEmptyWhenNoData() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null, 200);
        assertThat(r.events()).isEmpty();
        assertThat(r.totalCount()).isZero();
        assertThat(r.hasMore()).isFalse();
    }

    @Test void queryWithNullKindsDefaultsToAll() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null, 200);
        assertThat(r).isNotNull();
    }

    @Test void queryWithEmptyKindsDefaultsToAll() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of(), 200);
        assertThat(r).isNotNull();
    }

    // ===== 分页 =====

    @Test void queryReportsHasMoreWhenOverLimit() {
        List<LearningTimelineEntry> entries = List.of(makeEntry("1", "submission"), makeEntry("2", "submission"), makeEntry("3", "submission"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(entries);
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of("submission"), 2);
        assertThat(r.events()).hasSize(2);
        assertThat(r.hasMore()).isTrue();
    }

    @Test void queryReportsNoMoreWhenUnderLimit() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", "submission")));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of("submission"), 200);
        assertThat(r.hasMore()).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {1, 5, 10, 100, 500, 1000})
    void queryRespectsVariousLimits(int limit) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null, limit);
        assertThat(r).isNotNull();
    }

    @Test void queryClampsTooHighLimit() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null, 9999);
        assertThat(r).isNotNull();
    }

    @Test void queryClampsTooLowLimit() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null, -5);
        assertThat(r).isNotNull();
    }

    // ===== 输入校验 =====

    @Test void queryRejectsNullFromDate() {
        assertThatThrownBy(() -> service.query(42L, null, LocalDate.of(2026, 5, 1), null, 200))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid-time-range");
    }

    @Test void queryRejectsNullToDate() {
        assertThatThrownBy(() -> service.query(42L, LocalDate.of(2026, 4, 1), null, null, 200))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid-time-range");
    }

    @Test void queryRejectsBothDatesNull() {
        assertThatThrownBy(() -> service.query(42L, null, null, null, 200))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid-time-range");
    }

    @Test void queryRejectsInvertedRange() {
        assertThatThrownBy(() -> service.query(42L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null, 200))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid-time-range");
    }

    @Test void queryRejectsTooLargeSpan() {
        assertThatThrownBy(() -> service.query(42L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), null, 200))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("time-span-too-large");
    }

    @Test void queryAcceptsExactly365DaySpan() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), null, 200);
        assertThat(r).isNotNull();
    }

    @Test void queryAcceptsSameDayRange() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1), null, 200);
        assertThat(r).isNotNull();
    }

    // ===== Kind 过滤 =====

    @Test void queryWithInvalidKindReturnsEmpty() {
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of("invalid_kind"), 200);
        assertThat(r.events()).isEmpty();
    }

    @Test void queryWithMixedValidInvalidKinds() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", "submission")));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                List.of("submission", "invalid"), 200);
        assertThat(r.events()).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"submission", "memory", "ai_event", "notebook"})
    void queryAcceptsEachValidKindIndividually(String kind) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(makeEntry("1", kind)));
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), List.of(kind), 200);
        assertThat(r.events()).hasSize(1);
    }

    // ===== 跨年边界 =====

    @Test void queryHandlesCrossYearBoundary() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31), null, 200);
        assertThat(r).isNotNull();
    }

    @Test void queryHandlesLeapYear() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        LearningTimelineResponse r = service.query(42L, LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1), null, 200);
        assertThat(r).isNotNull();
    }

    // ===== DTO 常量 =====

    @Test void kindConstantsAreCorrect() {
        assertThat(LearningTimelineEntry.KIND_SUBMISSION).isEqualTo("submission");
        assertThat(LearningTimelineEntry.KIND_MEMORY).isEqualTo("memory");
        assertThat(LearningTimelineEntry.KIND_AI_EVENT).isEqualTo("ai_event");
        assertThat(LearningTimelineEntry.KIND_NOTEBOOK).isEqualTo("notebook");
    }
}
