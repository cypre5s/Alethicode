package com.alethicode.service.languagepack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record LanguagePackProblemPackage(
        String displayId,
        String title,
        String description,
        String inputDescription,
        String outputDescription,
        List<Sample> samples,
        List<TestCase> testCases,
        Map<String, String> template,
        Integer timeLimit,
        Integer memoryLimit,
        String difficulty,
        List<Integer> sourcePages,
        List<Long> sourceExampleIds,
        List<Long> relatedKcIds,
        String teachingExplanation,
        List<String> commonMistakes,
        Long languagePackId,
        String referenceSolutionLanguage,
        String referenceSolutionCode
) {

    public record Sample(String input, String output) {
    }

    public record TestCase(String input, String output) {
    }

    public LanguagePackProblemPackage withOverwrittenOutputs(List<Sample> newSamples, List<TestCase> newTestCases) {
        return new LanguagePackProblemPackage(
                displayId, title, description, inputDescription, outputDescription,
                newSamples, newTestCases, template, timeLimit, memoryLimit, difficulty,
                sourcePages, sourceExampleIds, relatedKcIds, teachingExplanation, commonMistakes,
                languagePackId, referenceSolutionLanguage, referenceSolutionCode
        );
    }

    public LanguagePackProblemPackage withReplacedInputs(Map<Integer, String> sampleReplacements,
                                                         Map<Integer, String> testCaseReplacements) {
        List<Sample> newSamples = new ArrayList<>(samples == null ? List.of() : samples);
        for (var entry : sampleReplacements.entrySet()) {
            int idx = entry.getKey();
            if (idx >= 0 && idx < newSamples.size()) {
                Sample old = newSamples.get(idx);
                newSamples.set(idx, new Sample(entry.getValue(), old.output()));
            }
        }

        List<TestCase> newTestCases = new ArrayList<>(testCases == null ? List.of() : testCases);
        for (var entry : testCaseReplacements.entrySet()) {
            int idx = entry.getKey();
            if (idx >= 0 && idx < newTestCases.size()) {
                TestCase old = newTestCases.get(idx);
                newTestCases.set(idx, new TestCase(entry.getValue(), old.output()));
            }
        }

        return new LanguagePackProblemPackage(
                displayId, title, description, inputDescription, outputDescription,
                List.copyOf(newSamples), List.copyOf(newTestCases),
                template, timeLimit, memoryLimit, difficulty,
                sourcePages, sourceExampleIds, relatedKcIds, teachingExplanation, commonMistakes,
                languagePackId, referenceSolutionLanguage, referenceSolutionCode
        );
    }
}
