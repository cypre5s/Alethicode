package com.alethicode.service.aitutor.profile;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.aitutor.nfk.NfkInferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasteryServiceNfkTest {

    private JdbcTemplate jdbcTemplate;
    private NfkInferenceService nfkInferenceService;
    private AlethicodeProperties properties;
    private MasteryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        nfkInferenceService = mock(NfkInferenceService.class);
        properties = new AlethicodeProperties();
        properties.getNfk().setEnabled(true);
        properties.getNfk().setFallbackToBkt(true);

        service = new MasteryService(jdbcTemplate);
        ReflectionTestUtils.setField(service, "nfkInferenceService", nfkInferenceService);
        ReflectionTestUtils.setField(service, "properties", properties);

        List<Map<String, Object>> kcRows = new ArrayList<>();
        kcRows.add(kcRow(7L, "循环", 0.2));
        kcRows.add(kcRow(9L, "条件判断", 0.3));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object.class)))
                .thenReturn(kcRows, List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object.class), any(Object.class)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void nfkPathReturnsMasteryFromInference() {
        when(nfkInferenceService.isAvailable()).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong(), any(Integer.class)))
                .thenReturn(List.of(
                        submissionRow(100L, 7L, 1, 1_712_000_000L),
                        submissionRow(101L, 9L, 0, 1_712_001_000L)
                ));
        when(nfkInferenceService.predictForSkills(anyList(), anyList()))
                .thenReturn(Map.of(7L, 0.82, 9L, 0.45));

        Map<String, Double> mastery = service.projectMastery(42L, 100L);

        assertThat(mastery).containsEntry("循环", 0.82);
        assertThat(mastery).containsEntry("条件判断", 0.45);
        verify(nfkInferenceService).predictForSkills(anyList(), anyList());
    }

    @Test
    void fallsBackToBktWhenNfkUnavailable() {
        when(nfkInferenceService.isAvailable()).thenReturn(false);

        Map<String, Double> mastery = service.projectMastery(42L, 100L);

        assertThat(mastery).containsOnlyKeys("循环", "条件判断");
        verify(nfkInferenceService, org.mockito.Mockito.never())
                .predictForSkills(anyList(), anyList());
    }

    @Test
    void fallsBackToBktWhenNfkThrowsAndFallbackIsEnabled() {
        when(nfkInferenceService.isAvailable()).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong(), any(Integer.class)))
                .thenReturn(List.of(submissionRow(100L, 7L, 1, 1_712_000_000L)));
        when(nfkInferenceService.predictForSkills(anyList(), anyList()))
                .thenThrow(new IllegalStateException("ort down"));

        Map<String, Double> mastery = service.projectMastery(42L, 100L);

        assertThat(mastery).containsOnlyKeys("循环", "条件判断");
    }

    @Test
    void emptySubmissionSequenceSkipsNfkAndUsesBkt() {
        when(nfkInferenceService.isAvailable()).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong(), any(Integer.class)))
                .thenReturn(List.of());

        Map<String, Double> mastery = service.projectMastery(42L, 100L);

        assertThat(mastery).containsOnlyKeys("循环", "条件判断");
        verify(nfkInferenceService, org.mockito.Mockito.never())
                .predictForSkills(anyList(), anyList());
    }

    @Test
    void nfkInteractionQueryFiltersPrimaryKcByCurrentLanguagePackInsideCte() {
        when(nfkInferenceService.isAvailable()).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong(), any(Integer.class)))
                .thenReturn(List.of());

        service.projectMastery(42L, 100L);

        ArgumentCaptor<String> sqlCaptor = forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(100L), eq(42L), any(Integer.class));
        assertThat(sqlCaptor.getValue())
                .contains("JOIN current_pack cp ON cp.language_pack_id = m.language_pack_id");
    }

    private static Map<String, Object> kcRow(long id, String name, double pInit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("p_init", pInit);
        return m;
    }

    private static Map<String, Object> submissionRow(long problemId, long skillId, int response, long tsSeconds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("question_id", problemId);
        m.put("skill_id", skillId);
        m.put("response", response);
        m.put("ts", new java.sql.Timestamp(tsSeconds * 1000L));
        return m;
    }
}
