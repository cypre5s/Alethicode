package com.alethicode.service.languagepack.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class LanguagePackPipelineWorkflowImpl implements LanguagePackPipelineWorkflow {

    private final LanguagePackPipelineActivities activities = Workflow.newActivityStub(
            LanguagePackPipelineActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(1)
                            .build())
                    .build()
    );

    @Override
    public void run(LanguagePackPipelineRequest request) {
        activities.parseDocuments(request.taskId());
        activities.extractKcs(request.taskId());
        activities.extractExamples(request.taskId());
        activities.generateProblems(request.taskId());
        activities.validateProblems(request.taskId());
        activities.publish(request.taskId());
    }
}
