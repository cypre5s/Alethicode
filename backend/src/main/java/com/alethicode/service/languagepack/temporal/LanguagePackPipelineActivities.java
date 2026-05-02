package com.alethicode.service.languagepack.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface LanguagePackPipelineActivities {

    @ActivityMethod
    void parseDocuments(Long taskId);

    @ActivityMethod
    void extractKcs(Long taskId);

    @ActivityMethod
    void extractExamples(Long taskId);

    @ActivityMethod
    void generateProblems(Long taskId);

    @ActivityMethod
    void validateProblems(Long taskId);

    @ActivityMethod
    void publish(Long taskId);
}
