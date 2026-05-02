package com.alethicode.service.languagepack.impl;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguagePackChapterIndexResolver {

    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第\\s*([0-9]+|[零〇一二三四五六七八九十百千]+)\\s*章");
    private static final Pattern PPT_PATTERN = Pattern.compile("(?i)(?:^|[^a-z0-9])PPT\\s*([0-9]+)");
    private static final Map<Character, Integer> CHINESE_DIGITS = Map.ofEntries(
            Map.entry('零', 0),
            Map.entry('〇', 0),
            Map.entry('一', 1),
            Map.entry('二', 2),
            Map.entry('三', 3),
            Map.entry('四', 4),
            Map.entry('五', 5),
            Map.entry('六', 6),
            Map.entry('七', 7),
            Map.entry('八', 8),
            Map.entry('九', 9)
    );

    private LanguagePackChapterIndexResolver() {
    }

    public static Integer resolveForPptFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String normalized = filename.strip();
        if (!isPptFilename(normalized)) {
            return null;
        }

        Matcher chapterMatcher = CHAPTER_PATTERN.matcher(normalized);
        if (chapterMatcher.find()) {
            Integer chapterIndex = parseChapterToken(chapterMatcher.group(1));
            if (chapterIndex != null && chapterIndex > 0) {
                return chapterIndex;
            }
        }

        Matcher pptMatcher = PPT_PATTERN.matcher(normalized.toUpperCase(Locale.ROOT));
        if (pptMatcher.find()) {
            try {
                int chapterIndex = Integer.parseInt(pptMatcher.group(1));
                return chapterIndex > 0 ? chapterIndex : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static boolean isPptFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ppt") || lower.endsWith(".pptx");
    }

    private static Integer parseChapterToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.strip();
        try {
            int numeric = Integer.parseInt(normalized);
            return numeric > 0 ? numeric : null;
        } catch (NumberFormatException ignored) {
            return parseChineseNumber(normalized);
        }
    }

    private static Integer parseChineseNumber(String value) {
        int total = 0;
        int current = 0;
        boolean hasDigit = false;
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            Integer digit = CHINESE_DIGITS.get(currentChar);
            if (digit != null) {
                current = digit;
                hasDigit = true;
                continue;
            }
            int unit = switch (currentChar) {
                case '十' -> 10;
                case '百' -> 100;
                case '千' -> 1000;
                default -> -1;
            };
            if (unit < 0) {
                return null;
            }
            if (!hasDigit) {
                current = 1;
            }
            total += current * unit;
            current = 0;
            hasDigit = false;
        }
        int result = total + current;
        return result > 0 ? result : null;
    }
}
