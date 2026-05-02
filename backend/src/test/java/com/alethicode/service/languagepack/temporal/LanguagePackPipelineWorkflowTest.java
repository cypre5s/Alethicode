package com.alethicode.service.languagepack.temporal;

import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguagePackPipelineWorkflowTest {

    private TestWorkflowEnvironment testWorkflowEnvironment;

    @AfterEach
    void tearDown() {
        if (testWorkflowEnvironment != null) {
            testWorkflowEnvironment.close();
        }
    }

    @Test
    void workflowShouldRunActivitiesInPipelineOrder() {
        RecordingActivities activities = new RecordingActivities(false);
        LanguagePackPipelineWorkflow workflow = newWorkflow(activities);

        workflow.run(new LanguagePackPipelineRequest(7L));

        assertThat(activities.steps()).containsExactly(
                "parse",
                "extractKcs",
                "extractExamples",
                "generateProblems",
                "validateProblems",
                "publish"
        );
    }

    @Test
    void workflowShouldFailFastWhenAnActivityFails() {
        RecordingActivities activities = new RecordingActivities(true);
        LanguagePackPipelineWorkflow workflow = newWorkflow(activities);

        assertThatThrownBy(() -> workflow.run(new LanguagePackPipelineRequest(7L)))
                .hasStackTraceContaining("extract failed");
        assertThat(activities.steps()).containsExactly("parse", "extractKcs");
    }

    private LanguagePackPipelineWorkflow newWorkflow(RecordingActivities activities) {
        testWorkflowEnvironment = TestWorkflowEnvironment.newInstance();
        Worker worker = testWorkflowEnvironment.newWorker("language-pack-pipeline-test");
        worker.registerWorkflowImplementationTypes(LanguagePackPipelineWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        testWorkflowEnvironment.start();
        return testWorkflowEnvironment.getWorkflowClient()
                .newWorkflowStub(
                        LanguagePackPipelineWorkflow.class,
                        io.temporal.client.WorkflowOptions.newBuilder()
                                .setTaskQueue("language-pack-pipeline-test")
                                .build()
                );
    }

    private static final class RecordingActivities implements LanguagePackPipelineActivities {
        private final List<String> steps = new ArrayList<>();
        private final boolean failOnExtract;

        private RecordingActivities(boolean failOnExtract) {
            this.failOnExtract = failOnExtract;
        }

        @Override
        public void parseDocuments(Long taskId) {
            steps.add("parse");
        }

        @Override
        public void extractKcs(Long taskId) {
            steps.add("extractKcs");
            if (failOnExtract) {
                throw new IllegalStateException("extract failed");
            }
        }

        @Override
        public void extractExamples(Long taskId) {
            steps.add("extractExamples");
        }

        @Override
        public void generateProblems(Long taskId) {
            steps.add("generateProblems");
        }

        @Override
        public void validateProblems(Long taskId) {
            steps.add("validateProblems");
        }

        @Override
        public void publish(Long taskId) {
            steps.add("publish");
        }

        private List<String> steps() {
            return steps;
        }
    }
}
