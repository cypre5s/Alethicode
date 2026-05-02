package com.alethicode.service.aitutor.assessment;

import com.alethicode.service.aitutor.language.TutorLanguageSupport;

import java.util.Map;

public class LanguageRoutedCodeQualityAssessmentService implements CodeQualityAssessmentService {

    private final CodeQualityAssessmentService pythonService;
    private final CodeQualityAssessmentService genericService;

    public LanguageRoutedCodeQualityAssessmentService(CodeQualityAssessmentService pythonService,
                                                      CodeQualityAssessmentService genericService) {
        this.pythonService = pythonService;
        this.genericService = genericService;
    }

    @Override
    public Map<String, Object> assess(String code, String language, String problemDescription) {
        if (TutorLanguageSupport.isPython(language)) {
            return pythonService.assess(code, language, problemDescription);
        }
        return genericService.assess(code, language, problemDescription);
    }
}
