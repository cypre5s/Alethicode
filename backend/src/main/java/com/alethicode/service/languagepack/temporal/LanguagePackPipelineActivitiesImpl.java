package com.alethicode.service.languagepack.temporal;

import com.alethicode.service.languagepack.DocumentParsingService;
import com.alethicode.service.languagepack.ExampleExtractionService;
import com.alethicode.service.languagepack.KcExtractionService;
import com.alethicode.service.languagepack.LanguagePackPublishService;
import com.alethicode.service.languagepack.ProblemGenerationService;
import com.alethicode.service.languagepack.ProblemValidationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "alethicode.temporal.enabled", havingValue = "true")
public class LanguagePackPipelineActivitiesImpl implements LanguagePackPipelineActivities {

    private final DocumentParsingService documentParsingService;
    private final KcExtractionService kcExtractionService;
    private final ExampleExtractionService exampleExtractionService;
    private final ProblemGenerationService problemGenerationService;
    private final ProblemValidationService problemValidationService;
    private final LanguagePackPublishService languagePackPublishService;

    public LanguagePackPipelineActivitiesImpl(
            DocumentParsingService documentParsingService,
            KcExtractionService kcExtractionService,
            ExampleExtractionService exampleExtractionService,
            ProblemGenerationService problemGenerationService,
            ProblemValidationService problemValidationService,
            LanguagePackPublishService languagePackPublishService
    ) {
        this.documentParsingService = documentParsingService;
        this.kcExtractionService = kcExtractionService;
        this.exampleExtractionService = exampleExtractionService;
        this.problemGenerationService = problemGenerationService;
        this.problemValidationService = problemValidationService;
        this.languagePackPublishService = languagePackPublishService;
    }

    @Override
    public void parseDocuments(Long taskId) {
        documentParsingService.parseDocuments(taskId);
    }

    @Override
    public void extractKcs(Long taskId) {
        kcExtractionService.extractChaptersAndKcs(taskId);
    }

    @Override
    public void extractExamples(Long taskId) {
        exampleExtractionService.extractExamples(taskId);
    }

    @Override
    public void generateProblems(Long taskId) {
        problemGenerationService.generateCandidateProblems(taskId);
    }

    @Override
    public void validateProblems(Long taskId) {
        problemValidationService.validateCandidates(taskId);
    }

    @Override
    public void publish(Long taskId) {
        languagePackPublishService.publishPack(taskId);
    }
}
