package com.alethicode.service.aitutor.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnExpression("'${alethicode.stream.learning-events.enabled:true}' != 'true' or '${alethicode.stream.learning-events.transport:nats}' != 'nats'")
public class NoopLearningEventPublisher implements LearningEventPublisher {

    @Override
    public void publishReviewPackageUpdated(Long userId, String packageId, String reason, Map<String, Object> payload) {
    }

    @Override
    public void publishLearnerMemoryUpdated(Long userId, String memoryKey, String memoryType, Long problemId,
                                            Map<String, Object> payload) {
    }

    @Override
    public void publishAssignmentSubmissionGraded(Long userId, String assignmentId, Long problemId,
                                                   boolean isCorrect, String errorTaxonomy, Long languagePackId,
                                                   String submissionDetailId) {
    }
}
