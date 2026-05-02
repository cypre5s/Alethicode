package com.alethicode.service.languagepack;

public interface ProblemGenerationService {

    void generateCandidateProblems(Long taskId);

    LanguagePackProblemPackage regenerateCandidateProblem(Long taskId, String sourceSignature);
}
