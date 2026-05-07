package com.alethicode.service.classroom.ai;

import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Classroom 智能组卷薄适配器。
 *
 * - 拉班级所有 student 的 mastery 摘要（{@link MasteryService#projectMasteryByLanguagePack}）
 * - 聚合班级薄弱 KC TOP-K（教师未指定 target_kc_ids 时自动推断）
 * - 对每个学生（实际只取代表生）调 {@link BeginnerSupplementPlannerService#buildPlan}
 *   "daily_review" trigger 拿 coding_problem / objective_problem 卡片
 * - 去重 problem_id，按 KC 分组组装 sections
 *
 * 不直接 SQL 选题；不直接写入 mastery / 复习包；KC 校验委托 {@link ClassroomKcResolver}。
 */
@Service
public class ClassroomAssignmentSmartComposer {

    private static final double WEAK_THRESHOLD = 0.5;
    private static final int DEFAULT_PER_STUDENT_BUDGET = 3;
    private static final int DEFAULT_TOTAL_BUDGET = 8;
    private static final String TRIGGER = "daily_review";

    private final JdbcTemplate jdbcTemplate;
    private final ClassroomKcResolver classroomKcResolver;
    private final MasteryService masteryService;
    private final BeginnerSupplementPlannerService plannerService;

    public ClassroomAssignmentSmartComposer(JdbcTemplate jdbcTemplate,
                                            ClassroomKcResolver classroomKcResolver,
                                            MasteryService masteryService,
                                            BeginnerSupplementPlannerService plannerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.classroomKcResolver = classroomKcResolver;
        this.masteryService = masteryService;
        this.plannerService = plannerService;
    }

    public Map<String, Object> composeForClassroom(String classroomId,
                                                   List<Long> requestedKcIds,
                                                   Integer perStudentBudgetRaw,
                                                   Integer totalProblemBudgetRaw) {
        if (classroomId == null || classroomId.isBlank()) {
            throw BusinessExceptions.fromLegacy("error", "classroom_id is required");
        }
        Long languagePackId = classroomKcResolver.resolveLanguagePackId(classroomId);
        List<Long> validatedKcIds = classroomKcResolver.expandKcIds(classroomId, requestedKcIds == null ? List.of() : requestedKcIds);

        int perStudentBudget = perStudentBudgetRaw == null || perStudentBudgetRaw <= 0 ? DEFAULT_PER_STUDENT_BUDGET : Math.min(perStudentBudgetRaw, 5);
        int totalProblemBudget = totalProblemBudgetRaw == null || totalProblemBudgetRaw <= 0 ? DEFAULT_TOTAL_BUDGET : Math.min(totalProblemBudgetRaw, 30);

        List<Long> studentIds = jdbcTemplate.queryForList(
                "select user_id from classroom_member where classroom_id = ? and role = 'student'",
                Long.class,
                classroomId
        );
        Map<String, Double> aggregatedMastery = aggregateMastery(studentIds, languagePackId);

        List<Long> kcIdsForCompose;
        if (!validatedKcIds.isEmpty()) {
            kcIdsForCompose = validatedKcIds;
        } else {
            kcIdsForCompose = topWeakKcIds(languagePackId, aggregatedMastery, Math.max(1, totalProblemBudget / 2));
            if (kcIdsForCompose.isEmpty()) {
                throw BusinessExceptions.fromLegacy("error", "班级没有可识别的薄弱 KC，无法启用智能组卷");
            }
        }
        Map<Long, String> kcNameMap = classroomKcResolver.loadKcNameMap(languagePackId, kcIdsForCompose);

        Long representativeStudentId = pickRepresentativeStudent(studentIds, languagePackId, aggregatedMastery, kcIdsForCompose);

        Map<Long, List<Map<String, Object>>> kcToCards = new LinkedHashMap<>();
        Set<Long> usedProblemIds = new LinkedHashSet<>();
        for (Long kcId : kcIdsForCompose) {
            List<Map<String, Object>> cards = collectProblemCardsForKc(representativeStudentId, languagePackId, kcId, perStudentBudget);
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> card : cards) {
                Object pidObj = card.get("problem_id");
                if (!(pidObj instanceof Number n)) continue;
                Long pid = n.longValue();
                if (usedProblemIds.contains(pid)) continue;
                usedProblemIds.add(pid);
                filtered.add(card);
                if (usedProblemIds.size() >= totalProblemBudget) break;
            }
            kcToCards.put(kcId, filtered);
            if (usedProblemIds.size() >= totalProblemBudget) break;
        }

        List<Map<String, Object>> sections = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> entry : kcToCards.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("kc_id", entry.getKey());
            section.put("title", kcNameMap.getOrDefault(entry.getKey(), "KC #" + entry.getKey()));
            section.put("problems", entry.getValue());
            sections.add(section);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classroom_id", classroomId);
        result.put("language_pack_id", languagePackId);
        result.put("compose_strategy", "smart_kc");
        result.put("kc_ids", kcIdsForCompose);
        result.put("kc_names", kcIdsForCompose.stream().map(id -> kcNameMap.getOrDefault(id, "KC #" + id)).toList());
        result.put("per_student_budget", perStudentBudget);
        result.put("total_problem_budget", totalProblemBudget);
        result.put("total_picked", usedProblemIds.size());
        result.put("aggregated_mastery", aggregatedMastery);
        result.put("sections", sections);
        return result;
    }

    private Map<String, Double> aggregateMastery(List<Long> studentIds, Long languagePackId) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Long studentId : studentIds) {
            Map<String, Double> mastery = masteryService.projectMasteryByLanguagePack(studentId, languagePackId);
            for (Map.Entry<String, Double> entry : mastery.entrySet()) {
                sums.merge(entry.getKey(), entry.getValue(), Double::sum);
                counts.merge(entry.getKey(), 1, Integer::sum);
            }
        }
        Map<String, Double> avg = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : sums.entrySet()) {
            int n = counts.getOrDefault(entry.getKey(), 1);
            avg.put(entry.getKey(), Math.round(entry.getValue() / n * 1000.0) / 1000.0);
        }
        return avg;
    }

    private List<Long> topWeakKcIds(Long languagePackId, Map<String, Double> aggregatedMastery, int limit) {
        List<String> weakKcNames = new ArrayList<>(aggregatedMastery.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() < WEAK_THRESHOLD)
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList());
        if (weakKcNames.isEmpty()) return List.of();
        if (weakKcNames.size() > limit) {
            weakKcNames = weakKcNames.subList(0, limit);
        }
        String placeholders = String.join(", ", Collections.nCopies(weakKcNames.size(), "?"));
        Object[] args = new Object[weakKcNames.size() + 1];
        for (int i = 0; i < weakKcNames.size(); i++) {
            args[i] = weakKcNames.get(i);
        }
        args[args.length - 1] = languagePackId;
        return jdbcTemplate.query(
                "select id from language_pack_kc where name in (" + placeholders + ") and language_pack_id = ? order by id asc",
                (rs, rowNum) -> rs.getLong("id"),
                args
        );
    }

    private Long pickRepresentativeStudent(List<Long> studentIds, Long languagePackId,
                                           Map<String, Double> aggregatedMastery, List<Long> kcIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return null;
        }
        if (kcIds.isEmpty()) {
            return studentIds.get(0);
        }
        // 选择 KC 掌握度最接近全班均值的学生，作为代表性样本。
        Long bestId = studentIds.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (Long studentId : studentIds) {
            Map<String, Double> mastery = masteryService.projectMasteryByLanguagePack(studentId, languagePackId);
            double distance = 0.0;
            int matched = 0;
            for (Map.Entry<String, Double> agg : aggregatedMastery.entrySet()) {
                Double individual = mastery.get(agg.getKey());
                if (individual == null) continue;
                distance += Math.abs(individual - agg.getValue());
                matched++;
            }
            if (matched == 0) continue;
            distance /= matched;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestId = studentId;
            }
        }
        return bestId;
    }

    private List<Map<String, Object>> collectProblemCardsForKc(Long studentId, Long languagePackId, Long kcId, int budget) {
        if (studentId == null || languagePackId == null || kcId == null) return List.of();
        Map<String, Object> plan = plannerService.buildPlan(studentId, TRIGGER, languagePackId, null, null, null, budget);
        Object cards = plan.get("cards");
        if (!(cards instanceof List<?> rawCards)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object cardObj : rawCards) {
            if (!(cardObj instanceof Map<?, ?> map)) continue;
            String cardType = String.valueOf(map.get("card_type"));
            if (!"coding_problem".equals(cardType) && !"objective_problem".equals(cardType)) continue;
            Object payloadObj = map.get("payload");
            if (!(payloadObj instanceof Map<?, ?> payload)) continue;
            Object problemId = payload.get("problem_id");
            if (problemId == null) continue;
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("problem_id", problemId);
            card.put("problem_key", payload.get("problem_key"));
            card.put("title", payload.get("title"));
            card.put("difficulty", payload.get("difficulty"));
            card.put("question_type", payload.get("question_type"));
            card.put("card_type", cardType);
            card.put("kc_id", kcId);
            result.add(card);
            if (result.size() >= budget) break;
        }
        return result;
    }

    public List<String> resolveClassroomProblemIdsByProblemId(String classroomId, List<Long> problemIds) {
        if (classroomId == null || classroomId.isBlank() || problemIds == null || problemIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(problemIds.size(), "?"));
        Object[] args = new Object[problemIds.size() + 1];
        args[0] = classroomId;
        for (int i = 0; i < problemIds.size(); i++) {
            args[i + 1] = problemIds.get(i);
        }
        return jdbcTemplate.query(
                "select id from classroom_problem where classroom_id = ? and problem_id in (" + placeholders + ")",
                (rs, rowNum) -> rs.getString("id"),
                args
        );
    }

    public Locale defaultLocale() { return Locale.ROOT; }
}
