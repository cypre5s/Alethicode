package com.alethicode.service.languagepack;

public interface ProblemPackageWriteService {

    ProblemPackageWriteResult writeProblem(LanguagePackProblemPackage problemPackage, ProblemPackageWriteOptions options);
}
