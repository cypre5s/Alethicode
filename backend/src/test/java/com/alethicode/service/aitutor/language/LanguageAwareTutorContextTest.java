package com.alethicode.service.aitutor.language;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanguageAwareTutorContextTest {

    @Test
    void fromShouldPreferWorkflowLanguageOverAllOtherSources() {
        LanguageAwareTutorContext context = LanguageAwareTutorContext.from(
                Map.of(
                        "language", "Java"
                ),
                Map.of(
                        "language", "C++"
                ),
                Map.of(
                        "languages", List.of("Python3", "C"),
                        "reference_solution_language", "Python3",
                        "language_pack_id", 8L,
                        "language_pack_primary_language", "C"
                )
        );

        assertEquals("Java", context.currentLanguage());
        assertEquals(List.of("Python3", "C"), context.problemSupportedLanguages());
        assertEquals("Python3", context.problemReferenceSolutionLanguage());
        assertEquals(8L, context.languagePackId());
        assertEquals("C", context.languagePackPrimaryLanguage());
        assertEquals("非计算机专业的 Java 初学者", context.audience());
    }

    @Test
    void fromShouldFallbackToProblemReferenceLanguageWhenWorkflowLanguageMissing() {
        LanguageAwareTutorContext context = LanguageAwareTutorContext.from(
                Map.of(),
                Map.of(),
                Map.of(
                        "reference_solution_language", "C++",
                        "languages", List.of("Java", "C++")
                )
        );

        assertEquals("C++", context.currentLanguage());
        assertEquals("非计算机专业的 C++ 初学者", context.audience());
    }

    @Test
    void fromShouldFailFastWhenNoLanguageCanBeResolved() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> LanguageAwareTutorContext.from(Map.of(), Map.of(), Map.of())
        );

        assertEquals("current_language is required", exception.getMessage());
    }
}
