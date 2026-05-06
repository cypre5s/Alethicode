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

    Map<String, Object> resolveReferences(String sessionId, List<String> references, String currentQuery);

    /**
     * Phase 1 chat composer plan 1.7 节定义：读 ai_tutor_workflow_session 三列拼成
     * SessionUsage 给前端 ContextUsageBar 与 tutor-graph 内部 retrieval 链路使用。
     *
     * @throws com.alethicode.service.aitutor.impl.InternalAITutorToolServiceImpl.ProblemNotFoundException
     *         session_id 不存在时抛出，由 controller 映射 404
     */
    SessionUsage getSessionUsage(String sessionId);
}
