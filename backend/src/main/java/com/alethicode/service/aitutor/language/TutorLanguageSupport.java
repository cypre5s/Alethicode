package com.alethicode.service.aitutor.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TutorLanguageSupport {

    private TutorLanguageSupport() {
    }

    public static String normalizeLanguage(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.isBlank()) {
            return "";
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "python", "python3" -> "Python3";
            case "python2" -> "Python2";
            case "java" -> "Java";
            case "c++", "cpp" -> "C++";
            case "c" -> "C";
            default -> value;
        };
    }

    public static String displayLanguage(String language) {
        String normalized = normalizeLanguage(language);
        if ("Python3".equals(normalized)) {
            return "Python";
        }
        return normalized;
    }

    public static boolean isPython(String language) {
        String normalized = normalizeLanguage(language);
        return "Python3".equals(normalized) || "Python2".equals(normalized);
    }

    public static String audienceFor(String language) {
        String display = displayLanguage(language);
        if (display.isBlank()) {
            throw new IllegalStateException("current_language is required");
        }
        return "非计算机专业的 " + display + " 初学者";
    }

    public static String codeFenceLanguage(String language) {
        return switch (normalizeLanguage(language)) {
            case "Python3", "Python2" -> "python";
            case "Java" -> "java";
            case "C++" -> "cpp";
            case "C" -> "c";
            default -> "";
        };
    }

    public static List<String> parseLanguageList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String normalized = normalizeLanguage(item);
                if (!normalized.isBlank() && !result.contains(normalized)) {
                    result.add(normalized);
                }
            }
            return result;
        }
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isBlank()) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        String[] parts = text.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalizeLanguage(part.replace("\"", "").trim());
            if (!normalized.isBlank() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    public static Long parseLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String firstNonBlankLanguage(Object... candidates) {
        for (Object candidate : candidates) {
            String normalized = normalizeLanguage(candidate);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    public static String beginnerSystemRole(String roleName, LanguageAwareTutorContext context) {
        return "你是 " + roleName + "。目标用户是" + context.audience() + "。";
    }

    public static String templateLanguageLabel(String language) {
        String normalized = normalizeLanguage(language);
        return normalized.isBlank() ? "当前语言" : normalized;
    }

    public static String problemLanguageLine(LanguageAwareTutorContext context) {
        return """
                当前编程语言: %s
                题目支持语言: %s
                参考解语言: %s
                语言包主语言: %s
                目标受众: %s
                """.formatted(
                context.currentLanguage(),
                context.problemSupportedLanguages(),
                context.problemReferenceSolutionLanguage(),
                context.languagePackPrimaryLanguage(),
                context.audience()
        );
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
