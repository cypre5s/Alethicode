package com.alethicode.service.aitutor.events;

import java.util.Map;

public interface LearningEventPublisher {

    LearningEventPublisher NOOP = new LearningEventPublisher() {
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
    };

    void publishReviewPackageUpdated(Long userId, String packageId, String reason, Map<String, Object> payload);

    void publishLearnerMemoryUpdated(Long userId, String memoryKey, String memoryType, Long problemId,
                                     Map<String, Object> payload);

    void publishAssignmentSubmissionGraded(Long userId, String assignmentId, Long problemId,
                                           boolean isCorrect, String errorTaxonomy, Long languagePackId,
                                           String submissionDetailId);
}
