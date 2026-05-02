package com.alethicode.service.aitutor.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiTutorProblemLanguageNormalizerTest {

    private final AiTutorProblemLanguageNormalizer normalizer =
            new AiTutorProblemLanguageNormalizer(new ObjectMapper());

    @Test
    void normalizeShouldExpandAiTutorCodingProblemToFourLanguagesAndSynthesizeMissingTemplates() {
        AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalized = normalizer.normalize(
                "global_public",
                "{\"question_type\":\"coding\"}",
                "[\"Python3\"]",
                """
                {"Python3":"//PREPEND BEGIN\\n\\n//PREPEND END\\n\\n//TEMPLATE BEGIN\\nprint(1)\\n//TEMPLATE END\\n\\n//APPEND BEGIN\\n\\n//APPEND END"}
                """
        );

        assertThat(normalized.aiTutorEnabled()).isTrue();
        assertThat(normalized.languages()).containsExactly("Python3", "C", "C++", "Java");
        assertThat(normalized.publicTemplates()).containsEntry("Python3", "print(1)\n");
        assertThat(normalized.publicTemplates().get("C")).contains("#include <stdio.h>");
        assertThat(normalized.publicTemplates().get("C++")).contains("#include <iostream>");
        assertThat(normalized.publicTemplates().get("Java")).contains("public class Main");
        assertThat(normalized.fullTemplates().get("Java")).contains("//TEMPLATE BEGIN");
        assertThat(normalized.fullTemplates().get("Java")).contains("//APPEND END");
    }

    @Test
    void normalizeShouldKeepNonAiTutorOrObjectiveProblemsUnchanged() {
        AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage objective = normalizer.normalize(
                "student_private",
                "{\"objective_question\":{\"question_type\":\"choice\",\"answer\":\"A\"}}",
                "[\"Python3\"]",
                "{\"Python3\":\"//PREPEND BEGIN\\n\\n//PREPEND END\\n\\n//TEMPLATE BEGIN\\nprint(1)\\n//TEMPLATE END\\n\\n//APPEND BEGIN\\n\\n//APPEND END\"}"
        );

        assertThat(objective.aiTutorEnabled()).isFalse();
        assertThat(objective.languages()).containsExactly("Python3");
        assertThat(objective.publicTemplates()).containsOnlyKeys("Python3");
        assertThat(objective.fullTemplates()).containsOnlyKeys("Python3");
    }

    @Test
    void normalizeShouldTreatProblemsWithCodeLanguagesAsCodingEvenWithoutExplicitQuestionType() {
        AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalized = normalizer.normalize(
                "class_public",
                "{}",
                "[\"Python3\"]",
                "{\"Python3\":\"//PREPEND BEGIN\\n\\n//PREPEND END\\n\\n//TEMPLATE BEGIN\\nprint(1)\\n//TEMPLATE END\\n\\n//APPEND BEGIN\\n\\n//APPEND END\"}"
        );

        assertThat(normalized.aiTutorEnabled()).isTrue();
        assertThat(normalized.languages()).isEqualTo(List.of("Python3", "C", "C++", "Java"));
    }
}
