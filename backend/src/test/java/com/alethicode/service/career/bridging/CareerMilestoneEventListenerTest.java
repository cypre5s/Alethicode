package com.alethicode.service.career.bridging;

import com.alethicode.config.AlethicodeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

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
 *   <li>mastery &lt; 0.7 ⇒ 不触发 KC 簇毕业；</li>
 *   <li>mastery ≥ 0.7 ⇒ 写一条 KC_CLUSTER_GRADUATED；</li>
 *   <li>章节进入 ⇒ 写一条 CHAPTER_ENTERED；</li>
 *   <li>milestone_ref 命名稳定 (lp:&lt;id&gt;:kc:&lt;id&gt; / lp:&lt;id&gt;) 保证幂等键唯一。</li>
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
    void onMasteryUpdatedDoesNotTriggerWhenMasteryBelowThreshold() {
        stubMajorCodeReturns(7L, "biology");
        stubMasteryReturns(7L, 10L, 100L, 0.55);

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedDoesNotTriggerWhenMasteryRowMissing() {
        stubMajorCodeReturns(7L, "biology");
        when(jdbcTemplate.queryForList(
                argThat(sqlContains("from learner_kc_mastery")),
                eq(Double.class), eq(7L), eq(10L), eq(100L)))
                .thenReturn(List.of());

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void onMasteryUpdatedRecordsKcClusterGraduatedAtThreshold() {
        stubMajorCodeReturns(7L, "biology");
        stubMasteryReturns(7L, 10L, 100L, 0.7);

        listener.onMasteryUpdated(7L, 10L, 100L);

        verify(careerBridgingService, times(1)).recordMilestone(
                eq(7L), eq(MilestoneType.KC_CLUSTER_GRADUATED), eq("lp:10:kc:100"));
    }

    @Test
    void onMasteryUpdatedRecordsKcClusterGraduatedAboveThreshold() {
        stubMajorCodeReturns(42L, "psychology");
        stubMasteryReturns(42L, 11L, 200L, 0.92);

        listener.onMasteryUpdated(42L, 11L, 200L);

        verify(careerBridgingService, times(1)).recordMilestone(
                eq(42L), eq(MilestoneType.KC_CLUSTER_GRADUATED), eq("lp:11:kc:200"));
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

    private void stubMasteryReturns(long userId, long lpId, long kcId, double mastery) {
        lenient().when(jdbcTemplate.queryForList(
                        argThat(sqlContains("from learner_kc_mastery")),
                        eq(Double.class), eq(userId), eq(lpId), eq(kcId)))
                .thenReturn(List.of(mastery));
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
