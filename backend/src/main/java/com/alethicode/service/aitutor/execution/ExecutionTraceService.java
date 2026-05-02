package com.alethicode.service.aitutor.execution;

import com.alethicode.service.aitutor.language.LanguageAwareTutorContext;

import java.util.Map;

public interface ExecutionTraceService {

    Map<String, Object> explain(
            LanguageAwareTutorContext context,
            String code,
            String inputSample,
            String failureReason,
            Map<String, Object> submissionEvidence
    );
}
