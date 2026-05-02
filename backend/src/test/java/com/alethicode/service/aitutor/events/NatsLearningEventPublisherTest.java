package com.alethicode.service.aitutor.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NatsLearningEventPublisherTest {

    @Test
    void ensureStreamShouldUpdateStreamWhenRequiredSubjectsAreMissing() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration existingConfiguration = StreamConfiguration.builder()
                .name("ALETHICODE_LEARNING")
                .storageType(StorageType.File)
                .subjects(List.of("legacy.topic", "learner.memory.updated"))
                .build();
        StreamInfo existingInfo = streamInfo(existingConfiguration);
        when(management.getStreamInfo("ALETHICODE_LEARNING")).thenReturn(existingInfo);
        NatsLearningEventPublisher publisher = new TestablePublisher();

        publisher.ensureStream(management);

        verify(management).updateStream(argThat(configuration ->
                configuration.getSubjects().contains("legacy.topic")
                        && configuration.getSubjects().contains("learner.memory.updated")
                        && configuration.getSubjects().contains("review.package.updated")
                        && configuration.getSubjects().contains("misconception.detected")
                        && configuration.getSubjects().contains("alethicode.classroom.assignment.problem.submitted")
                        && configuration.getSubjects().size() == 5
        ));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStreamShouldNotUpdateStreamWhenAllRequiredSubjectsExist() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration existingConfiguration = StreamConfiguration.builder()
                .name("ALETHICODE_LEARNING")
                .storageType(StorageType.File)
                .subjects(List.of(
                        "learner.memory.updated",
                        "review.package.updated",
                        "misconception.detected",
                        "alethicode.classroom.assignment.problem.submitted"
                ))
                .build();
        StreamInfo existingInfo = streamInfo(existingConfiguration);
        when(management.getStreamInfo("ALETHICODE_LEARNING")).thenReturn(existingInfo);
        NatsLearningEventPublisher publisher = new TestablePublisher();

        publisher.ensureStream(management);

        verify(management, never()).updateStream(any(StreamConfiguration.class));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStreamShouldUpdateToTargetWhenExistingConfigurationIsMissing() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamInfo missingConfigurationInfo = streamInfo(null);
        when(management.getStreamInfo("ALETHICODE_LEARNING")).thenReturn(missingConfigurationInfo);
        NatsLearningEventPublisher publisher = new TestablePublisher();

        publisher.ensureStream(management);

        verify(management).updateStream(argThat(configuration ->
                configuration.getSubjects().contains("learner.memory.updated")
                        && configuration.getSubjects().contains("review.package.updated")
                        && configuration.getSubjects().contains("misconception.detected")
                        && configuration.getSubjects().contains("alethicode.classroom.assignment.problem.submitted")
                        && configuration.getSubjects().size() == 4
        ));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    private static StreamInfo streamInfo(StreamConfiguration configuration) {
        StreamInfo streamInfo = mock(StreamInfo.class);
        when(streamInfo.getConfiguration()).thenReturn(configuration);
        return streamInfo;
    }

    private static final class TestablePublisher extends NatsLearningEventPublisher {

        private TestablePublisher() {
            super(
                    new ObjectMapper(),
                    "nats://127.0.0.1:4222",
                    "ALETHICODE_LEARNING",
                    "learner.memory.updated",
                    "review.package.updated",
                    "misconception.detected",
                    "alethicode.classroom.assignment.problem.submitted"
            );
        }
    }
}
