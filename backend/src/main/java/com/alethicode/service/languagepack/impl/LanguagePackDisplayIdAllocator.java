package com.alethicode.service.languagepack.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LanguagePackDisplayIdAllocator {

    private LanguagePackDisplayIdAllocator() {
    }

    static void assignDeterministicDisplayIds(List<Map<String, Object>> units) {
        if (units == null || units.isEmpty()) {
            return;
        }
        List<Map<String, Object>> sortedUnits = units.stream()
                .sorted(java.util.Comparator
                        .comparingInt(LanguagePackDisplayIdAllocator::requiredChapterIndex)
                        .thenComparingInt(LanguagePackDisplayIdAllocator::firstSourcePage)
                        .thenComparing(row -> stringVal(row.get("source_title")))
                        .thenComparing(row -> stringVal(row.get("source_signature")))
                        .thenComparing(row -> longVal(row.get("id")) == null ? Long.MAX_VALUE : longVal(row.get("id"))))
                .toList();
        Map<Integer, Integer> chapterOrdinals = new LinkedHashMap<>();
        for (Map<String, Object> unit : sortedUnits) {
            int chapterIndex = requiredChapterIndex(unit);
            int ordinal = chapterOrdinals.getOrDefault(chapterIndex, 0) + 1;
            chapterOrdinals.put(chapterIndex, ordinal);
            unit.put("display_id", LanguagePackDisplayIdPolicy.build(chapterIndex, ordinal));
        }
    }

    private static int firstSourcePage(Map<String, Object> unit) {
        List<Integer> sourcePages = parseIntegerList(unit.get("source_pages"));
        if (!sourcePages.isEmpty()) {
            return sourcePages.getFirst();
        }
        Integer start = intVal(unit.get("page_range_start"));
        return start == null ? 0 : start;
    }

    private static int requiredChapterIndex(Map<String, Object> unit) {
        Integer chapterIndex = intVal(unit.get("chapter_index"));
        if (chapterIndex == null || chapterIndex <= 0) {
            throw new IllegalStateException("chapter_index is required for deterministic display_id assignment");
        }
        return chapterIndex;
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<Integer> parseIntegerList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Integer> rows = new java.util.ArrayList<>();
        for (Object item : rawList) {
            Integer parsed = intVal(item);
            if (parsed != null) {
                rows.add(parsed);
            }
        }
        return List.copyOf(new java.util.LinkedHashSet<>(rows));
    }
}
