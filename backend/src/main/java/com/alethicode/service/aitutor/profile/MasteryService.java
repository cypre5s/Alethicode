package com.alethicode.service.aitutor.profile;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.aitutor.nfk.NfkInferenceService;
import com.alethicode.service.aitutor.nfk.NfkInferenceService.NfkInteraction;
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

@Service
public class MasteryService {

    private static final Logger log = LoggerFactory.getLogger(MasteryService.class);
    private static final double PREREQUISITE_BLEND_WEIGHT = 0.2;
    private static final int NFK_MAX_SEQUENCE_LENGTH = 200;

    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private NfkInferenceService nfkInferenceService;
    @Autowired(required = false)
    private AlethicodeProperties properties;

    public MasteryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Double> projectMastery(Long userId, Long problemId) {
        if (problemId == null) {
            return Map.of();
        }
        List<Map<String, Object>> kcs = jdbcTemplate.query(
                """
                select kc.id, kc.name, kc.p_init
                from ai_problem_kc_mapping mapping
                join ai_knowledge_component kc on kc.id = mapping.kc_id
                where mapping.problem_id = ?
                order by mapping.weight desc, kc.id asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("p_init", rs.getDouble("p_init"));
                    return row;
                },
                problemId
        );
        if (kcs.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> idToName = new LinkedHashMap<>();
        for (Map<String, Object> kc : kcs) {
            Long kcId = ((Number) kc.get("id")).longValue();
            idToName.put(kcId, String.valueOf(kc.get("name")));
        }

        Map<String, Double> nfkMastery = tryNfkProjection(userId, problemId, idToName);
        if (nfkMastery != null) {
            applyKcGraphPropagation(userId, idToName, nfkMastery);
            return nfkMastery;
        }

        List<Boolean> outcomes = userId == null ? List.of() : jdbcTemplate.query(
                """
                select is_correct
                from ai_learning_event
                where user_id = ?
                  and problem_id = ?
                  and is_correct is not null
                order by created_at asc
                limit 20
                """,
                (rs, rowNum) -> rs.getBoolean("is_correct"),
                userId,
                problemId
        );

        Map<String, Double> masteryByKc = new LinkedHashMap<>();
        for (Map<String, Object> kc : kcs) {
            String name = String.valueOf(kc.get("name"));
            double mastery = ((Number) kc.get("p_init")).doubleValue();
            for (Boolean outcome : outcomes) {
                mastery = 0.7 * mastery + 0.3 * (Boolean.TRUE.equals(outcome) ? 1.0 : 0.0);
            }
            masteryByKc.put(name, round(mastery));
        }

        applyKcGraphPropagation(userId, idToName, masteryByKc);

        return masteryByKc;
    }

    /**
     * 尝试用 NFK 推理产出当前题目所有 KC 的 mastery。
     *
     * @return NFK 分支成功 → Map；其它情况（禁用 / 不可用 / 无序列 / 推理失败 && 允许回退） → null，调用方回退 BKT
     * @throws IllegalStateException 仅当 NFK 推理失败且 {@code fallbackToBkt=false}
     */
    private Map<String, Double> tryNfkProjection(Long userId, Long problemId, Map<Long, String> idToName) {
        if (userId == null) {
            return null;
        }
        if (nfkInferenceService == null || !nfkInferenceService.isAvailable()) {
            return null;
        }
        try {
            List<NfkInteraction> interactions = loadNfkInteractions(userId, problemId);
            if (interactions.isEmpty()) {
                return null;
            }
            long start = System.currentTimeMillis();
            Map<Long, Double> skillToMastery = nfkInferenceService.predictForSkills(
                    interactions, List.copyOf(idToName.keySet()));
            long elapsedMs = System.currentTimeMillis() - start;
            log.info("NFK inference ok: user={}, problem={}, seq_len={}, elapsed={}ms",
                    userId, problemId, interactions.size(), elapsedMs);
            if (skillToMastery.isEmpty()) {
                return null;
            }
            Map<String, Double> masteryByKc = new LinkedHashMap<>();
            for (Map.Entry<Long, String> entry : idToName.entrySet()) {
                Double prob = skillToMastery.get(entry.getKey());
                if (prob != null) {
                    masteryByKc.put(entry.getValue(), round(prob));
                }
            }
            if (masteryByKc.isEmpty()) {
                return null;
            }
            return masteryByKc;
        } catch (RuntimeException e) {
            boolean fallback = properties == null || properties.getNfk().isFallbackToBkt();
            if (fallback) {
                log.warn("NFK inference failed for user={} problem={}; falling back to BKT: {}",
                        userId, problemId, e.getMessage());
                return null;
            }
            throw e;
        }
    }

    /**
     * 拉取该学生所在课程包里最近 {@value #NFK_MAX_SEQUENCE_LENGTH} 条 submission，
     * 按 {@code create_time ASC} 排序，用 {@code ai_problem_kc_mapping} 的 weight 最大项补齐 skill_id。
     */
    private List<NfkInteraction> loadNfkInteractions(Long userId, Long problemId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            WITH current_pack AS (
                SELECT lpm.language_pack_id
                FROM language_pack_problem_mapping lpm
                WHERE lpm.problem_id = ?
                ORDER BY lpm.create_time DESC
                LIMIT 1
            ),
            primary_kc AS (
                SELECT DISTINCT ON (m.problem_id)
                       m.problem_id,
                       m.kc_id
                FROM ai_problem_kc_mapping m
                JOIN current_pack cp ON cp.language_pack_id = m.language_pack_id
                WHERE m.kc_id IS NOT NULL
                  AND m.weight > 0
                ORDER BY m.problem_id, m.weight DESC, m.kc_id ASC
            )
            SELECT s.problem_id     AS question_id,
                   pk.kc_id         AS skill_id,
                   CASE WHEN s.result = 0 THEN 1 ELSE 0 END AS response,
                   s.create_time    AS ts
            FROM submission s
            JOIN primary_kc pk ON pk.problem_id = s.problem_id
            JOIN language_pack_problem_mapping lpm ON lpm.problem_id = s.problem_id
            JOIN current_pack cp ON cp.language_pack_id = lpm.language_pack_id
            WHERE s.user_id = ?
            ORDER BY s.create_time ASC, s.id ASC
            LIMIT ?
            """, problemId, userId, NFK_MAX_SEQUENCE_LENGTH);

        List<NfkInteraction> interactions = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object tsRaw = row.get("ts");
            double seconds = 0.0;
            if (tsRaw instanceof Timestamp t) {
                seconds = t.getTime() / 1000.0;
            } else if (tsRaw instanceof java.util.Date d) {
                seconds = d.getTime() / 1000.0;
            } else if (tsRaw instanceof Number n) {
                seconds = n.doubleValue();
            }
            long questionId = asLong(row.get("question_id"));
            long skillId = asLong(row.get("skill_id"));
            int response = (int) asLong(row.get("response"));
            interactions.add(new NfkInteraction(questionId, skillId, response, seconds));
        }
        return interactions;
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0L;
        return Long.parseLong(value.toString());
    }

    private void applyKcGraphPropagation(Long userId, Map<Long, String> idToName, Map<String, Double> masteryByKc) {
        if (idToName.isEmpty()) {
            return;
        }
        List<Long> kcIds = List.copyOf(idToName.keySet());
        String placeholders = String.join(",", kcIds.stream().map(id -> "?").toList());

        List<Map<String, Object>> relations = jdbcTemplate.query(
                """
                select r.to_kc_id, r.from_kc_id, r.relation_type, r.weight,
                       from_kc.name as from_kc_name, from_kc.p_init as from_kc_p_init
                from ai_kc_relation r
                join ai_knowledge_component from_kc on from_kc.id = r.from_kc_id
                where r.to_kc_id in (%s)
                  and r.relation_type = 'prerequisite'
                """.formatted(placeholders),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("to_kc_id", rs.getLong("to_kc_id"));
                    row.put("from_kc_id", rs.getLong("from_kc_id"));
                    row.put("weight", rs.getDouble("weight"));
                    row.put("from_kc_name", rs.getString("from_kc_name"));
                    row.put("from_kc_p_init", rs.getDouble("from_kc_p_init"));
                    return row;
                },
                kcIds.toArray()
        );

        if (relations.isEmpty()) {
            return;
        }

        Map<Long, Double> prereqMasteryCache = new LinkedHashMap<>();
        for (Map<String, Object> rel : relations) {
            Long toKcId = ((Number) rel.get("to_kc_id")).longValue();
            Long fromKcId = ((Number) rel.get("from_kc_id")).longValue();
            double relWeight = ((Number) rel.get("weight")).doubleValue();

            String toKcName = idToName.get(toKcId);
            if (toKcName == null) {
                continue;
            }

            double prereqMastery;
            String fromKcName = String.valueOf(rel.get("from_kc_name"));
            if (masteryByKc.containsKey(fromKcName)) {
                prereqMastery = masteryByKc.get(fromKcName);
            } else {
                prereqMastery = prereqMasteryCache.computeIfAbsent(fromKcId, id ->
                        loadSingleKcMastery(userId, id, ((Number) rel.get("from_kc_p_init")).doubleValue())
                );
            }

            double currentMastery = masteryByKc.getOrDefault(toKcName, 0.5);
            double blendFactor = PREREQUISITE_BLEND_WEIGHT * relWeight;
            double blended = (1.0 - blendFactor) * currentMastery + blendFactor * prereqMastery;
            masteryByKc.put(toKcName, round(blended));
        }
    }

    private double loadSingleKcMastery(Long userId, Long kcId, double pInit) {
        if (userId == null) {
            return pInit;
        }
        List<Boolean> outcomes = jdbcTemplate.query(
                """
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
                userId,
                kcId
        );
        double mastery = pInit;
        for (Boolean outcome : outcomes) {
            mastery = 0.7 * mastery + 0.3 * (Boolean.TRUE.equals(outcome) ? 1.0 : 0.0);
        }
        return round(mastery);
    }

    /**
     * 公共写入入口：把一次提交结果写入 {@code ai_learning_event}，作为 BKT/NFK 推理的事实序列源。
     * BKT/NFK 计算保持 lazy 风格在 {@link #projectMastery} 路径里聚合，本方法只负责 ingest 事实。
     */
    public void applyEvidence(Long userId, Long problemId, boolean isCorrect,
                              String source, String errorTaxonomy) {
        if (userId == null || problemId == null) {
            throw new IllegalArgumentException("user_id and problem_id are required");
        }
        String eventType = isCorrect ? "submission_ac" : "submission_wa";
        String normalizedSource = source == null || source.isBlank() ? "generic" : source;
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("source", normalizedSource);
        if (errorTaxonomy != null && !errorTaxonomy.isBlank()) {
            extraData.put("error_taxonomy", errorTaxonomy);
        }
        String extraJson;
        try {
            extraJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extraData);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize ai_learning_event extra_data", exception);
        }
        jdbcTemplate.update(
                """
                insert into ai_learning_event(user_id, problem_id, event_type, extra_data,
                                              is_correct, error_taxonomy, root_cause, detector_name, created_at)
                values (?, ?, ?, cast(? as jsonb), ?, ?, null, null, now())
                """,
                userId, problemId, eventType, extraJson, isCorrect, errorTaxonomy);
    }

    public Map<String, Double> projectMasteryByLanguagePack(Long userId, Long languagePackId) {
        if (userId == null || languagePackId == null) return Map.of();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT k.name AS kc_name, km.mastery AS mastery_value
            FROM learner_kc_mastery km
            JOIN language_pack_kc k ON k.id = km.kc_id
            WHERE km.user_id = ? AND km.language_pack_id = ?
            """, userId, languagePackId);
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("kc_name");
            Number val = (Number) row.get("mastery_value");
            if (name != null && val != null) {
                result.put(name, round(val.doubleValue()));
            }
        }
        return result;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
