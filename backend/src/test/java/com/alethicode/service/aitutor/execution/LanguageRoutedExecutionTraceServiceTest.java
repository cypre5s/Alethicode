package com.alethicode.service.aitutor.execution;

import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageRoutedExecutionTraceServiceTest {

    @Test
    void explainShouldRoutePythonToPythonService() {
        RecordingExecutionTraceService pythonService = new RecordingExecutionTraceService("python");
        RecordingExecutionTraceService nativeService = new RecordingExecutionTraceService("native");
        LanguageRoutedExecutionTraceService service = new LanguageRoutedExecutionTraceService(pythonService, nativeService);

        Map<String, Object> result = service.explain(
                new LanguageAwareTutorContext("Python3", List.of("Python3"), "Python3", null, "", "非计算机专业的 Python3 初学者"),
                "print(1)",
                "1",
                "",
                Map.of()
        );

        assertEquals("python", result.get("route"));
        assertEquals(1, pythonService.invocationCount);
        assertEquals(0, nativeService.invocationCount);
    }

    @Test
    void explainShouldRouteNativeLanguagesToJudgeBackedService() {
        RecordingExecutionTraceService pythonService = new RecordingExecutionTraceService("python");
        RecordingExecutionTraceService nativeService = new RecordingExecutionTraceService("native");
        LanguageRoutedExecutionTraceService service = new LanguageRoutedExecutionTraceService(pythonService, nativeService);

        Map<String, Object> result = service.explain(
                new LanguageAwareTutorContext("Java", List.of("Java"), "Java", null, "", "非计算机专业的 Java 初学者"),
                "class Main {}",
                "",
                "",
                Map.of()
        );

        assertEquals("native", result.get("route"));
        assertEquals(0, pythonService.invocationCount);
        assertEquals(1, nativeService.invocationCount);
    }

    private static final class RecordingExecutionTraceService implements ExecutionTraceService {
        private final String routeName;
        private int invocationCount;

        private RecordingExecutionTraceService(String routeName) {
            this.routeName = routeName;
        }

        @Override
        public Map<String, Object> explain(LanguageAwareTutorContext context,
                                           String code,
                                           String inputSample,
                                           String failureReason,
                                           Map<String, Object> submissionEvidence) {
            invocationCount += 1;
            return Map.of("route", routeName);
        }
    }
}
