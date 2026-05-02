package com.alethicode.service.aitutor.profile;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearnerNarrativeSummaryServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AiModelGateway gateway = mock(AiModelGateway.class);
    private final LearnerMemoryService memoryService = mock(LearnerMemoryService.class);
    private final LearnerNarrativeSummaryService service =
            new LearnerNarrativeSummaryService(jdbc, gateway, memoryService, new ObjectMapper());

    @Test
    void loadOrGenerateReturnsEmptyWhenUserIdIsNull() {
        LearnerNarrativeSummaryService.NarrativeSummary result = service.loadOrGenerate(null);
        assertThat(result.summaryText()).isEmpty();
        verify(gateway, never()).callForJson(anyString(), anyString());
    }

    @Test
    void loadOrGenerateGeneratesWhenRowMissing() throws Exception {
        when(memoryService.inferLearningStyle(anyLong())).thenReturn(LearningStyle.STEP_BY_STEP);
        // First call (loadRow) throws, then after generateAndPersist, second call returns the new row.
        ResultSet rs = mockResultSet("近 30 天小明做了 5 道题", 1, false, false);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), anyLong()))
                .thenThrow(new EmptyResultDataAccessException(1))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });
        // Materials collection
        when(jdbc.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenReturn(5L, 3L, 100L);
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of());
        when(gateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "summary_text", "近 30 天小明做了 5 道题，AC 3 道。",
                "top_kcs", List.of("for_loop"),
                "top_errors", List.of("range_boundary")
        ));

        LearnerNarrativeSummaryService.NarrativeSummary result = service.loadOrGenerate(42L);

        assertThat(result.summaryText()).contains("近 30 天小明");
        verify(gateway, times(1)).callForJson(anyString(), anyString());
    }

    @Test
    void loadOrGenerateSkipsLlmWhenRowExists() throws Exception {
        when(memoryService.inferLearningStyle(anyLong())).thenReturn(LearningStyle.STEP_BY_STEP);
        ResultSet rs = mockResultSet("既有摘要", 7, false, false);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), anyLong()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });

        LearnerNarrativeSummaryService.NarrativeSummary result = service.loadOrGenerate(99L);

        assertThat(result.summaryText()).isEqualTo("既有摘要");
        assertThat(result.version()).isEqualTo(7);
        verify(gateway, never()).callForJson(anyString(), anyString());
    }

    @Test
    void overrideSummaryUpsertsAndMarksUserOverridden() {
        when(memoryService.inferLearningStyle(anyLong())).thenReturn(LearningStyle.ANALYTICAL);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.overrideSummary(42L, "我是 ANALYTICAL，请用严谨推理");

        verify(jdbc).update(anyString(),
                eq(42L),
                eq("我是 ANALYTICAL，请用严谨推理"),
                eq("analytical"));
    }

    @Test
    void disablePersonalizationUpdatesFlagWithoutLlm() {
        when(jdbc.update(anyString(), eq(true), eq(42L))).thenReturn(1);

        service.disablePersonalization(42L);

        verify(jdbc).update(anyString(), eq(true), eq(42L));
        verify(gateway, never()).callForJson(anyString(), anyString());
    }

    @Test
    void refreshIfStaleSkipsWhenUserOverridden() throws Exception {
        ResultSet rs = mockResultSet("学生改写", 3, true, false);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), anyLong()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });

        LearnerNarrativeSummaryService.NarrativeSummary result = service.refreshIfStale(42L);

        assertThat(result.userOverridden()).isTrue();
        verify(gateway, never()).callForJson(anyString(), anyString());
    }

    private ResultSet mockResultSet(String summaryText, int version, boolean userOverridden, boolean userDisabled) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("user_id")).thenReturn(42L);
        when(rs.getInt("summary_version")).thenReturn(version);
        when(rs.getString("summary_text")).thenReturn(summaryText);
        when(rs.getString("payload_json")).thenReturn("{}");
        when(rs.getString("learning_style_key")).thenReturn("step_by_step");
        when(rs.getObject("last_event_id")).thenReturn(0L);
        when(rs.getString("last_session_id")).thenReturn(null);
        when(rs.getBoolean("is_user_overridden")).thenReturn(userOverridden);
        when(rs.getBoolean("user_disabled")).thenReturn(userDisabled);
        Timestamp now = Timestamp.from(Instant.now());
        when(rs.getTimestamp("created_at")).thenReturn(now);
        when(rs.getTimestamp("updated_at")).thenReturn(now);
        return rs;
    }
}
