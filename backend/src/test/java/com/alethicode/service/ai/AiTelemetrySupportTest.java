package com.alethicode.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiTelemetrySupportTest {

    @Test
    void callerFromFrameShouldClassifyMainAiBusinessDomains() {
        assertThat(AiTelemetrySupport.callerFromFrame(
                "com.alethicode.service.languagepack.impl.ProblemGenerationServiceImpl",
                "ProblemGenerationServiceImpl",
                "generateProblems"
        ))
                .extracting(
                        AiTelemetryCaller::service,
                        AiTelemetryCaller::scene,
                        AiTelemetryCaller::domain
                )
                .containsExactly(
                        "language-pack",
                        "language-pack.ProblemGenerationService.generateProblems",
                        "language-pack"
                );

        assertThat(AiTelemetrySupport.callerFromFrame(
                "com.alethicode.service.aitutor.eval.QaEvalHarness",
                "QaEvalHarness",
                "evaluateSingle"
        ))
                .extracting(AiTelemetryCaller::service, AiTelemetryCaller::domain)
                .containsExactly("qa-harness", "ai-tutor");

        assertThat(AiTelemetrySupport.callerFromFrame(
                "com.alethicode.service.aitutor.review.ErrorReviewPackageService",
                "ErrorReviewPackageService",
                "generateOneProblem"
        ))
                .extracting(AiTelemetryCaller::service, AiTelemetryCaller::domain)
                .containsExactly("review-package", "ai-tutor");

        assertThat(AiTelemetrySupport.callerFromFrame(
                "com.alethicode.service.classroom.ClassroomAiProblemService",
                "ClassroomAiProblemService",
                "generateProblem"
        ))
                .extracting(AiTelemetryCaller::service, AiTelemetryCaller::domain)
                .containsExactly("classroom-ai", "classroom");
    }
}
