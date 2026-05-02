package com.alethicode.service.aitutor.supplement;

import com.alethicode.service.aitutor.language.TutorLanguageSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BeginnerSupplementPlannerService {

    private static final Set<String> VALID_TRIGGERS = Set.of("warmup", "stuck", "wrong_answer", "daily_review", "post_ac");
    private static final int DEFAULT_CARD_COUNT = 3;

    private final JdbcTemplate jdbcTemplate;

    public BeginnerSupplementPlannerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> buildPlan(Long userId,
                                         String trigger,
                                         Long languagePackId,
                                         Long problemId,
                                         Long submissionId,
                                         String errorTaxonomy,
                                         Integer requestedCount) {
        String normalizedTrigger = normalizeTrigger(trigger);
        if (languagePackId == null) {
            throw new IllegalArgumentException("language_pack_id is required");
        }
        int cardCount = requestedCount == null || requestedCount < 1 ? DEFAULT_CARD_COUNT : Math.min(requestedCount, 5);
        Map<String, Object> pack = loadLanguagePack(languagePackId);
        String primaryLanguage = TutorLanguageSupport.normalizeLanguage(pack.get("primary_language"));
        Map<String, Object> profile = buildLanguageProfile(primaryLanguage);
        List<KcTarget> targetKcs = resolveTargetKcs(userId, languagePackId, problemId, errorTaxonomy, 3);

        List<Map<String, Object>> cards = new ArrayList<>();
        Map<String, Object> exampleCard = buildCourseExampleCard(languagePackId, targetKcs, normalizedTrigger);
        if (!exampleCard.isEmpty()) {
            cards.add(exampleCard);
        }

        Map<String, Object> microCard = buildMicroPracticeCard(userId, languagePackId, problemId, targetKcs, normalizedTrigger, primaryLanguage);
        if (!microCard.isEmpty()) {
            cards.add(microCard);
        }

        if (!isLearnerStuckTrigger(normalizedTrigger)) {
            Map<String, Object> codingCard = buildCodingPracticeCard(userId, languagePackId, problemId, targetKcs, normalizedTrigger);
            if (!codingCard.isEmpty()) {
                cards.add(codingCard);
            }
        }

        if ("post_ac".equals(normalizedTrigger)) {
            Map<String, Object> transferCard = buildTransferPreviewCard(problemId, languagePackId, targetKcs, primaryLanguage);
            if (!transferCard.isEmpty()) {
                cards.add(transferCard);
            }
        }

        List<Map<String, Object>> dedupedCards = dedupeCards(cards, cardCount);

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("language_profile", profile);
        plan.put("intro_message", buildIntroMessage(normalizedTrigger, primaryLanguage, targetKcs));
        plan.put("target_kcs", targetKcs.stream().map(KcTarget::name).toList());
        plan.put("cards", dedupedCards);
        if (submissionId != null) {
            plan.put("submission_id", submissionId);
        }
        plan.put("language_pack_id", languagePackId);
        return plan;
    }

    private String normalizeTrigger(String trigger) {
        String normalized = trigger == null ? "warmup" : trigger.trim().toLowerCase(Locale.ROOT);
        if (!VALID_TRIGGERS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid trigger: " + trigger);
        }
        return normalized;
    }

    private boolean isLearnerStuckTrigger(String normalizedTrigger) {
        return "stuck".equals(normalizedTrigger) || "wrong_answer".equals(normalizedTrigger);
    }

    private Map<String, Object> loadLanguagePack(Long languagePackId) {
        return jdbcTemplate.query(
                """
                select id, name, primary_language
                from language_pack
                where id = ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("name", rs.getString("name"));
                    item.put("primary_language", rs.getString("primary_language"));
                    return item;
                },
                languagePackId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("language pack not found"));
    }

    private Map<String, Object> buildLanguageProfile(String primaryLanguage) {
        String normalized = normalizeLanguage(primaryLanguage);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("language", normalized);
        switch (normalized) {
            case "C" -> {
                profile.put("teaching_tone", "强调输入输出格式、边界检查和小样例验证。");
                profile.put("common_pitfalls", List.of("scanf/printf 格式", "数组越界", "循环边界", "函数参数"));
                profile.put("preferred_card_order", List.of("course_example", "faded_example", "coding_problem", "transfer_problem"));
            }
            case "C++" -> {
                profile.put("teaching_tone", "强调类型、容器和 STL 基础误用。");
                profile.put("common_pitfalls", List.of("类型转换", "vector/string 访问", "循环条件", "STL 误用"));
                profile.put("preferred_card_order", List.of("course_example", "faded_example", "coding_problem", "transfer_problem"));
            }
            case "Java" -> {
                profile.put("teaching_tone", "强调类与方法结构、Scanner 输入和数组循环。");
                profile.put("common_pitfalls", List.of("Scanner 输入", "类型声明", "数组边界", "方法调用"));
                profile.put("preferred_card_order", List.of("course_example", "faded_example", "coding_problem", "transfer_problem"));
            }
            default -> {
                profile.put("teaching_tone", "强调变量、缩进、容器和边界判断。");
                profile.put("common_pitfalls", List.of("缩进", "变量更新", "列表/字符串访问", "边界条件"));
                profile.put("preferred_card_order", List.of("course_example", "faded_example", "coding_problem", "transfer_problem"));
            }
        }
        return profile;
    }

    private String normalizeLanguage(String primaryLanguage) {
        String normalized = TutorLanguageSupport.normalizeLanguage(primaryLanguage);
        if (normalized == null || normalized.isBlank()) {
            return "Python3";
        }
        return normalized;
    }

    private List<KcTarget> resolveTargetKcs(Long userId, Long languagePackId, Long problemId, String errorTaxonomy, int limit) {
        if (problemId != null) {
            List<KcTarget> fromProblem = jdbcTemplate.query(
                    """
                    select distinct kc.id, kc.name, km.mastery
                    from ai_problem_kc_mapping m
                    join language_pack_kc kc on kc.id = m.kc_id
                    left join learner_kc_mastery km on km.kc_id = kc.id and km.user_id = ?
                    where m.problem_id = ? and m.language_pack_id = ?
                    order by km.mastery asc nulls first, kc.id asc
                    limit ?
                    """,
                    (rs, rowNum) -> new KcTarget(rs.getLong("id"), rs.getString("name"), getNullableDouble(rs.getObject("mastery"))),
                    userId, problemId, languagePackId, limit
            );
            if (!fromProblem.isEmpty()) {
                return fromProblem;
            }
        }
        if (errorTaxonomy != null && !errorTaxonomy.isBlank()) {
            List<KcTarget> fromNotebook = jdbcTemplate.query(
                    """
                    select kc.id, kc.name, min(km.mastery) as mastery
                    from ai_learner_notebook n
                    join language_pack_problem_mapping lpm on lpm.problem_id = n.problem_id and lpm.language_pack_id = ?
                    join ai_problem_kc_mapping m on m.problem_id = n.problem_id and m.language_pack_id = ?
                    join language_pack_kc kc on kc.id = m.kc_id
                    left join learner_kc_mastery km on km.kc_id = kc.id and km.user_id = n.user_id
                    where n.user_id = ? and n.is_deleted = false and n.error_taxonomy = ?
                    group by kc.id, kc.name
                    order by count(*) desc, mastery asc nulls first, kc.id asc
                    limit ?
                    """,
                    (rs, rowNum) -> new KcTarget(rs.getLong("id"), rs.getString("name"), getNullableDouble(rs.getObject("mastery"))),
                    languagePackId, languagePackId, userId, errorTaxonomy, limit
            );
            if (!fromNotebook.isEmpty()) {
                return fromNotebook;
            }
        }
        List<KcTarget> weakest = jdbcTemplate.query(
                """
                select kc.id, kc.name, km.mastery
                from language_pack_kc kc
                left join learner_kc_mastery km on km.kc_id = kc.id and km.user_id = ?
                where kc.language_pack_id = ?
                order by km.mastery asc nulls first, kc.id asc
                limit ?
                """,
                (rs, rowNum) -> new KcTarget(rs.getLong("id"), rs.getString("name"), getNullableDouble(rs.getObject("mastery"))),
                userId, languagePackId, limit
        );
        if (!weakest.isEmpty()) {
            return weakest;
        }
        return List.of();
    }

    private Map<String, Object> buildCourseExampleCard(Long languagePackId, List<KcTarget> targetKcs, String trigger) {
        if (targetKcs.isEmpty()) return Map.of();
        List<Long> kcIds = targetKcs.stream().map(KcTarget::id).toList();
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select e.id, e.source_title, e.normalized_body, e.document_id, e.page_range_start,
                       kc.name as kc_name, ch.title as chapter_title
                from language_pack_example_kc_mapping ekm
                join language_pack_example e on e.id = ekm.example_id
                join language_pack_kc kc on kc.id = ekm.kc_id
                left join language_pack_chapter ch on ch.id = kc.chapter_id
                where e.language_pack_id = ?
                  and ekm.kc_id in (%s)
                order by e.id asc
                limit 1
                """.formatted(placeholders(kcIds.size())),
                (rs, rowNum) -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("example_id", rs.getLong("id"));
                    payload.put("source_title", rs.getString("source_title"));
                    payload.put("normalized_body", rs.getString("normalized_body"));
                    payload.put("kc_name", rs.getString("kc_name"));
                    payload.put("chapter_title", rs.getString("chapter_title"));
                    payload.put("document_id", rs.getObject("document_id"));
                    payload.put("slide_number", rs.getObject("page_range_start"));
                    return payload;
                },
                combineArgs(languagePackId, kcIds)
        );
        if (rows.isEmpty()) return Map.of();
        return buildCard("understand", "course_example", languagePackId, targetKcs,
                explainWhy(trigger, "course_example"),
                rows.getFirst().getOrDefault("source_title", "课件例题").toString(),
                rows.getFirst());
    }

    private Map<String, Object> buildMicroPracticeCard(Long userId, Long languagePackId, Long currentProblemId, List<KcTarget> targetKcs, String trigger, String primaryLanguage) {
        Map<String, Object> objective = loadProblemCard(userId, languagePackId, currentProblemId, targetKcs, true);
        if (!objective.isEmpty()) {
            return buildCard("recall", "objective_problem", languagePackId, targetKcs,
                    explainWhy(trigger, "objective_problem"),
                    stringValue(objective.get("title")),
                    objective);
        }
        Map<String, Object> faded = buildFadedExampleCard(languagePackId, targetKcs, primaryLanguage, trigger);
        if (!faded.isEmpty()) {
            return faded;
        }
        return Map.of();
    }

    private Map<String, Object> buildCodingPracticeCard(Long userId, Long languagePackId, Long currentProblemId, List<KcTarget> targetKcs, String trigger) {
        Map<String, Object> coding = loadProblemCard(userId, languagePackId, currentProblemId, targetKcs, false);
        if (coding.isEmpty()) return Map.of();
        return buildCard("apply", "coding_problem", languagePackId, targetKcs,
                explainWhy(trigger, "coding_problem"),
                stringValue(coding.get("title")),
                coding);
    }

    private Map<String, Object> buildTransferPreviewCard(Long problemId, Long languagePackId, List<KcTarget> targetKcs, String primaryLanguage) {
        if (problemId == null) return Map.of();
        Map<String, Object> source = jdbcTemplate.query(
                """
                select p._id, p.title, p.description
                from problem p
                join language_pack_problem_mapping lpm on lpm.problem_id = p.id and lpm.language_pack_id = ?
                where p.id = ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("problem_display_id", "");
                    item.put("source_problem_key", rs.getString("_id"));
                    item.put("title", "从「" + rs.getString("title") + "」继续进阶");
                    item.put("description", "你已经完成原题，下一步应该换一个新情境继续练同一知识点。当前版本先给出迁移方向，后续仍可通过现有 AI 举一反三入口生成正式迁移题。");
                    item.put("hint", buildTransferHint(primaryLanguage, targetKcs));
                    item.put("target_kcs", targetKcs.stream().map(KcTarget::name).toList());
                    item.put("samples", List.of());
                    return item;
                },
                languagePackId, problemId
        ).stream().findFirst().orElse(Map.of());
        if (source.isEmpty()) return Map.of();
        return buildCard("transfer", "transfer_problem", languagePackId, targetKcs,
                "最后切到一个新情境，检查你是不是真的会举一反三。",
                stringValue(source.get("title")),
                source);
    }

    private String buildTransferHint(String primaryLanguage, List<KcTarget> targetKcs) {
        String kcText = targetKcs.isEmpty() ? "当前核心知识点" : targetKcs.getFirst().name();
        return switch (normalizeLanguage(primaryLanguage)) {
            case "C" -> "先用最小输入手算一遍，再确认每一步变量更新是否符合预期，重点盯住 " + kcText + "。";
            case "C++" -> "先写出输入、状态和输出三件事，再检查容器访问和循环边界，重点盯住 " + kcText + "。";
            case "Java" -> "先把输入读取、主循环和输出三块分开想，再检查变量类型和数组范围，重点盯住 " + kcText + "。";
            default -> "先画出变量变化过程，再用一个最小样例验证边界条件，重点盯住 " + kcText + "。";
        };
    }

    private Map<String, Object> buildFadedExampleCard(Long languagePackId, List<KcTarget> targetKcs, String primaryLanguage, String trigger) {
        if (targetKcs.isEmpty()) return Map.of();
        List<Long> kcIds = targetKcs.stream().map(KcTarget::id).toList();
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select e.id, e.source_title, e.normalized_body, e.document_id, e.page_range_start
                from language_pack_example_kc_mapping ekm
                join language_pack_example e on e.id = ekm.example_id
                where e.language_pack_id = ?
                  and ekm.kc_id in (%s)
                  and e.normalized_body is not null and e.normalized_body <> ''
                order by e.id asc
                limit 1
                """.formatted(placeholders(kcIds.size())),
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("example_id", rs.getLong("id"));
                    item.put("source_title", rs.getString("source_title"));
                    item.put("normalized_body", rs.getString("normalized_body"));
                    item.put("document_id", rs.getObject("document_id"));
                    item.put("slide_number", rs.getObject("page_range_start"));
                    return item;
                },
                combineArgs(languagePackId, kcIds)
        );
        if (rows.isEmpty()) return Map.of();
        String body = stringValue(rows.getFirst().get("normalized_body"));
        List<String> lines = body.lines().map(String::trim).filter(line -> !line.isBlank()).limit(4).toList();
        if (lines.isEmpty()) return Map.of();
        int fadedIndex = lines.size() == 1 ? 0 : Math.min(1, lines.size() - 1);
        List<Map<String, Object>> steps = new ArrayList<>();
        List<String> studentBlanks = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            boolean faded = i == fadedIndex;
            String stepId = "step_" + (i + 1);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step_id", stepId);
            step.put("subgoal", "理解第 " + (i + 1) + " 步");
            step.put("faded", faded);
            if (faded) {
                step.put("hint", buildFadedHint(primaryLanguage, trigger));
                studentBlanks.add(stepId);
            } else {
                step.put("code", lines.get(i));
                step.put("explanation", "先读懂这一行在做什么。");
            }
            steps.add(step);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", rows.getFirst().get("source_title"));
        payload.put("steps", steps);
        payload.put("student_blanks", studentBlanks);
        payload.put("validation_status", "preview");
        payload.put("source_title", rows.getFirst().get("source_title"));
        payload.put("document_id", rows.getFirst().get("document_id"));
        payload.put("slide_number", rows.getFirst().get("slide_number"));
        return buildCard("recall", "faded_example", languagePackId, targetKcs,
                explainWhy(trigger, "faded_example"),
                stringValue(rows.getFirst().get("source_title")),
                payload);
    }

    private String buildFadedHint(String primaryLanguage, String trigger) {
        String prefix = "stuck".equals(trigger) || "wrong_answer".equals(trigger)
                ? "先只补最关键的一步，不要一次全改。"
                : "先补全这一小步，再继续。";
        return switch (normalizeLanguage(primaryLanguage)) {
            case "C" -> prefix + " 重点检查变量更新和输入输出格式。";
            case "C++" -> prefix + " 重点检查容器访问和循环条件。";
            case "Java" -> prefix + " 重点检查变量类型和方法调用。";
            default -> prefix + " 重点检查变量变化和边界条件。";
        };
    }

    private Map<String, Object> loadProblemCard(Long userId, Long languagePackId, Long excludeProblemId, List<KcTarget> targetKcs, boolean objectiveOnly) {
        if (targetKcs.isEmpty()) return Map.of();
        List<Long> kcIds = targetKcs.stream().map(KcTarget::id).toList();
        String questionFilter = objectiveOnly
                ? "coalesce(p.statistic_info #>> '{objective_question,question_type}', '') in ('choice', 'fill_blank')"
                : "coalesce(p.statistic_info #>> '{objective_question,question_type}', '') not in ('choice', 'fill_blank')";
        long excludeId = excludeProblemId == null ? -1L : excludeProblemId;
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select distinct p.id, p._id, p.title, p.difficulty,
                       coalesce(p.statistic_info #>> '{objective_question,question_type}', 'coding') as question_type
                from ai_problem_kc_mapping m
                join language_pack_problem_mapping lpm on lpm.problem_id = m.problem_id and lpm.language_pack_id = ?
                join problem p on p.id = m.problem_id
                where m.language_pack_id = ?
                  and m.kc_id in (%s)
                  and p.visible = true
                  and p.id <> ?
                  and %s
                  and not exists (
                      select 1 from submission s
                      where s.user_id = ? and s.problem_id = p.id and s.result = 0
                  )
                order by p.id asc
                limit 1
                """.formatted(placeholders(kcIds.size()), questionFilter),
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("problem_id", rs.getLong("id"));
                    item.put("problem_key", rs.getString("_id"));
                    item.put("title", rs.getString("title"));
                    item.put("difficulty", rs.getString("difficulty"));
                    item.put("question_type", rs.getString("question_type"));
                    return item;
                },
                combineProblemCardArgs(languagePackId, kcIds, excludeId, userId)
        );
        return rows.stream().findFirst().orElse(Map.of());
    }

    private List<Map<String, Object>> dedupeCards(List<Map<String, Object>> cards, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> card : cards) {
            if (card == null || card.isEmpty()) continue;
            String key = stringValue(card.get("card_type")) + "|" + stringValue(castMap(card.get("payload")).get("problem_id")) + "|" + stringValue(card.get("title"));
            if (seen.add(key)) {
                result.add(card);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Map<String, Object> buildCard(String educationGoal,
                                          String cardType,
                                          Long languagePackId,
                                          List<KcTarget> targetKcs,
                                          String whyThisNow,
                                          String title,
                                          Map<String, Object> payload) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("education_goal", educationGoal);
        card.put("card_type", cardType);
        card.put("language_pack_id", languagePackId);
        card.put("target_kcs", targetKcs.stream().map(KcTarget::name).toList());
        card.put("why_this_now", whyThisNow);
        card.put("title", title);
        card.put("payload", payload);
        return card;
    }

    private String buildIntroMessage(String trigger, String primaryLanguage, List<KcTarget> targetKcs) {
        String kcText = targetKcs.isEmpty() ? "当前知识点" : targetKcs.getFirst().name();
        String language = normalizeLanguage(primaryLanguage);
        return switch (trigger) {
            case "stuck" -> "卡住时不急着换题，先回到课件例题，再用一道更小的练习重新理顺「" + kcText + "」。";
            case "wrong_answer" -> "先回到课件例题对照写法，再用一道更小的练习巩固「" + kcText + "」。";
            case "daily_review" -> "今天先复习「" + kcText + "」，按照例题 -> 微练习 -> 正式题的顺序走一遍。";
            case "post_ac" -> "这道题已经完成，下一步切换到一个新情境继续练「" + kcText + "」。";
            default -> language + " 初学者建议先看例题，再做小步练习，最后进入正式编码。";
        };
    }

    private String explainWhy(String trigger, String cardType) {
        return switch (cardType) {
            case "course_example" -> switch (trigger) {
                case "stuck", "wrong_answer" -> "先回到课件例题，看清楚这类知识点本来应该怎样写。";
                default -> "先建立正确的代码印象，再进入练习。";
            };
            case "objective_problem" -> "先用更短的题确认你记住了关键判断，再进入完整编码。";
            case "faded_example" -> "先补全关键一步，避免一上来就被完整实现压住。";
            case "coding_problem" -> "现在做一道同知识点编程练习，确认你能把刚复盘的思路独立写成代码。";
            case "transfer_problem" -> "最后换一个新情境，检查你是否已经真正掌握。";
            default -> "按照初学者节奏逐步推进。";
        };
    }

    private Object[] combineArgs(Object first, List<Long> kcIds) {
        Object[] args = new Object[1 + kcIds.size()];
        args[0] = first;
        for (int i = 0; i < kcIds.size(); i++) {
            args[i + 1] = kcIds.get(i);
        }
        return args;
    }

    private Object[] combineArgs(Object first, Object second, List<Long> kcIds, Object tail) {
        Object[] args = new Object[3 + kcIds.size()];
        args[0] = first;
        args[1] = second;
        for (int i = 0; i < kcIds.size(); i++) {
            args[i + 2] = kcIds.get(i);
        }
        args[args.length - 1] = tail;
        return args;
    }

    private Object[] combineProblemCardArgs(Long languagePackId, List<Long> kcIds, long excludeProblemId, Long userId) {
        Object[] args = new Object[4 + kcIds.size()];
        args[0] = languagePackId;
        args[1] = languagePackId;
        for (int i = 0; i < kcIds.size(); i++) {
            args[i + 2] = kcIds.get(i);
        }
        args[args.length - 2] = excludeProblemId;
        args[args.length - 1] = userId;
        return args;
    }

    private String placeholders(int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) builder.append(", ");
            builder.append("?");
        }
        return builder.toString();
    }

    private Double getNullableDouble(Object raw) {
        if (!(raw instanceof Number number)) return null;
        return number.doubleValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record KcTarget(Long id, String name, Double mastery) {
    }
}
