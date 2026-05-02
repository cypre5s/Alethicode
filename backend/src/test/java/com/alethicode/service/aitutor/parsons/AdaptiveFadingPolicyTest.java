package com.alethicode.service.aitutor.parsons;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdaptiveFadingPolicy 单元测试：四档阈值映射、边界值、override 路径。
 *
 * <p>设计稿 §9.4 阈值默认 0.30 / 0.60 / 0.85。低于 level0Max 全 visible，
 * 区间 [level0Max, level1Max) 给 1 faded + 1 distractor，依此类推；
 * fadingLevel 与 fadedCount / distractorCount 在默认配置下相等。</p>
 */
class AdaptiveFadingPolicyTest {

    private AdaptiveFadingPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AdaptiveFadingPolicy(new ParsonsProperties());
    }

    @Test
    void decideReturnsLevel0WhenMasteryBelowLowerThreshold() {
        Map<Long, MasteryWithSource> low = singleKc(0.05);
        FadingDecision d = policy.decide(low);
        assertThat(d).isEqualTo(new FadingDecision(0, 0, 0));
    }

    @Test
    void decideReturnsLevel1WhenMasteryInLowMidRange() {
        FadingDecision d = policy.decide(singleKc(0.45));
        assertThat(d).isEqualTo(new FadingDecision(1, 1, 1));
    }

    @Test
    void decideReturnsLevel2WhenMasteryInMidHighRange() {
        FadingDecision d = policy.decide(singleKc(0.75));
        assertThat(d).isEqualTo(new FadingDecision(2, 2, 2));
    }

    @Test
    void decideReturnsLevel3WhenMasteryAboveTopThreshold() {
        FadingDecision d = policy.decide(singleKc(0.95));
        assertThat(d).isEqualTo(new FadingDecision(3, 3, 3));
    }

    @Test
    void decideAveragesAcrossMultipleKcs() {
        Map<Long, MasteryWithSource> mixed = new LinkedHashMap<>();
        mixed.put(1L, MasteryWithSource.bkt(0.20, MasteryWithSource.FallbackReason.COVERAGE));
        mixed.put(2L, MasteryWithSource.bkt(0.40, MasteryWithSource.FallbackReason.COVERAGE));
        // 平均 0.30 落在 level1 区间（>= 0.30）
        FadingDecision d = policy.decide(mixed);
        assertThat(d).isEqualTo(new FadingDecision(1, 1, 1));
    }

    @Test
    void decideForLevelClampsOutsideOfZeroToThree() {
        assertThat(policy.decideForLevel(-5)).isEqualTo(new FadingDecision(0, 0, 0));
        assertThat(policy.decideForLevel(99)).isEqualTo(new FadingDecision(3, 3, 3));
        assertThat(policy.decideForLevel(2)).isEqualTo(new FadingDecision(2, 2, 2));
    }

    @Test
    void decideOnEmptyMasteryFallsBackToLevel0() {
        FadingDecision d = policy.decide(Map.of());
        assertThat(d).isEqualTo(new FadingDecision(0, 0, 0));
    }

    private Map<Long, MasteryWithSource> singleKc(double mastery) {
        Map<Long, MasteryWithSource> m = new LinkedHashMap<>();
        m.put(1L, MasteryWithSource.bkt(mastery, MasteryWithSource.FallbackReason.COVERAGE));
        return m;
    }
}
