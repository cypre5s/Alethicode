package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.aitutor.nfk.NfkInferenceService;
import com.alethicode.service.aitutor.nfk.NfkInferenceService.NfkInteraction;
import com.alethicode.service.aitutor.profile.MasteryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 双闸路由 + NFK fallback 行为单元测试。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>NFK 不可用 → 全 BKT，原因 NFK_UNAVAILABLE</li>
 *   <li>序列长度不足 → 全 BKT，原因 INTERACTION_COUNT</li>
 *   <li>覆盖度低于阈值 → 该 KC 走 BKT，原因 COVERAGE</li>
 *   <li>NFK predict 抛错 → 该 KC 走 BKT，原因 NFK_UNAVAILABLE</li>
 *   <li>混合路由：高覆盖走 NFK、低覆盖走 BKT 同时存在</li>
 *   <li>空 KC 列表直接返回空 Map</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MasteryNfkProjectionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private KcCoverageRegistry kcCoverageRegistry;
    @Mock
    private NfkInferenceService nfkInferenceService;
    @Mock
    private MasteryService masteryService;

    private ParsonsProperties properties;
    private MasteryNfkProjectionService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new ParsonsProperties();
        service = new MasteryNfkProjectionService(jdbcTemplate, kcCoverageRegistry, properties, masteryService);
        injectNfk(service, nfkInferenceService);
    }

    @Test
    void emptyKcIdsReturnsEmptyAndSkipsAllRouting() {
        Map<Long, MasteryWithSource> result = service.getMasteryByKc(1L, List.of());
        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate, kcCoverageRegistry, nfkInferenceService, masteryService);
    }

    @Test
    void unavailableNfkRoutesEverythingToBktWithUnavailableReason() {
        when(nfkInferenceService.isAvailable()).thenReturn(false);
        stubBktForKc(101L, 0.42);
        stubBktForKc(202L, 0.18);

        Map<Long, MasteryWithSource> result = service.getMasteryByKc(7L, List.of(101L, 202L));

        assertThat(result).hasSize(2);
        assertThat(result.get(101L).source()).isEqualTo(MasteryWithSource.Source.BKT);
        assertThat(result.get(101L).fallbackReason()).isEqualTo(MasteryWithSource.FallbackReason.NFK_UNAVAILABLE);
        assertThat(result.get(101L).mastery()).isEqualTo(0.42);
        assertThat(result.get(202L).source()).isEqualTo(MasteryWithSource.Source.BKT);
        assertThat(result.get(202L).fallbackReason()).isEqualTo(MasteryWithSource.FallbackReason.NFK_UNAVAILABLE);
        verifyNoInteractions(kcCoverageRegistry);
    }

    @Test
    void shortInteractionSequenceFallsBackToBktWithInteractionCountReason() {
        when(nfkInferenceService.isAvailable()).thenReturn(true);
        stubInteractionRows(2); // < default 5
        stubBktForKc(101L, 0.55);

        Map<Long, MasteryWithSource> result = service.getMasteryByKc(7L, List.of(101L));

        assertThat(result.get(101L).source()).isEqualTo(MasteryWithSource.Source.BKT);
        assertThat(result.get(101L).fallbackReason()).isEqualTo(MasteryWithSource.FallbackReason.INTERACTION_COUNT);
        verifyNoInteractions(kcCoverageRegistry);
    }

    @Test
    void mixedCoverageRoutesHighCoverageToNfkAndLowCoverageToBkt() {
        when(nfkInferenceService.isAvailable()).thenReturn(true);
        stubInteractionRows(8);
        when(kcCoverageRegistry.getCoverage(101L)).thenReturn(50);
        when(kcCoverageRegistry.getCoverage(202L)).thenReturn(3);
        Map<Long, Double> nfkPrediction = new LinkedHashMap<>();
        nfkPrediction.put(101L, 0.71);
        when(nfkInferenceService.predictForSkills(any(), eq(List.of(101L))))
                .thenReturn(nfkPrediction);
        stubBktForKc(202L, 0.15);

        Map<Long, MasteryWithSource> result = service.getMasteryByKc(7L, List.of(101L, 202L));

        MasteryWithSource forNfk = result.get(101L);
        MasteryWithSource forBkt = result.get(202L);
        assertThat(forNfk.source()).isEqualTo(MasteryWithSource.Source.NFK);
        assertThat(forNfk.mastery()).isEqualTo(0.71);
        assertThat(forNfk.nfkSequenceLength()).isEqualTo(8);
        assertThat(forNfk.fallbackReason()).isNull();
        assertThat(forBkt.source()).isEqualTo(MasteryWithSource.Source.BKT);
        assertThat(forBkt.fallbackReason()).isEqualTo(MasteryWithSource.FallbackReason.COVERAGE);
        assertThat(forBkt.mastery()).isEqualTo(0.15);
    }

    @Test
    void nfkPredictionFailureFallsBackToBktForNfkSlice() {
        when(nfkInferenceService.isAvailable()).thenReturn(true);
        stubInteractionRows(8);
        when(kcCoverageRegistry.getCoverage(101L)).thenReturn(50);
        when(nfkInferenceService.predictForSkills(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("nfk down"));
        stubBktForKc(101L, 0.33);

        Map<Long, MasteryWithSource> result = service.getMasteryByKc(7L, List.of(101L));

        MasteryWithSource v = result.get(101L);
        assertThat(v.source()).isEqualTo(MasteryWithSource.Source.BKT);
        assertThat(v.fallbackReason()).isEqualTo(MasteryWithSource.FallbackReason.NFK_UNAVAILABLE);
        assertThat(v.mastery()).isEqualTo(0.33);
    }

    @Test
    void duplicatedKcIdsAreCollapsedAndProcessedOnce() {
        when(nfkInferenceService.isAvailable()).thenReturn(false);
        stubBktForKc(101L, 0.5);

        Map<Long, MasteryWithSource> result = service.getMasteryByKc(7L, List.of(101L, 101L));

        assertThat(result).hasSize(1);
        verify(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class), eq(101L));
    }

    @SuppressWarnings("unchecked")
    private void stubInteractionRows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question_id", 9000L + i);
            row.put("skill_id", 101L);
            row.put("response", i % 2);
            row.put("ts", new java.sql.Timestamp(1700000000000L + i * 1000L));
            rows.add(row);
        }
        lenient().when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(rows);
    }

    @SuppressWarnings("unchecked")
    private void stubBktForKc(long kcId, double mastery) {
        lenient().when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(kcId)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Double> extractor = invocation.getArgument(1);
                    ResultSet emptyRs = mock(ResultSet.class);
                    when(emptyRs.next()).thenReturn(false);
                    return extractor.extractData(emptyRs);
                });
        lenient().when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(kcId)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Double> extractor = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getDouble("p_init")).thenReturn(mastery);
                    return extractor.extractData(rs);
                });
        lenient().when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq(7L), eq(kcId)))
                .thenReturn(List.of()); // no outcomes ⇒ mastery stays at p_init
    }

    private static void injectNfk(Object target, NfkInferenceService nfk) throws Exception {
        Field f = MasteryNfkProjectionService.class.getDeclaredField("nfkInferenceService");
        f.setAccessible(true);
        f.set(target, nfk);
    }
}
