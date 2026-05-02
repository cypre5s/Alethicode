package com.alethicode.service.aitutor.parsons;

/**
 * 单个 KC 的 mastery 投影结果，附带数据源与回退原因，用于
 * Parsons {@code mastery_snapshot.routing} 字段。mastery 概率 ∈ [0, 1]。
 */
public record MasteryWithSource(
        double mastery,
        Source source,
        Integer nfkSequenceLength,
        FallbackReason fallbackReason
) {
    public enum Source {
        NFK, BKT;

        public String key() {
            return name().toLowerCase();
        }
    }

    public enum FallbackReason {
        COVERAGE,
        INTERACTION_COUNT,
        NFK_UNAVAILABLE;

        public String key() {
            return name().toLowerCase();
        }
    }

    public static MasteryWithSource nfk(double mastery, int sequenceLength) {
        return new MasteryWithSource(mastery, Source.NFK, sequenceLength, null);
    }

    public static MasteryWithSource bkt(double mastery, FallbackReason reason) {
        return new MasteryWithSource(mastery, Source.BKT, null, reason);
    }
}
