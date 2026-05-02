package com.alethicode.service.languagepack;

public interface LanguagePackInitExecutionService {

    void beginStep(Long taskId, String stepKey, String message, Integer progressCurrent, Integer progressTotal);

    void reportProgress(Long taskId, String stepKey, String message, Integer progressCurrent, Integer progressTotal);

    void finishStep(Long taskId, String stepKey, String message);

    void clearOnFailure(Long taskId);
}

