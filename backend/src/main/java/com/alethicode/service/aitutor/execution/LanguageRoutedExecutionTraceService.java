package com.alethicode.service.aitutor.execution;

import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;
import com.alethicode.service.aitutor.language.TutorLanguageSupport;

import java.util.Map;

public class LanguageRoutedExecutionTraceService implements ExecutionTraceService {

    private final ExecutionTraceService pythonExecutionTraceService;
    private final ExecutionTraceService judgeBackedExecutionTraceService;

    public LanguageRoutedExecutionTraceService(ExecutionTraceService pythonExecutionTraceService,
                                               ExecutionTraceService judgeBackedExecutionTraceService) {
        this.pythonExecutionTraceService = pythonExecutionTraceService;
        this.judgeBackedExecutionTraceService = judgeBackedExecutionTraceService;
    }

    @Override
    public Map<String, Object> explain(LanguageAwareTutorContext context,
                                       String code,
                                       String inputSample,
                                       String failureReason,
                                       Map<String, Object> submissionEvidence) {
        if (TutorLanguageSupport.isPython(context.currentLanguage())) {
            return pythonExecutionTraceService.explain(context, code, inputSample, failureReason, submissionEvidence);
        }
        return judgeBackedExecutionTraceService.explain(context, code, inputSample, failureReason, submissionEvidence);
    }
}
