package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LanguagePackProblemPackageMapper {

    private LanguagePackProblemPackageMapper() {
    }

    static LanguagePackProblemPackage normalizeGeneratedPackage(ObjectMapper objectMapper,
                                                               Map<String, Object> llmResult,
                                                               Map<String, Object> unit,
                                                               List<Long> relatedKcIds,
                                                               String primaryLanguage,
                                                               Long languagePackId) {
        Map<String, Object> rawPackage = extractSingleProblemMap(llmResult);
        return buildPackage(objectMapper, rawPackage, unit, relatedKcIds, primaryLanguage, languagePackId);
    }

    static LanguagePackProblemPackage canonicalizeStoredPackage(ObjectMapper objectMapper,
                                                               LanguagePackProblemPackage problemPackage,
                                                               Map<String, Object> unit,
                                                               List<Long> defaultRelatedKcIds,
                                                               String primaryLanguage,
                                                               Long languagePackId) {
        Map<String, Object> rawPackage = objectMapper.convertValue(problemPackage, new TypeReference<>() {});
        return buildPackage(objectMapper, rawPackage, unit, defaultRelatedKcIds, primaryLanguage, languagePackId);
    }

    static LanguagePackProblemPackage fromStoredJson(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("problem package json is required");
        }
        try {
            return objectMapper.readValue(json, LanguagePackProblemPackage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid problem package json", exception);
        }
    }

    static boolean hasMeaningfulStoredJson(String json) {
        String normalized = trimToNull(json);
        return normalized != null && !"{}".equals(normalized);
    }

    static LanguagePackProblemPackage fromLegacyRow(ObjectMapper objectMapper,
                                                    Map<String, Object> row,
                                                    Long languagePackId,
                                                    String primaryLanguage) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("display_id", row.get("display_id"));
        normalized.put("title", row.get("candidate_title"));
        normalized.put("description", row.get("candidate_body"));
        normalized.put("input_description", row.get("candidate_input_description"));
        normalized.put("output_description", row.get("candidate_output_description"));
        normalized.put("samples", parseJsonValue(objectMapper, stringVal(row.get("candidate_samples_json"))));
        normalized.put("test_cases", parseJsonValue(objectMapper, stringVal(row.get("test_cases_json"))));
        normalized.put("template", Map.of(primaryLanguage, defaultTemplate(primaryLanguage)));
        normalized.put("time_limit", parseInteger(row.get("time_limit")));
        normalized.put("memory_limit", parseInteger(row.get("memory_limit")));
        normalized.put("difficulty", row.get("difficulty"));
        normalized.put("source_pages", parseJsonValue(objectMapper, stringVal(row.get("source_pages_json"))));
        normalized.put("source_example_ids", parseJsonValue(objectMapper, stringVal(row.get("source_example_ids_json"))));
        normalized.put("related_kc_ids", parseJsonValue(objectMapper, stringVal(row.get("related_kc_ids_json"))));
        normalized.put("teaching_explanation", row.get("teaching_explanation"));
        normalized.put("common_mistakes", parseJsonValue(objectMapper, stringVal(row.get("common_mistakes_json"))));
        normalized.put("language_pack_id", languagePackId);
        normalized.put("reference_solution_language", primaryLanguage);
        normalized.put("reference_solution_code", firstNonBlank(
                stringVal(row.get("reference_solution_code")),
                stringVal(row.get("reference_solution"))
        ));
        Map<String, Object> legacyUnit = new LinkedHashMap<>();
        legacyUnit.put("id", row.get("example_id"));
        legacyUnit.put("page_range_start", parseFirstPage(row.get("source_pages_json"), objectMapper));
        legacyUnit.put("page_range_end", parseLastPage(row.get("source_pages_json"), objectMapper));
        return buildPackage(
                objectMapper,
                normalized,
                legacyUnit,
                parseJsonLongList(objectMapper, stringVal(row.get("related_kc_ids_json"))),
                primaryLanguage,
                languagePackId
        );
    }

    static Map<String, Object> toArtifactMap(ObjectMapper objectMapper,
                                             LanguagePackProblemPackage problemPackage,
                                             Map<String, Object> unit,
                                             String sourceSignature) {
        Map<String, Object> artifact = objectMapper.convertValue(problemPackage, new TypeReference<>() {});
        artifact.put("chapter_title", stringVal(unit.get("chapter_title")));
        artifact.put("chapter_index", longVal(unit.get("chapter_index")));
        artifact.put("source_title", stringVal(unit.get("source_title")));
        artifact.put("unit_type", stringVal(unit.get("unit_type")));
        artifact.put("source_signature", sourceSignature == null ? "" : sourceSignature);
        artifact.put("example_id", longVal(unit.get("id")));
        return artifact;
    }

    static String renderMarkdown(List<Map<String, Object>> artifactRows) {
        StringBuilder builder = new StringBuilder("# Problem Packages\n\n");
        int index = 1;
        for (Map<String, Object> row : artifactRows) {
            builder.append("## ").append(index++).append(". ").append(stringVal(row.get("title"))).append("\n\n");
            builder.append("- source_title: ").append(stringVal(row.get("source_title"))).append("\n");
            builder.append("- chapter_title: ").append(stringVal(row.get("chapter_title"))).append("\n");
            builder.append("- unit_type: ").append(stringVal(row.get("unit_type"))).append("\n");
            builder.append("- source_signature: ").append(stringVal(row.get("source_signature"))).append("\n");
            builder.append("- source_pages: ").append(row.getOrDefault("source_pages", List.of())).append("\n");
            builder.append("- source_example_ids: ").append(row.getOrDefault("source_example_ids", List.of())).append("\n");
            builder.append("- related_kc_ids: ").append(row.getOrDefault("related_kc_ids", List.of())).append("\n");
            builder.append("- difficulty: ").append(stringVal(row.get("difficulty"))).append("\n\n");
            builder.append("### Description\n\n");
            builder.append(stringVal(row.get("description"))).append("\n\n");
        }
        return builder.toString().strip() + "\n";
    }

    private static final List<String> PLACEHOLDER_PATTERNS = List.of(
            "TODO", "todo", "<placeholder", "<your", "xxx", "yyy", "FILL_IN", "..."
    );

    private static boolean containsPlaceholder(String value) {
        if (value == null) return false;
        for (String pat : PLACEHOLDER_PATTERNS) {
            if (value.contains(pat)) return true;
        }
        return false;
    }

    private static LanguagePackProblemPackage buildPackage(ObjectMapper objectMapper,
                                                           Map<String, Object> rawPackage,
                                                           Map<String, Object> unit,
                                                           List<Long> defaultRelatedKcIds,
                                                           String primaryLanguage,
                                                           Long languagePackId) {
        String rawJsonForError;
        try {
            rawJsonForError = objectMapper.writeValueAsString(rawPackage);
        } catch (Exception e) {
            rawJsonForError = String.valueOf(rawPackage);
        }

        List<LanguagePackProblemPackage.TestCase> testCases = parseTestCases(rawPackage.get("test_cases"));
        if (testCases.isEmpty()) {
            throw new LlmSchemaViolationException("test_cases is empty (expected 3-5 cases)", rawJsonForError);
        }
        if (testCases.size() < 3 || testCases.size() > 5) {
            throw new LlmSchemaViolationException(
                    "test_cases.size=" + testCases.size() + " not in [3,5]", rawJsonForError);
        }
        for (int i = 0; i < testCases.size(); i++) {
            var tc = testCases.get(i);
            if (tc.input() == null || tc.input().isBlank()) {
                throw new LlmSchemaViolationException("test_cases[" + i + "].input is empty/blank", rawJsonForError);
            }
            if (tc.output() == null || tc.output().isBlank()) {
                throw new LlmSchemaViolationException("test_cases[" + i + "].output is empty/blank", rawJsonForError);
            }
            if (containsPlaceholder(tc.input())) {
                throw new LlmSchemaViolationException("test_cases[" + i + "].input contains placeholder text", rawJsonForError);
            }
            if (containsPlaceholder(tc.output())) {
                throw new LlmSchemaViolationException("test_cases[" + i + "].output contains placeholder text", rawJsonForError);
            }
        }

        List<LanguagePackProblemPackage.Sample> samples = parseSamples(rawPackage.get("samples"));
        if (samples.isEmpty()) {
            LanguagePackProblemPackage.TestCase first = testCases.getFirst();
            samples = List.of(new LanguagePackProblemPackage.Sample(first.input(), first.output()));
        } else {
            String firstSampleInput = samples.getFirst().input() == null ? "" : samples.getFirst().input().strip();
            String firstTcInput = testCases.getFirst().input().strip();
            if (!firstSampleInput.equals(firstTcInput)) {
                throw new LlmSchemaViolationException(
                        "samples[0].input != test_cases[0].input (must match exactly)", rawJsonForError);
            }
        }

        String referenceSolutionCode = firstNonBlank(
                stringVal(rawPackage.get("reference_solution_code")),
                stringVal(rawPackage.get("reference_solution"))
        );
        if (referenceSolutionCode == null || referenceSolutionCode.isBlank()) {
            throw new LlmSchemaViolationException("reference_solution_code is missing/blank", rawJsonForError);
        }
        if (referenceSolutionCode.strip().length() < 10) {
            throw new LlmSchemaViolationException("reference_solution_code is too short (<10 chars)", rawJsonForError);
        }
        if (containsPlaceholder(referenceSolutionCode)) {
            throw new LlmSchemaViolationException("reference_solution_code contains placeholder text", rawJsonForError);
        }
        String langLower = primaryLanguage == null ? "" : primaryLanguage.toLowerCase(Locale.ROOT);
        if (langLower.startsWith("python")) {
            if (!referenceSolutionCode.contains("input(")
                    && !referenceSolutionCode.contains("sys.stdin")
                    && !referenceSolutionCode.contains("stdin.read")) {
                throw new LlmSchemaViolationException(
                        "reference_solution_code (Python) does not appear to read stdin", rawJsonForError);
            }
        }

        List<Long> relatedKcIds = sanitizeRelatedKcIds(rawPackage, defaultRelatedKcIds);
        if (relatedKcIds.isEmpty()) {
            throw new LlmSchemaViolationException("related_kc_ids is empty (must be non-empty subset)", rawJsonForError);
        }

        Map<String, String> template = parseTemplate(rawPackage.get("template"), primaryLanguage);
        if (template.isEmpty()) {
            template = Map.of(primaryLanguage, defaultTemplate(primaryLanguage));
        }

        List<Integer> unitPages = deriveUnitPages(unit);
        List<Integer> sourcePages = sanitizeSourcePages(parseIntegerList(rawPackage.get("source_pages")), unitPages);
        List<Long> sourceExampleIds = sanitizeSourceExampleIds(rawPackage, unit);
        String displayId = firstNonBlank(
                trimToNull(stringVal(unit.get("display_id"))),
                trimToNull(stringVal(rawPackage.get("display_id")))
        );

        return new LanguagePackProblemPackage(
                displayId,
                stringVal(rawPackage.get("title")),
                stringVal(rawPackage.get("description")),
                stringVal(rawPackage.get("input_description")),
                stringVal(rawPackage.get("output_description")),
                samples,
                testCases,
                template,
                parseInteger(rawPackage.get("time_limit")),
                parseInteger(rawPackage.get("memory_limit")),
                normalizeDifficulty(stringVal(rawPackage.get("difficulty"))),
                sourcePages,
                sourceExampleIds,
                relatedKcIds,
                stringVal(rawPackage.get("teaching_explanation")),
                parseStringList(rawPackage.get("common_mistakes")),
                languagePackId,
                primaryLanguage,
                referenceSolutionCode
        );
    }

    static Map<String, Object> extractSingleProblemMap(Map<String, Object> llmResult) {
        List<Map<String, Object>> problemPackages = parseProblemList(llmResult.get("problem_packages"));
        if (problemPackages.isEmpty()) {
            problemPackages = parseProblemList(llmResult.get("problems"));
        }
        if (problemPackages.isEmpty()) {
            throw new LlmSchemaViolationException("expected one problem package, got 0", String.valueOf(llmResult));
        }
        if (problemPackages.size() > 1) {
            throw new LlmSchemaViolationException(
                    "expected exactly one problem package, got " + problemPackages.size(), String.valueOf(llmResult));
        }
        return problemPackages.getFirst();
    }

    private static List<Map<String, Object>> parseProblemList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(normalized);
            }
        }
        return result;
    }

    private static List<LanguagePackProblemPackage.TestCase> parseTestCases(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<LanguagePackProblemPackage.TestCase> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> testCaseMap)) {
                continue;
            }
            result.add(new LanguagePackProblemPackage.TestCase(
                    stringVal(testCaseMap.get("input")),
                    stringVal(testCaseMap.get("output"))
            ));
        }
        return result;
    }

    private static List<LanguagePackProblemPackage.Sample> parseSamples(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<LanguagePackProblemPackage.Sample> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> sampleMap)) {
                continue;
            }
            result.add(new LanguagePackProblemPackage.Sample(
                    stringVal(sampleMap.get("input")),
                    stringVal(sampleMap.get("output"))
            ));
        }
        return result;
    }

    private static Map<String, String> parseTemplate(Object value, String primaryLanguage) {
        if (!(value instanceof Map<?, ?> rawTemplate)) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawTemplate.entrySet()) {
            String language = trimToNull(stringVal(entry.getKey()));
            if (language == null) {
                continue;
            }
            String templateBody = trimToNull(stringVal(entry.getValue()));
            if (templateBody == null) {
                continue;
            }
            normalized.put(language, templateBody);
        }
        if (normalized.isEmpty() && primaryLanguage != null) {
            return Map.of(primaryLanguage, defaultTemplate(primaryLanguage));
        }
        return Map.copyOf(normalized);
    }

    private static List<Integer> sanitizeSourcePages(List<Integer> sourcePages, List<Integer> unitPages) {
        if (unitPages.isEmpty()) {
            return dedupeIntegers(sourcePages);
        }
        if (sourcePages.isEmpty()) {
            return unitPages;
        }
        List<Integer> normalized = sourcePages.stream()
                .filter(unitPages::contains)
                .distinct()
                .toList();
        return normalized.isEmpty() ? unitPages : normalized;
    }

    private static List<Long> sanitizeSourceExampleIds(Map<String, Object> rawPackage, Map<String, Object> unit) {
        Long unitId = longVal(unit.get("id"));
        if (unitId != null) {
            return List.of(unitId);
        }
        List<Long> sourceExampleIds = parseLongList(rawPackage.get("source_example_ids"));
        if (sourceExampleIds.isEmpty()) {
            Long exampleId = longVal(rawPackage.get("example_id"));
            if (exampleId != null) {
                sourceExampleIds = List.of(exampleId);
            }
        }
        return dedupeLongs(sourceExampleIds);
    }

    private static List<Long> sanitizeRelatedKcIds(Map<String, Object> rawPackage, List<Long> defaultRelatedKcIds) {
        List<Long> relatedKcIds = parseLongList(rawPackage.get("related_kc_ids"));
        if (!relatedKcIds.isEmpty() && defaultRelatedKcIds != null && !defaultRelatedKcIds.isEmpty()) {
            Set<Long> allowed = new LinkedHashSet<>(defaultRelatedKcIds);
            List<Long> normalized = relatedKcIds.stream()
                    .filter(allowed::contains)
                    .distinct()
                    .toList();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        if (relatedKcIds.isEmpty()) {
            Long kcId = longVal(rawPackage.get("kc_id"));
            if (kcId != null) {
                relatedKcIds = List.of(kcId);
            }
        }
        if (relatedKcIds.isEmpty() || (defaultRelatedKcIds != null && !defaultRelatedKcIds.isEmpty())) {
            relatedKcIds = defaultRelatedKcIds == null ? List.of() : defaultRelatedKcIds;
        }
        return dedupeLongs(relatedKcIds);
    }

    private static List<Integer> deriveUnitPages(Map<String, Object> unit) {
        Integer start = parseInteger(unit.get("page_range_start"));
        Integer end = parseInteger(unit.get("page_range_end"));
        if (start == null) {
            return List.of();
        }
        if (end == null || end < start) {
            end = start;
        }
        List<Integer> result = new ArrayList<>();
        for (int pageNo = start; pageNo <= end; pageNo++) {
            result.add(pageNo);
        }
        return result;
    }

    private static List<Integer> dedupeIntegers(List<Integer> values) {
        return new ArrayList<>(new LinkedHashSet<>(values == null ? List.<Integer>of() : values));
    }

    private static List<Long> dedupeLongs(List<Long> values) {
        return new ArrayList<>(new LinkedHashSet<>(values == null ? List.<Long>of() : values));
    }

    private static String normalizeDifficulty(String difficulty) {
        return switch (difficulty) {
            case "Low", "Mid", "High" -> difficulty;
            default -> "Mid";
        };
    }

    private static Object parseJsonValue(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private static List<Long> parseJsonLongList(ObjectMapper objectMapper, String json) {
        Object value = parseJsonValue(objectMapper, json);
        return parseLongList(value);
    }

    private static Integer parseFirstPage(Object json, ObjectMapper objectMapper) {
        return parseJsonIntegerList(objectMapper, stringVal(json)).stream().findFirst().orElse(null);
    }

    private static Integer parseLastPage(Object json, ObjectMapper objectMapper) {
        List<Integer> pages = parseJsonIntegerList(objectMapper, stringVal(json));
        return pages.isEmpty() ? null : pages.getLast();
    }

    private static List<Integer> parseJsonIntegerList(ObjectMapper objectMapper, String json) {
        Object value = parseJsonValue(objectMapper, json);
        return parseIntegerList(value);
    }

    private static List<Integer> parseIntegerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : list) {
            Integer parsed = parseInteger(item);
            if (parsed != null && parsed > 0) {
                result.add(parsed);
            }
        }
        return result;
    }

    private static List<Long> parseLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            Long parsed = longVal(item);
            if (parsed != null && parsed > 0) {
                result.add(parsed);
            }
        }
        return result;
    }

    private static List<String> parseStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String normalized = trimToNull(stringVal(item));
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String normalized = trimToNull(stringVal(value));
        if (normalized == null) {
            return null;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String normalized = trimToNull(stringVal(value));
        if (normalized == null) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String defaultTemplate(String language) {
        String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "python3", "python" -> """
                    import sys

                    def solve():
                        # TODO: write your solution here
                        pass

                    if __name__ == "__main__":
                        solve()
                    """;
            case "java" -> """
                    import java.io.*;
                    import java.util.*;

                    public class Main {
                        public static void main(String[] args) throws Exception {
                            // TODO: write your solution here
                        }
                    }
                    """;
            default -> "// TODO: write your solution here\n";
        };
    }

    private static String firstNonBlank(String primary, String fallback) {
        String normalized = trimToNull(primary);
        return normalized == null ? stringVal(fallback) : normalized;
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
