package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.aitutor.nfk.NfkInferenceService;
import com.alethicode.service.aitutor.nfk.NfkInferenceService.NfkInteraction;
import com.alethicode.service.aitutor.profile.MasteryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-KC NFK / BKT 双闸路由（设计稿创新点 1）：
 * <ol>
 *   <li>第零闸：NFK ONNX 不可用 → 全部 BKT，原因 {@code NFK_UNAVAILABLE}</li>
 *   <li>第二闸：学生最近 50 条相关交互 &lt; {@code minUserInteractions} → 全部 BKT，原因 {@code INTERACTION_COUNT}</li>
 *   <li>第一闸：单 KC 在 NFK 训练集覆盖 &lt; {@code nfkCoverageThreshold} → 该 KC 走 BKT，原因 {@code COVERAGE}</li>
 * </ol>
 * <p>三闸都过的 KC 才进 NFK 推理，避免 padding 后的低可信结果污染 fading 决策。</p>
 *
 * <p>BKT 兜底使用 {@link MasteryService} 的 KC 名称投影：本服务接受 KC ID 列表，
 * 反查 {@code ai_knowledge_component} 名称后再从 {@code learner_kc_mastery} 拉值，
 * 不会重复 BKT 计算逻辑。</p>
 */
@Service
public class MasteryNfkProjectionService {

    private static final Logger log = LoggerFactory.getLogger(MasteryNfkProjectionService.class);
    private static final int MAX_SEQUENCE_LENGTH = 50;

    private final JdbcTemplate jdbcTemplate;
    private final KcCoverageRegistry kcCoverageRegistry;
    private final ParsonsProperties parsonsProperties;
    @Autowired(required = false)
    private NfkInferenceService nfkInferenceService;
    private final MasteryService masteryService;

    public MasteryNfkProjectionService(
            JdbcTemplate jdbcTemplate,
            KcCoverageRegistry kcCoverageRegistry,
            ParsonsProperties parsonsProperties,
            MasteryService masteryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.kcCoverageRegistry = kcCoverageRegistry;
        this.parsonsProperties = parsonsProperties;
        this.masteryService = masteryService;
    }

    public Map<Long, MasteryWithSource> getMasteryByKc(long userId, List<Long> kcIds) {
        if (kcIds == null || kcIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctKcIds = kcIds.stream().distinct().toList();

        if (nfkInferenceService == null || !nfkInferenceService.isAvailable()) {
            return getAllByBkt(userId, distinctKcIds, MasteryWithSource.FallbackReason.NFK_UNAVAILABLE);
        }

        List<NfkInteraction> seq = buildInteractionSequence(userId, distinctKcIds);
        int minInteractions = parsonsProperties.getRouting().getMinUserInteractions();
        if (seq.size() < minInteractions) {
            log.debug("Parsons mastery routing: seq={} < min={} → BKT all", seq.size(), minInteractions);
            return getAllByBkt(userId, distinctKcIds, MasteryWithSource.FallbackReason.INTERACTION_COUNT);
        }

        int coverageThreshold = parsonsProperties.getRouting().getNfkCoverageThreshold();
        List<Long> nfkKcs = new ArrayList<>();
        List<Long> bktKcs = new ArrayList<>();
        for (Long kc : distinctKcIds) {
            int coverage = kcCoverageRegistry.getCoverage(kc);
            if (coverage >= coverageThreshold) {
                nfkKcs.add(kc);
            } else {
                bktKcs.add(kc);
            }
        }

        Map<Long, MasteryWithSource> result = new LinkedHashMap<>();
        if (!nfkKcs.isEmpty()) {
            try {
                Map<Long, Double> nfkMastery = nfkInferenceService.predictForSkills(seq, nfkKcs);
                for (Long kc : nfkKcs) {
                    Double prob = nfkMastery.get(kc);
                    if (prob != null) {
                        result.put(kc, MasteryWithSource.nfk(roundProb(prob), seq.size()));
                    } else {
                        // KC 在 NFK 训练 vocab 之外或本序列里没出现，按 coverage 兜底
                        result.put(kc, MasteryWithSource.bkt(
                                bktSingle(userId, kc),
                                MasteryWithSource.FallbackReason.COVERAGE));
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Parsons NFK predict failed; falling back to BKT for kcs={}: {}", nfkKcs, e.getMessage());
                for (Long kc : nfkKcs) {
                    result.put(kc, MasteryWithSource.bkt(
                            bktSingle(userId, kc),
                            MasteryWithSource.FallbackReason.NFK_UNAVAILABLE));
                }
            }
        }
        for (Long kc : bktKcs) {
            result.put(kc, MasteryWithSource.bkt(
                    bktSingle(userId, kc),
                    MasteryWithSource.FallbackReason.COVERAGE));
        }
        return result;
    }

    private Map<Long, MasteryWithSource> getAllByBkt(long userId, List<Long> kcIds,
                                                     MasteryWithSource.FallbackReason reason) {
        Map<Long, MasteryWithSource> result = new LinkedHashMap<>();
        for (Long kc : kcIds) {
            result.put(kc, MasteryWithSource.bkt(bktSingle(userId, kc), reason));
        }
        return result;
    }

    private double bktSingle(long userId, long kcId) {
        // 与 MasteryService.loadSingleKcMastery 一致的 EMA 计算，但显式按 KC ID
        Double pInit = jdbcTemplate.query(
                "select p_init from ai_knowledge_component where id = ?",
                rs -> rs.next() ? rs.getDouble("p_init") : 0.5,
                kcId
        );
        double mastery = pInit == null ? 0.5 : pInit;
        List<Boolean> outcomes = jdbcTemplate.query("""
                select recent.is_correct
                from (
                    select le.is_correct, le.created_at
                    from ai_learning_event le
                    join ai_problem_kc_mapping m on m.problem_id = le.problem_id
                    where le.user_id = ?
                      and m.kc_id = ?
                      and le.is_correct is not null
                    order by le.created_at desc
                    limit 10
                ) recent
                order by recent.created_at asc
                """,
                (rs, rowNum) -> rs.getBoolean("is_correct"),
                userId, kcId);
        for (Boolean outcome : outcomes) {
            mastery = 0.7 * mastery + 0.3 * (Boolean.TRUE.equals(outcome) ? 1.0 : 0.0);
        }
        return roundProb(mastery);
    }

    private List<NfkInteraction> buildInteractionSequence(long userId, List<Long> kcIds) {
        if (kcIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", kcIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>(kcIds.size() + 1);
        args.add(userId);
        args.addAll(kcIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(("""
                SELECT s.problem_id   AS question_id,
                       m.kc_id        AS skill_id,
                       CASE WHEN s.result = 0 THEN 1 ELSE 0 END AS response,
                       s.create_time  AS ts
                FROM submission s
                JOIN ai_problem_kc_mapping m ON m.problem_id = s.problem_id
                WHERE s.user_id = ?
                  AND m.kc_id IN (%s)
                ORDER BY s.create_time DESC, s.id DESC
                LIMIT %d
                """).formatted(placeholders, MAX_SEQUENCE_LENGTH), args.toArray());
        // 反转得到时间升序
        List<NfkInteraction> seq = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = rows.get(i);
            long questionId = ((Number) row.get("question_id")).longValue();
            long skillId = ((Number) row.get("skill_id")).longValue();
            int response = ((Number) row.get("response")).intValue();
            Object ts = row.get("ts");
            double seconds = 0.0;
            if (ts instanceof Timestamp t) {
                seconds = t.getTime() / 1000.0;
            } else if (ts instanceof java.util.Date d) {
                seconds = d.getTime() / 1000.0;
            } else if (ts instanceof Number n) {
                seconds = n.doubleValue();
            }
            seq.add(new NfkInteraction(questionId, skillId, response, seconds));
        }
        return seq;
    }

    private static double roundProb(double v) {
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return Math.round(v * 1000.0) / 1000.0;
    }
}
