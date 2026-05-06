package com.alethicode.service.career.bridging;

import com.alethicode.config.AlethicodeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CareerMilestoneEventListener} 单测 —— 覆盖 plan 3.1 节 + todo 10 接入合约：
 * <ul>
 *   <li>career.bridging.enabled=false ⇒ 跳过；</li>
 *   <li>user_profile 缺失 / major_code 空 ⇒ 跳过（非 career 学生）；</li>
 *   <li>KC 无 chapter（散落 KC）⇒ 不触发 KC 簇毕业；</li>
 *   <li>chapter 内 KC mastery 均值 &lt; 0.7 ⇒ 不触发；</li>
 *   <li>chapter 内 KC mastery 均值 ≥ 0.7 ⇒ 写一条 KC_CLUSTER_GRADUATED；</li>
 *   <li>milestone_ref 命名稳定 (lp:&lt;id&gt;:chapter:&lt;id&gt; / lp:&lt;id&gt;) 保证幂等键唯一。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CareerMilestoneEventListenerTest {

    @Mock
    private CareerBridgingService careerBridgingService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private AlethicodeProperties properties;
    private CareerMilestoneEventListener listener;

    @BeforeEach
    void setUp() {
        properties = new AlethicodeProperties();
        properties.getCareer().getBridging().setEnabled(true);
        listener = new CareerMilestoneEventListener(careerBridgingService, properties, jdbcTemplate);
    }

    @Test
    void onMasteryUpdatedSkipsWhenCareerBridgingDisabled() {
        properties.getCareer().getBridging().setEnabled(false);

        listener.onMasteryUpdated(1L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(String.class), any());
    }

    @Test
    void onMasteryUpdatedSkipsWhenUserHasNoCareerProfile() {
        stubMajorCodeReturns(7L, null);

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedSkipsWhenUserProfileRowMissing() {
        when(jdbcTemplate.queryForList(
                argThat(sqlContains("from user_profile")),
                eq(String.class), eq(7L)))
                .thenReturn(List.of());

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedSkipsWhenKcHasNoChapter() {
        stubMajorCodeReturns(7L, "biology");
        // KC 行存在但 chapter_id NULL（散落 KC，不参与簇聚合）
        Map<String, Object> kcRow = new LinkedHashMap<>();
        kcRow.put("chapter_id", null);
        stubChapterIdQuery(100L, List.of(kcRow));

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedSkipsWhenKcRowMissing() {
        stubMajorCodeReturns(7L, "biology");
        stubChapterIdQuery(100L, List.of());

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedDoesNotTriggerWhenChapterAverageBelowThreshold() {
        stubMajorCodeReturns(7L, "biology");
        Map<String, Object> kcRow = new LinkedHashMap<>();
        kcRow.put("chapter_id", 33L);
        stubChapterIdQuery(100L, List.of(kcRow));
        stubChapterAverageReturns(7L, 10L, 33L, 0.55);

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedDoesNotTriggerWhenChapterAverageNull() {
        stubMajorCodeReturns(7L, "biology");
        Map<String, Object> kcRow = new LinkedHashMap<>();
        kcRow.put("chapter_id", 33L);
        stubChapterIdQuery(100L, List.of(kcRow));
        stubChapterAverageReturns(7L, 10L, 33L, null);

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedRecordsKcClusterGraduatedAtThreshold() {
        stubMajorCodeReturns(7L, "biology");
        Map<String, Object> kcRow = new LinkedHashMap<>();
        kcRow.put("chapter_id", 33L);
        stubChapterIdQuery(100L, List.of(kcRow));
        stubChapterAverageReturns(7L, 10L, 33L, 0.7);

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, times(1)).recordMilestone(
                eq(7L), eq(MilestoneType.KC_CLUSTER_GRADUATED), eq("lp:10:chapter:33"));
    }

    @Test
    void onMasteryUpdatedRecordsKcClusterGraduatedAboveThreshold() {
        stubMajorCodeReturns(42L, "psychology");
        Map<String, Object> kcRow = new LinkedHashMap<>();
        kcRow.put("chapter_id", 88L);
        stubChapterIdQuery(200L, List.of(kcRow));
        stubChapterAverageReturns(42L, 11L, 88L, 0.92);

        listener.onMasteryUpdated(42L, 11L, 200L);

        verify(careerBridgingService, times(1)).recordMilestone(
                eq(42L), eq(MilestoneType.KC_CLUSTER_GRADUATED), eq("lp:11:chapter:88"));
    }

    @Test
    void onLanguagePackEnteredSkipsWhenDisabled() {
        properties.getCareer().getBridging().setEnabled(false);

        listener.onLanguagePackEntered(7L, 10L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onLanguagePackEnteredSkipsWhenNoCareerProfile() {
        stubMajorCodeReturns(7L, "");

        listener.onLanguagePackEntered(7L, 10L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onLanguagePackEnteredRecordsChapterEntered() {
        stubMajorCodeReturns(7L, "biology");

        listener.onLanguagePackEntered(7L, 33L);

        verify(careerBridgingService, times(1)).recordMilestone(
                eq(7L), eq(MilestoneType.CHAPTER_ENTERED), eq("lp:33"));
    }

    // ---------- helpers ----------

    private void stubMajorCodeReturns(long userId, String majorCode) {
        lenient().when(jdbcTemplate.queryForList(
                        argThat(sqlContains("from user_profile")),
                        eq(String.class), eq(userId)))
                .thenReturn(majorCode == null ? List.of() : List.of(majorCode));
    }

    private void stubChapterIdQuery(long kcId, List<Map<String, Object>> rows) {
        lenient().when(jdbcTemplate.queryForList(
                        argThat(sqlContains("from language_pack_kc")),
                        eq(kcId)))
                .thenReturn(rows);
    }

    private void stubChapterAverageReturns(long userId, long lpId, long chapterId, Double avg) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from learner_kc_mastery")),
                        eq(Double.class), eq(userId), eq(lpId), eq(chapterId)))
                .thenReturn(avg);
    }

    private static SqlMatcher sqlContains(String fragment) {
        return new SqlMatcher(fragment);
    }

    private static class SqlMatcher implements org.mockito.ArgumentMatcher<String> {
        private final String fragment;

        SqlMatcher(String fragment) {
            this.fragment = fragment;
        }

        @Override
        public boolean matches(String argument) {
            return argument != null && argument.toLowerCase().contains(fragment.toLowerCase());
        }

        @Override
        public String toString() {
            return "sqlContains(" + fragment + ")";
        }
    }
}
