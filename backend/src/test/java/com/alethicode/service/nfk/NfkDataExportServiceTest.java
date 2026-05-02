package com.alethicode.service.nfk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NfkDataExportServiceTest {

    private JdbcTemplate jdbcTemplate;
    private NfkTrainingRowValidator rowValidator;
    private NfkDataExportService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        rowValidator = new NfkTrainingRowValidator(new ObjectMapper());
        rowValidator.initSchema();
        service = new NfkDataExportService(jdbcTemplate, rowValidator);
    }

    @Test
    void exportTrainingDataWritesCsvHeaderAndRows() {
        Timestamp ts1 = Timestamp.from(Instant.parse("2026-04-10T10:00:00Z"));
        Timestamp ts2 = Timestamp.from(Instant.parse("2026-04-10T10:05:00Z"));
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(
                        row(1L, 100L, 7L, 1, ts1),
                        row(1L, 101L, 7L, 0, ts2),
                        row(2L, 100L, 8L, 1, ts1)
                ));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        service.writeTrainingDataCsv(1L, buffer);

        String csv = buffer.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines[0]).isEqualTo(NfkDataExportService.CSV_HEADER);
        assertThat(lines).hasSize(4);
        assertThat(lines[1]).isEqualTo("1,100,7,1,2026-04-10T10:00:00Z");
        assertThat(lines[2]).isEqualTo("1,101,7,0,2026-04-10T10:05:00Z");
        assertThat(lines[3]).isEqualTo("2,100,8,1,2026-04-10T10:00:00Z");
    }

    @Test
    void exportTrainingDataMatchesRoundTripFixtureByteForByte() throws Exception {
        Timestamp ts1 = Timestamp.from(Instant.parse("2026-04-10T10:00:00Z"));
        Timestamp ts2 = Timestamp.from(Instant.parse("2026-04-10T10:05:00Z"));
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(
                        row(1L, 100L, 7L, 1, ts1),
                        row(1L, 101L, 7L, 0, ts2),
                        row(2L, 100L, 8L, 1, ts1)
                ));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        service.writeTrainingDataCsv(1L, buffer);
        String actual = buffer.toString(StandardCharsets.UTF_8);

        String expected;
        try (var stream = getClass().getResourceAsStream(
                "/contracts/nfk/fixtures/exporter_output_sample.csv")) {
            assertThat(stream)
                    .as("contracts/nfk/fixtures/exporter_output_sample.csv must be on classpath; "
                            + "check backend/pom.xml <resources> includes 'nfk/fixtures/**'")
                    .isNotNull();
            expected = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(actual)
                .as("Java exporter output must match the round-trip fixture byte-for-byte; "
                        + "if you intentionally changed CSV format, also update "
                        + "contracts/nfk/fixtures/exporter_output_sample.csv and Python tests.")
                .isEqualTo(expected);
    }

    @Test
    void exportTrainingDataAcceptsOffsetDateTimeAndConvertsToUtc() {
        OffsetDateTime localPlus8 = OffsetDateTime.of(
                2026, 4, 10, 18, 0, 0, 0, ZoneOffset.ofHours(8));
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(row(1L, 100L, 7L, 1, localPlus8)));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        service.writeTrainingDataCsv(1L, buffer);

        String[] lines = buffer.toString(StandardCharsets.UTF_8).split("\n");
        assertThat(lines[1]).isEqualTo("1,100,7,1,2026-04-10T10:00:00Z");
    }

    @Test
    void exportTrainingDataFailsFastWhenResponseOutOfRange() {
        Timestamp ts = Timestamp.from(Instant.parse("2026-04-10T10:00:00Z"));
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(row(1L, 100L, 7L, 2, ts)));

        assertThatThrownBy(() -> service.writeTrainingDataCsv(1L, new ByteArrayOutputStream()))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("row 1")
                .hasMessageContaining("response");
    }

    @Test
    void exportTrainingDataFailsFastWhenSkillIdNonPositive() {
        Timestamp ts = Timestamp.from(Instant.parse("2026-04-10T10:00:00Z"));
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(row(1L, 100L, 0L, 1, ts)));

        assertThatThrownBy(() -> service.writeTrainingDataCsv(1L, new ByteArrayOutputStream()))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("skill_id");
    }

    @Test
    void exportTrainingDataFailsFastWhenTimestampIsNull() {
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(row(1L, 100L, 7L, 1, null)));

        assertThatThrownBy(() -> service.writeTrainingDataCsv(1L, new ByteArrayOutputStream()))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void exportTrainingDataFailsFastWhenTimestampHasUnsupportedType() {
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(row(1L, 100L, 7L, 1, "not-a-time")));

        assertThatThrownBy(() -> service.writeTrainingDataCsv(1L, new ByteArrayOutputStream()))
                .isInstanceOf(NfkTrainingRowValidationException.class)
                .hasMessageContaining("timestamp must be");
    }

    @Test
    void exportTrainingDataRejectsInvalidPackId() {
        assertThatThrownBy(() -> service.writeTrainingDataCsv(0L, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.writeTrainingDataCsv(null, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computeReadinessReturnsColdWhenStudentCountLow() {
        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put("problem_count", 20L);
        aggregates.put("covered_count", 5L);
        aggregates.put("kc_count", 6L);
        aggregates.put("student_count", 3L);
        aggregates.put("interaction_count", 40L);
        when(jdbcTemplate.queryForMap(anyString(), anyLong())).thenReturn(aggregates);

        Map<String, Object> readiness = service.computeReadiness(42L);

        assertThat(readiness.get("language_pack_id")).isEqualTo(42L);
        assertThat(readiness.get("student_count")).isEqualTo(3L);
        assertThat(readiness.get("problem_count")).isEqualTo(20L);
        assertThat(readiness.get("covered_problem_count")).isEqualTo(5L);
        assertThat(readiness.get("kc_count")).isEqualTo(6L);
        assertThat(readiness.get("interaction_count")).isEqualTo(40L);
        assertThat(readiness.get("kc_coverage")).isEqualTo(0.25);
        assertThat(readiness.get("readiness_level")).isEqualTo("COLD");
    }

    @Test
    void computeReadinessReturnsWarmWhenAboveWarmThresholds() {
        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put("problem_count", 20L);
        aggregates.put("covered_count", 10L);
        aggregates.put("kc_count", 6L);
        aggregates.put("student_count", 12L);
        aggregates.put("interaction_count", 250L);
        when(jdbcTemplate.queryForMap(anyString(), anyLong())).thenReturn(aggregates);

        Map<String, Object> readiness = service.computeReadiness(7L);

        assertThat(readiness.get("readiness_level")).isEqualTo("WARM");
        assertThat(readiness.get("kc_coverage")).isEqualTo(0.5);
    }

    @Test
    void computeReadinessReturnsHotWhenAboveHotThresholds() {
        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put("problem_count", 50L);
        aggregates.put("covered_count", 40L);
        aggregates.put("kc_count", 12L);
        aggregates.put("student_count", 35L);
        aggregates.put("interaction_count", 1200L);
        when(jdbcTemplate.queryForMap(anyString(), anyLong())).thenReturn(aggregates);

        Map<String, Object> readiness = service.computeReadiness(99L);

        assertThat(readiness.get("readiness_level")).isEqualTo("HOT");
        assertThat(readiness.get("kc_coverage")).isEqualTo(0.8);
    }

    @Test
    void computeReadinessRejectsInvalidPackId() {
        assertThatThrownBy(() -> service.computeReadiness(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> row(long userId, long questionId, long skillId, int response, Object ts) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", userId);
        row.put("question_id", questionId);
        row.put("skill_id", skillId);
        row.put("response", response);
        row.put("ts", ts);
        return row;
    }
}
