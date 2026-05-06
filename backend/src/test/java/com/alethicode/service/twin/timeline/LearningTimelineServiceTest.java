package com.alethicode.service.twin.timeline;

import com.alethicode.dto.response.twin.LearningTimelineEntry;
import com.alethicode.dto.response.twin.LearningTimelineResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

    @Test
    void queryReturnsSubmissionEvents() {
        LearningTimelineEntry entry = new LearningTimelineEntry(
                "sub-001", "submission", Instant.now(), 1001L, "Hello World",
                "AC 了「Hello World」", false, Map.of("payload_int", 0, "is_ac", true)
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(entry));

        LearningTimelineResponse result = service.query(
                42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                List.of("submission"), 200
        );
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).eventKind()).isEqualTo("submission");
    }

    @Test
    void queryReturnsMemoryEvents() {
        LearningTimelineEntry entry = new LearningTimelineEntry(
                "1", "memory", Instant.now(), 1001L, "循环题",
                "记录了一条misconception（循环题）", false, Map.of("payload_text", "misconception")
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(entry));

        LearningTimelineResponse result = service.query(
                42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                List.of("memory"), 200
        );
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).eventKind()).isEqualTo("memory");
    }

    @Test
    void queryReturnsAllKindsWhenKindsIsNull() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        LearningTimelineResponse result = service.query(
                42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                null, 200
        );
        assertThat(result.events()).isEmpty();
        assertThat(result.totalCount()).isZero();
    }

    @Test
    void queryRejectsNullDates() {
        assertThatThrownBy(() -> service.query(42L, null, LocalDate.of(2026, 5, 1), null, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid-time-range");
    }

    @Test
    void queryRejectsInvertedRange() {
        assertThatThrownBy(() -> service.query(
                42L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null, 200
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid-time-range");
    }

    @Test
    void queryRejectsTooLargeSpan() {
        assertThatThrownBy(() -> service.query(
                42L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), null, 200
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("time-span-too-large");
    }

    @Test
    void queryReportsHasMoreWhenOverLimit() {
        List<LearningTimelineEntry> entries = List.of(
                new LearningTimelineEntry("1", "submission", Instant.now(), 1L, "P1", "s", false, Map.of()),
                new LearningTimelineEntry("2", "submission", Instant.now(), 2L, "P2", "s", false, Map.of()),
                new LearningTimelineEntry("3", "submission", Instant.now(), 3L, "P3", "s", false, Map.of())
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(entries);

        LearningTimelineResponse result = service.query(
                42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                List.of("submission"), 2
        );
        assertThat(result.events()).hasSize(2);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void queryWithInvalidKindReturnsEmpty() {
        LearningTimelineResponse result = service.query(
                42L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                List.of("invalid_kind"), 200
        );
        assertThat(result.events()).isEmpty();
    }
}
