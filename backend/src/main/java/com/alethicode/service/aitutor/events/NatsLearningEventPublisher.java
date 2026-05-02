package com.alethicode.service.aitutor.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.nats.NatsStreamSupport;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnExpression("'${alethicode.stream.learning-events.enabled:true}' == 'true' and '${alethicode.stream.learning-events.transport:nats}' == 'nats'")
public class NatsLearningEventPublisher implements LearningEventPublisher, InitializingBean, DisposableBean {

    private final ObjectMapper objectMapper;
    private final String natsUrl;
    private final String streamName;
    private final String learnerMemoryUpdatedSubject;
    private final String reviewPackageUpdatedSubject;
    private final String misconceptionDetectedSubject;
    private final String assignmentProblemSubmittedSubject;

    private Connection connection;
    private JetStream jetStream;

    public NatsLearningEventPublisher(
            ObjectMapper objectMapper,
            @Value("${alethicode.stream.learning-events.nats-url:}") String natsUrl,
            @Value("${alethicode.stream.learning-events.stream-name:ALETHICODE_LEARNING}") String streamName,
            @Value("${alethicode.stream.learning-events.learner-memory-updated-subject:learner.memory.updated}") String learnerMemoryUpdatedSubject,
            @Value("${alethicode.stream.learning-events.review-package-updated-subject:review.package.updated}") String reviewPackageUpdatedSubject,
            @Value("${alethicode.stream.learning-events.misconception-detected-subject:misconception.detected}") String misconceptionDetectedSubject,
            @Value("${alethicode.stream.learning-events.assignment-problem-submitted-subject:alethicode.classroom.assignment.problem.submitted}") String assignmentProblemSubmittedSubject
    ) {
        this.objectMapper = objectMapper;
        this.natsUrl = normalizeNullOnly(natsUrl, "");
        this.streamName = normalizeNullOnly(streamName, "ALETHICODE_LEARNING");
        this.learnerMemoryUpdatedSubject = normalizeNullOnly(learnerMemoryUpdatedSubject, "learner.memory.updated");
        this.reviewPackageUpdatedSubject = normalizeNullOnly(reviewPackageUpdatedSubject, "review.package.updated");
        this.misconceptionDetectedSubject = normalizeNullOnly(misconceptionDetectedSubject, "misconception.detected");
        this.assignmentProblemSubmittedSubject = normalizeNullOnly(assignmentProblemSubmittedSubject, "alethicode.classroom.assignment.problem.submitted");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (natsUrl.isBlank()) {
            throw new IllegalStateException("alethicode.stream.learning-events.nats-url is required when learning events use NATS");
        }
        NatsStreamSupport.requireNonBlank(streamName, "alethicode.stream.learning-events.stream-name is required");
        for (String subject : requiredSubjects()) {
            NatsStreamSupport.requireNonBlank(subject, "alethicode.stream.learning-events subject is required");
        }
        Options options = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(3))
                .build();
        connection = Nats.connect(options);
        JetStreamManagement management = connection.jetStreamManagement();
        ensureStream(management);
        jetStream = connection.jetStream();
    }

    @Override
    public void publishReviewPackageUpdated(Long userId, String packageId, String reason, Map<String, Object> payload) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (packageId == null || packageId.isBlank()) {
            throw new IllegalArgumentException("packageId is required");
        }
        Map<String, Object> event = baseEvent("review.package.updated", userId, payload);
        event.put("package_id", packageId);
        event.put("reason", normalize(reason, "updated"));
        publish(reviewPackageUpdatedSubject, event);
    }

    @Override
    public void publishLearnerMemoryUpdated(Long userId, String memoryKey, String memoryType, Long problemId,
                                            Map<String, Object> payload) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (memoryKey == null || memoryKey.isBlank()) {
            throw new IllegalArgumentException("memoryKey is required");
        }
        Map<String, Object> event = baseEvent("learner.memory.updated", userId, payload);
        event.put("memory_key", memoryKey);
        event.put("memory_type", normalize(memoryType, "generic"));
        if (problemId != null) {
            event.put("problem_id", problemId);
        }
        publish(learnerMemoryUpdatedSubject, event);
    }

    @Override
    public void publishAssignmentSubmissionGraded(Long userId, String assignmentId, Long problemId,
                                                   boolean isCorrect, String errorTaxonomy, Long languagePackId,
                                                   String submissionDetailId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (assignmentId == null || assignmentId.isBlank()) {
            throw new IllegalArgumentException("assignmentId is required");
        }
        if (problemId == null) {
            throw new IllegalArgumentException("problemId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignment_id", assignmentId);
        payload.put("problem_id", problemId);
        payload.put("is_correct", isCorrect);
        if (errorTaxonomy != null && !errorTaxonomy.isBlank()) {
            payload.put("error_taxonomy", errorTaxonomy);
        }
        if (languagePackId != null) {
            payload.put("language_pack_id", languagePackId);
        }
        if (submissionDetailId != null && !submissionDetailId.isBlank()) {
            payload.put("submission_detail_id", submissionDetailId);
        }
        Map<String, Object> event = baseEvent("classroom.assignment.problem.submitted", userId, payload);
        event.put("assignment_id", assignmentId);
        event.put("problem_id", problemId);
        publish(assignmentProblemSubmittedSubject, event);
    }

    @Override
    public void destroy() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private Map<String, Object> baseEvent(String eventType, Long userId, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", eventType);
        event.put("user_id", userId);
        event.put("occurred_at", Instant.now().toString());
        event.put("payload", payload == null ? Map.of() : payload);
        return event;
    }

    private void publish(String subject, Map<String, Object> event) {
        if (jetStream == null) {
            throw new IllegalStateException("NATS JetStream learning event publisher is not initialized");
        }
        try {
            jetStream.publish(subject, objectMapper.writeValueAsBytes(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize learning event", exception);
        } catch (IOException | JetStreamApiException exception) {
            throw new IllegalStateException("Failed to publish learning event to NATS JetStream", exception);
        }
    }

    void ensureStream(JetStreamManagement management) throws IOException, JetStreamApiException {
        NatsStreamSupport.ensureStream(management, streamName, requiredSubjects());
    }

    private List<String> requiredSubjects() {
        return List.of(
                learnerMemoryUpdatedSubject,
                reviewPackageUpdatedSubject,
                misconceptionDetectedSubject,
                assignmentProblemSubmittedSubject
        );
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.strip();
    }

    private String normalizeNullOnly(String value, String fallback) {
        return value == null ? fallback : value.strip();
    }
}
