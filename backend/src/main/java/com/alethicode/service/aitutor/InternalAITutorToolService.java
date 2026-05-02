package com.alethicode.service.aitutor;

import com.alethicode.service.aitutor.profile.ContextSignals;

import java.util.List;
import java.util.Map;

public interface InternalAITutorToolService {

    Map<String, Object> getWorkflowContext(Long problemId, Long userId, String sessionId, String language);

    Map<String, Object> getDiagnosisEvidence(String submissionId, Long userId, Long problemId, String sessionId);

    Map<String, Object> getLearnerState(Long userId, Long problemId, String sessionId, String language,
                                        ContextSignals contextSignals);

    Map<String, Object> getCoursewareHits(Long problemId, Long userId, String sessionId);

    Map<String, Object> getSimilarErrors(Long userId, Long problemId, String sessionId, String language);

    Map<String, Object> createTransferProblem(Map<String, Object> request);

    Map<String, Object> recordWorkflowEvent(Map<String, Object> request);

    Map<String, Object> dispatchVisualize(Map<String, Object> request);

    Map<String, Object> dispatchParsons(Map<String, Object> request);

    Map<String, Object> gradeParsons(Map<String, Object> request);

    Map<String, Object> getLastCards(String sessionId, int limit);

    Map<String, Object> resolveReferences(String sessionId, List<String> references);
}
