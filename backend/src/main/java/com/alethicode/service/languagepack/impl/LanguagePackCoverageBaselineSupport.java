package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class LanguagePackCoverageBaselineSupport {

    private LanguagePackCoverageBaselineSupport() {
    }

    static Map<String, Object> buildCoverageReport(ObjectMapper objectMapper,
                                                   String languagePackSlug,
                                                   List<Map<String, Object>> generatedRows,
                                                   List<Map<String, Object>> reviewedCandidates,
                                                   List<Map<String, Object>> chapterInventory,
                                                   List<Map<String, Object>> chapterMemoryRows,
                                                   int resumeReusedBatchCount) {
        List<Map<String, Object>> baselineRows = loadBaselineRows(objectMapper, languagePackSlug);
        LinkedHashMap<String, Map<String, Object>> baselineByKey = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> baselineCountByChapter = new LinkedHashMap<>();
        for (Map<String, Object> row : baselineRows) {
            String key = baselineKey(row);
            if (!key.isBlank()) {
                baselineByKey.putIfAbsent(key, row);
            }
            String chapterKey = chapterKey(row);
            if (!chapterKey.isBlank()) {
                baselineCountByChapter.merge(chapterKey, 1, Integer::sum);
            }
        }

        LinkedHashMap<String, Map<String, Object>> chapterStats = new LinkedHashMap<>();
        List<Map<String, Object>> blockedCandidates = new ArrayList<>();
        List<Map<String, Object>> unresolvedReviewRequired = new ArrayList<>();

        for (Map<String, Object> chapter : chapterInventory) {
            Map<String, Object> stat = createChapterStat(chapter);
            chapterStats.put(chapterKey(stat), stat);
        }

        for (Map<String, Object> candidate : reviewedCandidates) {
            String chapterKey = chapterKey(candidate);
            Map<String, Object> stat = chapterStats.computeIfAbsent(chapterKey, ignored -> createChapterStat(candidate));
            stat.put("unit_count", intVal(stat.get("unit_count")) + 1);
            stat.put("chapter_has_task_signal", booleanVal(stat.get("chapter_has_task_signal")) || intVal(candidate.get("task_signal_score")) > 0);
            if (booleanVal(candidate.get("oj_convertible"))) {
                stat.put("oj_candidate_count", intVal(stat.get("oj_candidate_count")) + 1);
            }
            if (booleanVal(candidate.get("stdin_stdout_convertible")) || booleanVal(candidate.get("oj_convertible"))) {
                stat.put("convertible_unit_count", intVal(stat.get("convertible_unit_count")) + 1);
            } else {
                stat.put("non_convertible_unit_count", intVal(stat.get("non_convertible_unit_count")) + 1);
            }
            if (!booleanVal(candidate.get("oj_convertible"))) {
                blockedCandidates.add(candidateSummary(candidate));
                if (booleanVal(candidate.get("review_required"))) {
                    unresolvedReviewRequired.add(candidateSummary(candidate));
                }
                @SuppressWarnings("unchecked")
                Map<String, Integer> blockedByReason = (Map<String, Integer>) stat.get("blocked_by_reason");
                blockedByReason.merge(stringVal(candidate.get("oj_block_reason")), 1, Integer::sum);
            }
        }

        LinkedHashSet<String> matchedKeys = new LinkedHashSet<>();
        List<Map<String, Object>> extra = new ArrayList<>();
        for (Map<String, Object> generated : generatedRows) {
            String chapterKey = chapterKey(generated);
            Map<String, Object> stat = chapterStats.computeIfAbsent(chapterKey, ignored -> createChapterStat(generated));
            stat.put("generated_problem_count", intVal(stat.get("generated_problem_count")) + 1);

            String matched = findBaselineMatch(generated, baselineByKey);
            if (matched == null) {
                extra.add(generatedSummary(generated));
                continue;
            }
            matchedKeys.add(matched);
        }

        List<Map<String, Object>> missing = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : baselineByKey.entrySet()) {
            if (!matchedKeys.contains(entry.getKey())) {
                missing.add(entry.getValue());
            }
        }

        for (Map<String, Object> stat : chapterStats.values()) {
            stat.put("baseline_expected_count", baselineCountByChapter.getOrDefault(chapterKey(stat), 0));
        }

        List<Map<String, Object>> highRiskChapters = chapterStats.values().stream()
                .filter(stat -> isHighRiskChapter(stat, !baselineRows.isEmpty()))
                .map(LanguagePackCoverageBaselineSupport::copyRow)
                .toList();

        int finalOjCandidateCount = (int) reviewedCandidates.stream()
                .filter(row -> booleanVal(row.get("oj_convertible")))
                .count();
        int kcAliasMergeCount = chapterMemoryRows.stream()
                .mapToInt(row -> intVal(row.get("kc_alias_merge_count")))
                .sum();
        int crossBatchMergedKcCount = chapterMemoryRows.stream()
                .mapToInt(row -> intVal(row.get("cross_batch_merged_kc_count")))
                .sum();
        int chapterMemoryConflictCount = chapterMemoryRows.stream()
                .mapToInt(row -> intVal(row.get("conflict_count")))
                .sum();

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("baseline_slug", languagePackSlug);
        report.put("baseline_problem_count", baselineByKey.size());
        report.put("generated_problem_count", generatedRows.size());
        report.put("final_oj_candidate_count", finalOjCandidateCount);
        report.put("matched_count", matchedKeys.size());
        report.put("missing", missing);
        report.put("extra", extra);
        report.put("blocked_candidates", blockedCandidates);
        report.put("chapter_stats", chapterStats.values().stream()
                .sorted((left, right) -> Integer.compare(intVal(left.get("chapter_index")), intVal(right.get("chapter_index"))))
                .toList());
        report.put("high_risk_chapters", highRiskChapters);
        report.put("unresolved_review_required", unresolvedReviewRequired);
        report.put("kc_alias_merge_count", kcAliasMergeCount);
        report.put("cross_batch_merged_kc_count", crossBatchMergedKcCount);
        report.put("resume_reused_batch_count", resumeReusedBatchCount);
        report.put("chapter_memory_conflict_count", chapterMemoryConflictCount);
        return report;
    }

    private static Map<String, Object> createChapterStat(Map<String, Object> row) {
        LinkedHashMap<String, Object> stat = new LinkedHashMap<>();
        stat.put("chapter_title", stringVal(row.get("chapter_title")));
        stat.put("chapter_index", intVal(row.get("chapter_index")));
        stat.put("chapter_page_count", intVal(row.get("chapter_page_count")));
        stat.put("unit_count", 0);
        stat.put("oj_candidate_count", 0);
        stat.put("convertible_unit_count", 0);
        stat.put("non_convertible_unit_count", 0);
        stat.put("generated_problem_count", 0);
        stat.put("baseline_expected_count", 0);
        stat.put("chapter_has_task_signal", false);
        stat.put("blocked_by_reason", new LinkedHashMap<String, Integer>());
        return stat;
    }

    private static boolean isHighRiskChapter(Map<String, Object> stat, boolean baselineEnabled) {
        if (intVal(stat.get("chapter_page_count")) < 8) {
            return false;
        }
        if (intVal(stat.get("oj_candidate_count")) > 0) {
            return false;
        }
        if (!booleanVal(stat.get("chapter_has_task_signal"))) {
            return false;
        }
        if (intVal(stat.get("convertible_unit_count")) <= 0) {
            return false;
        }
        if (!baselineEnabled) {
            return true;
        }
        return intVal(stat.get("baseline_expected_count")) > 0;
    }

    private static Map<String, Object> candidateSummary(Map<String, Object> row) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("chapter_title", stringVal(row.get("chapter_title")));
        summary.put("chapter_index", intVal(row.get("chapter_index")));
        summary.put("source_title", stringVal(row.get("source_title")));
        summary.put("page_range_start", intVal(row.get("page_range_start")));
        summary.put("page_range_end", intVal(row.get("page_range_end")));
        summary.put("oj_block_reason", stringVal(row.get("oj_block_reason")));
        summary.put("review_reason", stringVal(row.get("review_reason")));
        return summary;
    }

    private static Map<String, Object> generatedSummary(Map<String, Object> row) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("chapter_title", stringVal(row.get("chapter_title")));
        summary.put("chapter_index", intVal(row.get("chapter_index")));
        summary.put("source_title", stringVal(row.get("source_title")));
        summary.put("problem_title", stringVal(row.get("title")));
        summary.put("unit_type", stringVal(row.get("unit_type")));
        return summary;
    }

    private static Map<String, Object> copyRow(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    private static List<Map<String, Object>> loadBaselineRows(ObjectMapper objectMapper, String languagePackSlug) {
        String resourcePath = "language-pack-baselines/" + languagePackSlug + ".json";
        try (InputStream inputStream = LanguagePackCoverageBaselineSupport.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return List.of();
            }
            Map<String, Object> root = objectMapper.readValue(inputStream, new TypeReference<>() {});
            Object problems = root.get("problems");
            if (!(problems instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    rows.add(normalized);
                }
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load language-pack baseline: " + languagePackSlug, exception);
        }
    }

    private static String findBaselineMatch(Map<String, Object> generated,
                                              Map<String, Map<String, Object>> baselineByKey) {
        String exactKey = generatedKey(generated);
        if (!exactKey.isBlank() && baselineByKey.containsKey(exactKey)) {
            return exactKey;
        }
        String genTitle = stripNumberPrefix(normalizeKey(generatedTitle(generated)));
        if (genTitle.isBlank()) {
            return null;
        }
        String generatedChapterKey = chapterKey(generated);
        for (Map.Entry<String, Map<String, Object>> entry : baselineByKey.entrySet()) {
            String baseTitle = stripNumberPrefix(normalizeKey(stringVal(entry.getValue().get("title"))));
            if (baseTitle.isBlank()) continue;
            String baselineChapterKey = chapterKey(entry.getValue());
            if (!generatedChapterKey.isBlank()
                    && !baselineChapterKey.isBlank()
                    && !generatedChapterKey.equals(baselineChapterKey)) {
                continue;
            }
            if (genTitle.equals(baseTitle) || genTitle.contains(baseTitle) || baseTitle.contains(genTitle)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String generatedTitle(Map<String, Object> row) {
        String title = stringVal(row.get("source_title"));
        if (title.isBlank()) title = stringVal(row.get("title"));
        if (title.isBlank()) title = stringVal(row.get("candidate_title"));
        return title;
    }

    private static int extractGeneratedChapterIndex(Map<String, Object> row) {
        int idx = intVal(row.get("chapter_index"));
        if (idx > 0) return idx;
        return extractChapterIndex(stringVal(row.get("chapter_title")));
    }

    private static String stripNumberPrefix(String normalized) {
        return normalized.replaceAll("^[\\d]+", "");
    }

    private static String baselineKey(Map<String, Object> row) {
        return chapterKey(row) + "::" + normalizeKey(stringVal(row.get("title")));
    }

    private static String generatedKey(Map<String, Object> row) {
        String title = generatedTitle(row);
        return chapterKey(row) + "::" + normalizeKey(title);
    }

    private static String chapterKey(Map<String, Object> row) {
        String chapterTitle = stringVal(row.get("chapter_title"));
        if (chapterTitle.isBlank()) {
            chapterTitle = stringVal(row.get("chapter"));
        }
        String normalizedChapterTitle = normalizeKey(chapterTitle);
        if (!normalizedChapterTitle.isBlank()) {
            return normalizedChapterTitle;
        }
        int chapterIndex = intVal(row.get("chapter_index"));
        if (chapterIndex == 0 && !chapterTitle.isBlank()) {
            chapterIndex = extractChapterIndex(chapterTitle);
        }
        return chapterIndex <= 0 ? "" : "chapter-" + chapterIndex;
    }

    private static int extractChapterIndex(String chapterTitle) {
        String text = stringVal(chapterTitle);
        if (text.isBlank()) {
            return 0;
        }
        for (int index = 1; index <= 20; index++) {
            if (text.contains("第" + toChineseNumber(index) + "章") || text.contains("第" + index + "章")) {
                return index;
            }
        }
        return 0;
    }

    private static String toChineseNumber(int number) {
        return switch (number) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            default -> String.valueOf(number);
        };
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}：，。、“”‘’（）()\\[\\]【】《》<>\\s]+", "")
                .strip();
    }

    private static boolean booleanVal(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(stringVal(value));
    }

    private static int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).strip();
    }
}
