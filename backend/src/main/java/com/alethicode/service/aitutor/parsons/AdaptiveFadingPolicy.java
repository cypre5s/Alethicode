package com.alethicode.service.aitutor.parsons;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 自适应渐退策略：把多个 KC 的 mastery 平均后映射到 fading level。
 *
 * <p>映射区间由 {@link ParsonsProperties.FadingThresholds} 三个阈值控制：
 * <pre>
 *   avg &lt; level0Max   → fadingLevel=0（只显示，无干扰块）
 *   avg &lt; level1Max   → fadingLevel=1（1 个渐隐块，1 个干扰块）
 *   avg &lt; level2Max   → fadingLevel=2（2 个渐隐块，2 个干扰块）
 *   else                  → fadingLevel=3（微 AST 级别，3 个干扰块）
 * </pre>
 * 失败 cascade 强制阶梯降级时调用方传入 {@code overrideLevel} 跳过 mastery 重算。</p>
 */
@Service
public class AdaptiveFadingPolicy {

    private final ParsonsProperties properties;

    public AdaptiveFadingPolicy(ParsonsProperties properties) {
        this.properties = properties;
    }

    public FadingDecision decide(Map<Long, MasteryWithSource> masteryByKc) {
        double avg = averageMastery(masteryByKc.values());
        return decideForAverage(avg);
    }

    public FadingDecision decideForAverage(double avgMastery) {
        ParsonsProperties.FadingThresholds t = properties.getFadingThresholds();
        if (avgMastery < t.getLevel0Max()) {
            return new FadingDecision(0, 0, 0);
        }
        if (avgMastery < t.getLevel1Max()) {
            return new FadingDecision(1, 1, 1);
        }
        if (avgMastery < t.getLevel2Max()) {
            return new FadingDecision(2, 2, 2);
        }
        return new FadingDecision(3, 3, 3);
    }

    public FadingDecision decideForLevel(int fadingLevel) {
        int level = Math.max(0, Math.min(3, fadingLevel));
        return new FadingDecision(level, level, level);
    }

    private double averageMastery(Collection<MasteryWithSource> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (MasteryWithSource v : values) {
            if (v == null) continue;
            sum += v.mastery();
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }
}
