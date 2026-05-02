package com.alethicode.service.aitutor.parsons;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Faded Parsons 配置块（{@code alethicode.parsons.*}）。
 *
 * <p>对应设计稿 ALETH-PLAN-2026-0427-FP01 附录 A.2。</p>
 */
@ConfigurationProperties(prefix = "alethicode.parsons")
public class ParsonsProperties {

    private boolean enabled = true;
    private final FadingThresholds fadingThresholds = new FadingThresholds();
    private final Distractor distractor = new Distractor();
    private final Walkthrough walkthrough = new Walkthrough();
    private final Routing routing = new Routing();
    private final FailureCascade failureCascade = new FailureCascade();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public FadingThresholds getFadingThresholds() { return fadingThresholds; }
    public Distractor getDistractor() { return distractor; }
    public Walkthrough getWalkthrough() { return walkthrough; }
    public Routing getRouting() { return routing; }
    public FailureCascade getFailureCascade() { return failureCascade; }

    public static class FadingThresholds {
        private double level0Max = 0.30;
        private double level1Max = 0.60;
        private double level2Max = 0.85;

        public double getLevel0Max() { return level0Max; }
        public void setLevel0Max(double level0Max) { this.level0Max = level0Max; }
        public double getLevel1Max() { return level1Max; }
        public void setLevel1Max(double level1Max) { this.level1Max = level1Max; }
        public double getLevel2Max() { return level2Max; }
        public void setLevel2Max(double level2Max) { this.level2Max = level2Max; }
    }

    public static class Distractor {
        private double targetNotebookRatio = 0.70;
        private boolean llmFallbackEnabled = true;
        private String llmModel = "deepseek-v4-flash";
        private int maxLlmRetries = 2;
        private double lcsSimilarityThreshold = 0.85;

        public double getTargetNotebookRatio() { return targetNotebookRatio; }
        public void setTargetNotebookRatio(double targetNotebookRatio) { this.targetNotebookRatio = targetNotebookRatio; }
        public boolean isLlmFallbackEnabled() { return llmFallbackEnabled; }
        public void setLlmFallbackEnabled(boolean llmFallbackEnabled) { this.llmFallbackEnabled = llmFallbackEnabled; }
        public String getLlmModel() { return llmModel; }
        public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
        public int getMaxLlmRetries() { return maxLlmRetries; }
        public void setMaxLlmRetries(int maxLlmRetries) { this.maxLlmRetries = maxLlmRetries; }
        public double getLcsSimilarityThreshold() { return lcsSimilarityThreshold; }
        public void setLcsSimilarityThreshold(double lcsSimilarityThreshold) { this.lcsSimilarityThreshold = lcsSimilarityThreshold; }
    }

    public static class Walkthrough {
        private double scoreThreshold = 0.70;
        private String llmModel = "deepseek-v4-flash";
        private int maxRewriteAttempts = 1;

        public double getScoreThreshold() { return scoreThreshold; }
        public void setScoreThreshold(double scoreThreshold) { this.scoreThreshold = scoreThreshold; }
        public String getLlmModel() { return llmModel; }
        public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
        public int getMaxRewriteAttempts() { return maxRewriteAttempts; }
        public void setMaxRewriteAttempts(int maxRewriteAttempts) { this.maxRewriteAttempts = maxRewriteAttempts; }
    }

    public static class Routing {
        private int nfkCoverageThreshold = 20;
        private int minUserInteractions = 5;
        private Duration kcCoverageCacheTtl = Duration.ofHours(1);
        private Duration kcCoverageRefreshInterval = Duration.ofHours(1);

        public int getNfkCoverageThreshold() { return nfkCoverageThreshold; }
        public void setNfkCoverageThreshold(int nfkCoverageThreshold) { this.nfkCoverageThreshold = nfkCoverageThreshold; }
        public int getMinUserInteractions() { return minUserInteractions; }
        public void setMinUserInteractions(int minUserInteractions) { this.minUserInteractions = minUserInteractions; }
        public Duration getKcCoverageCacheTtl() { return kcCoverageCacheTtl; }
        public void setKcCoverageCacheTtl(Duration kcCoverageCacheTtl) { this.kcCoverageCacheTtl = kcCoverageCacheTtl; }
        public Duration getKcCoverageRefreshInterval() { return kcCoverageRefreshInterval; }
        public void setKcCoverageRefreshInterval(Duration kcCoverageRefreshInterval) { this.kcCoverageRefreshInterval = kcCoverageRefreshInterval; }
    }

    public static class FailureCascade {
        private int maxAttemptsBeforeDegrade = 3;
        private int maxAttemptsBeforeFailfast = 4;

        public int getMaxAttemptsBeforeDegrade() { return maxAttemptsBeforeDegrade; }
        public void setMaxAttemptsBeforeDegrade(int maxAttemptsBeforeDegrade) { this.maxAttemptsBeforeDegrade = maxAttemptsBeforeDegrade; }
        public int getMaxAttemptsBeforeFailfast() { return maxAttemptsBeforeFailfast; }
        public void setMaxAttemptsBeforeFailfast(int maxAttemptsBeforeFailfast) { this.maxAttemptsBeforeFailfast = maxAttemptsBeforeFailfast; }
    }
}
