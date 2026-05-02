package com.alethicode.service.languagepack.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LanguagePackDisplayIdPolicy {

    static final Pattern DISPLAY_ID_PATTERN = Pattern.compile("^PPT(\\d+)-(\\d+)$");

    private LanguagePackDisplayIdPolicy() {
    }

    static String build(int chapterIndex, int ordinal) {
        if (chapterIndex <= 0) {
            throw new IllegalStateException("chapter_index must be positive");
        }
        if (ordinal <= 0) {
            throw new IllegalStateException("ordinal must be positive");
        }
        return "PPT" + chapterIndex + "-" + ordinal;
    }

    static boolean isValid(String displayId) {
        if (displayId == null || displayId.isBlank()) {
            return false;
        }
        return DISPLAY_ID_PATTERN.matcher(displayId.strip()).matches();
    }

    static Integer parseChapterIndex(String displayId) {
        if (displayId == null || displayId.isBlank()) {
            return null;
        }
        Matcher matcher = DISPLAY_ID_PATTERN.matcher(displayId.strip());
        if (!matcher.matches()) {
            return null;
        }
        try {
            int chapterIndex = Integer.parseInt(matcher.group(1));
            return chapterIndex > 0 ? chapterIndex : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
