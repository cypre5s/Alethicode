package com.alethicode.service.aitutor.language;

import java.util.List;
import java.util.Map;

public record LanguageAwareTutorContext(
        String currentLanguage,
        List<String> problemSupportedLanguages,
        String problemReferenceSolutionLanguage,
        Long languagePackId,
        String languagePackPrimaryLanguage,
        String audience
) {

    public LanguageAwareTutorContext {
        currentLanguage = TutorLanguageSupport.normalizeLanguage(currentLanguage);
        problemSupportedLanguages = problemSupportedLanguages == null ? List.of() : List.copyOf(problemSupportedLanguages);
        problemReferenceSolutionLanguage = TutorLanguageSupport.normalizeLanguage(problemReferenceSolutionLanguage);
        languagePackPrimaryLanguage = TutorLanguageSupport.normalizeLanguage(languagePackPrimaryLanguage);
        audience = audience == null || audience.isBlank()
                ? TutorLanguageSupport.audienceFor(currentLanguage)
                : audience;
        if (currentLanguage.isBlank()) {
            throw new IllegalStateException("current_language is required");
        }
    }

    public static LanguageAwareTutorContext from(Map<String, Object> eventData,
                                                 Map<String, Object> codeOrSubmission,
                                                 Map<String, Object> problem) {
        List<String> supportedLanguages = TutorLanguageSupport.parseLanguageList(problem.get("languages"));
        String referenceLanguage = TutorLanguageSupport.firstNonBlankLanguage(
                problem.get("reference_solution_language"),
                problem.get("problem_reference_solution_language")
        );
        String currentLanguage = TutorLanguageSupport.firstNonBlankLanguage(
                eventData.get("language"),
                codeOrSubmission.get("language"),
                referenceLanguage,
                supportedLanguages.isEmpty() ? "" : supportedLanguages.get(0)
        );
        return new LanguageAwareTutorContext(
                currentLanguage,
                supportedLanguages,
                referenceLanguage,
                TutorLanguageSupport.parseLong(problem.get("language_pack_id")),
                TutorLanguageSupport.firstNonBlankLanguage(problem.get("language_pack_primary_language")),
                TutorLanguageSupport.audienceFor(currentLanguage)
        );
    }
}
