package com.alethicode.service.aitutor.language;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiTutorProblemLanguageNormalizer {

    public static final List<String> AI_TUTOR_LANGUAGES = List.of("Python3", "C", "C++", "Java");
    private static final Pattern TEMPLATE_BODY_PATTERN =
            Pattern.compile("//TEMPLATE BEGIN\\n([\\s\\S]+?)//TEMPLATE END");

    private final ObjectMapper objectMapper;

    public AiTutorProblemLanguageNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedProblemLanguage normalize(String visibilityStatus,
                                               String statisticInfoJson,
                                               String languagesJson,
                                               String templateJson) {
        List<String> originalLanguages = parseStringList(languagesJson);
        Map<String, String> originalTemplates = parseTemplateMap(templateJson);
        boolean aiTutorEnabled = shouldNormalize(
                visibilityStatus,
                statisticInfoJson,
                originalLanguages,
                originalTemplates
        );
        if (!aiTutorEnabled) {
            return new NormalizedProblemLanguage(
                    false,
                    originalLanguages,
                    originalTemplates,
                    extractPublicTemplates(originalTemplates)
            );
        }

        Map<String, String> normalizedTemplates = new LinkedHashMap<>();
        for (String language : AI_TUTOR_LANGUAGES) {
            String fullTemplate = originalTemplates.get(language);
            if (fullTemplate == null || fullTemplate.isBlank()) {
                fullTemplate = buildFullTemplate(defaultTemplateBody(language));
            }
            normalizedTemplates.put(language, fullTemplate);
        }

        return new NormalizedProblemLanguage(
                true,
                AI_TUTOR_LANGUAGES,
                normalizedTemplates,
                extractPublicTemplates(normalizedTemplates)
        );
    }

    public boolean isAiTutorEnabled(String visibilityStatus,
                                    String statisticInfoJson,
                                    String languagesJson,
                                    String templateJson) {
        List<String> languages = parseStringList(languagesJson);
        Map<String, String> templates = parseTemplateMap(templateJson);
        return shouldNormalize(visibilityStatus, statisticInfoJson, languages, templates);
    }

    private boolean shouldNormalize(String visibilityStatus,
                                    String statisticInfoJson,
                                    List<String> languages,
                                    Map<String, String> templates) {
        if ("student_private".equals(trimToEmpty(visibilityStatus))) {
            return false;
        }
        String questionType = extractQuestionType(statisticInfoJson);
        if ("choice".equals(questionType) || "fill_blank".equals(questionType)) {
            return false;
        }
        if ("coding".equals(questionType)) {
            return true;
        }
        return !languages.isEmpty() || !templates.isEmpty();
    }

    private String extractQuestionType(String statisticInfoJson) {
        Map<String, Object> statisticInfo = parseJsonMap(statisticInfoJson);
        Object direct = statisticInfo.get("question_type");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct).trim();
        }
        Object objectiveQuestion = statisticInfo.get("objective_question");
        if (!(objectiveQuestion instanceof Map<?, ?> map)) {
            return "";
        }
        Object questionType = map.get("question_type");
        return questionType == null ? "" : String.valueOf(questionType).trim();
    }

    private Map<String, String> extractPublicTemplates(Map<String, String> fullTemplates) {
        Map<String, String> publicTemplates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fullTemplates.entrySet()) {
            publicTemplates.put(entry.getKey(), extractTemplateBody(entry.getValue()));
        }
        return publicTemplates;
    }

    private String extractTemplateBody(String fullTemplate) {
        if (fullTemplate == null || fullTemplate.isBlank()) {
            return "";
        }
        Matcher matcher = TEMPLATE_BODY_PATTERN.matcher(fullTemplate);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private List<String> parseStringList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private Map<String, String> parseTemplateMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private String buildFullTemplate(String templateBody) {
        return "//PREPEND BEGIN\n"
                + "\n//PREPEND END\n\n//TEMPLATE BEGIN\n"
                + templateBody
                + "\n//TEMPLATE END\n\n//APPEND BEGIN\n"
                + "\n//APPEND END";
    }

    private String defaultTemplateBody(String language) {
        return switch (language) {
            case "C" -> """
                    #include <stdio.h>

                    int main(void) {
                        // TODO: 实现解题逻辑
                        return 0;
                    }""";
            case "C++" -> """
                    #include <iostream>
                    using namespace std;

                    int main() {
                        // TODO: 实现解题逻辑
                        return 0;
                    }""";
            case "Java" -> """
                    import java.io.*;
                    import java.util.*;

                    public class Main {
                        public static void main(String[] args) throws Exception {
                            // TODO: 实现解题逻辑
                        }
                    }""";
            default -> "# TODO: 实现解题逻辑";
        };
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record NormalizedProblemLanguage(
            boolean aiTutorEnabled,
            List<String> languages,
            Map<String, String> fullTemplates,
            Map<String, String> publicTemplates
    ) {
    }
}
