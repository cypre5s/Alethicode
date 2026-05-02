package com.alethicode.service.languagepack.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface LanguagePackPipelineWorkflow {

    @WorkflowMethod
    void run(LanguagePackPipelineRequest request);
}
