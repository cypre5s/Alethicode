package com.alethicode.service.aitutor.path;

import com.alethicode.service.aitutor.profile.MasteryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LearningPathOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(LearningPathOptimizerService.class);

    private final JdbcTemplate jdbcTemplate;
    private final MasteryService masteryService;

    public LearningPathOptimizerService(JdbcTemplate jdbcTemplate, MasteryService masteryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.masteryService = masteryService;
    }

    public Map<String, Object> computePath(Long userId, Long languagePackId) {
        List<Map<String, Object>> kcs = jdbcTemplate.queryForList("""
            SELECT k.id AS kc_id, k.name AS kc_name, c.chapter_index, c.title AS chapter_title
            FROM language_pack_kc k
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE c.language_pack_id = ?
            ORDER BY c.chapter_index, k.id
            """, languagePackId);

        if (kcs.isEmpty()) {
            return Map.of("path", List.of(), "language_pack_id", languagePackId);
        }

        List<Map<String, Object>> edges = jdbcTemplate.queryForList("""
            SELECT kc_id AS target, prerequisite_kc_id AS source
            FROM language_pack_kc_prerequisite
            WHERE language_pack_id = ?
            """, languagePackId);

        Map<Long, Set<Long>> prerequisites = new LinkedHashMap<>();
        Map<Long, Set<Long>> dependents = new LinkedHashMap<>();
        for (Map<String, Object> kc : kcs) {
            long id = ((Number) kc.get("kc_id")).longValue();
            prerequisites.put(id, new LinkedHashSet<>());
            dependents.put(id, new LinkedHashSet<>());
        }
        for (Map<String, Object> edge : edges) {
            long target = ((Number) edge.get("target")).longValue();
            long source = ((Number) edge.get("source")).longValue();
            if (prerequisites.containsKey(target) && prerequisites.containsKey(source)) {
                prerequisites.get(target).add(source);
                dependents.get(source).add(target);
            }
        }

        List<Long> topoOrder = topologicalSort(kcs, prerequisites, dependents);

        Map<String, Double> masteryByKc = userId != null
                ? masteryService.projectMasteryByLanguagePack(userId, languagePackId)
                : Map.of();

        Map<Long, String> kcNames = new LinkedHashMap<>();
        Map<Long, String> kcChapters = new LinkedHashMap<>();
        for (Map<String, Object> kc : kcs) {
            long id = ((Number) kc.get("kc_id")).longValue();
            kcNames.put(id, (String) kc.get("kc_name"));
            kcChapters.put(id, (String) kc.get("chapter_title"));
        }

        Set<Long> mastered = new LinkedHashSet<>();
        List<Map<String, Object>> path = new ArrayList<>();

        for (Long kcId : topoOrder) {
            String kcName = kcNames.getOrDefault(kcId, "KC-" + kcId);
            double mastery = findMastery(masteryByKc, kcName, kcId);

            boolean prereqsMet = prerequisites.get(kcId).stream().allMatch(mastered::contains);
            String status;
            if (mastery >= 0.7) {
                status = "mastered";
                mastered.add(kcId);
            } else if (prereqsMet) {
                status = "current";
            } else {
                status = "locked";
            }

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("kc_id", kcId);
            node.put("kc_name", kcName);
            node.put("chapter", kcChapters.get(kcId));
            node.put("mastery", Math.round(mastery * 1000.0) / 1000.0);
            node.put("status", status);
            node.put("prerequisites", new ArrayList<>(prerequisites.get(kcId)));
            path.add(node);
        }

        Long nextKcId = path.stream()
                .filter(n -> "current".equals(n.get("status")))
                .min((a, b) -> Double.compare(
                        ((Number) a.get("mastery")).doubleValue(),
                        ((Number) b.get("mastery")).doubleValue()))
                .map(n -> ((Number) n.get("kc_id")).longValue())
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("language_pack_id", languagePackId);
        result.put("user_id", userId);
        result.put("path", path);
        result.put("next_kc_id", nextKcId);
        result.put("mastered_count", mastered.size());
        result.put("total_count", kcs.size());
        return result;
    }

    private List<Long> topologicalSort(List<Map<String, Object>> kcs,
                                        Map<Long, Set<Long>> prerequisites,
                                        Map<Long, Set<Long>> dependents) {
        Map<Long, Integer> inDegree = new LinkedHashMap<>();
        for (Map<String, Object> kc : kcs) {
            long id = ((Number) kc.get("kc_id")).longValue();
            inDegree.put(id, prerequisites.get(id).size());
        }

        List<Long> queue = new ArrayList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<Long> result = new ArrayList<>();
        Set<Long> visited = new LinkedHashSet<>();
        int head = 0;
        while (head < queue.size()) {
            Long current = queue.get(head++);
            result.add(current);
            visited.add(current);
            for (Long dep : dependents.getOrDefault(current, Set.of())) {
                int newDeg = inDegree.get(dep) - 1;
                inDegree.put(dep, newDeg);
                if (newDeg == 0) queue.add(dep);
            }
        }

        for (Map<String, Object> kc : kcs) {
            long id = ((Number) kc.get("kc_id")).longValue();
            if (!visited.contains(id)) result.add(id);
        }

        return result;
    }

    private double findMastery(Map<String, Double> masteryByKc, String kcName, Long kcId) {
        if (masteryByKc.isEmpty()) return 0.0;
        Double direct = masteryByKc.get(kcName);
        if (direct != null) return direct;
        Double byId = masteryByKc.get(String.valueOf(kcId));
        if (byId != null) return byId;
        return 0.0;
    }
}
