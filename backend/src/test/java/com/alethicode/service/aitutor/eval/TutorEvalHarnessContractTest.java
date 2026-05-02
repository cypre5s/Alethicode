package com.alethicode.service.aitutor.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TutorEvalHarnessContractTest {

    @Test
    void scheduledTutorJudgeRunsHourlyAndWritesTutorWorkflowProjectionEvents() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/alethicode/service/aitutor/eval/TutorEvalHarness.java"));

        assertThat(source).contains("@Scheduled(cron = \"0 0 * * * *\")");
        assertThat(source).contains("ai_tutor_workflow_event");
        assertThat(source).contains("quality_trend_score");
        assertThat(source).contains("QUALITY_TREND_SCORE");
        assertThat(source).contains("ai_tutor_generation_log");
        assertThat(source).doesNotContain("from ai_generation_log");
        assertThat(source).doesNotContain("insert into ai_workflow_event");
    }
}
