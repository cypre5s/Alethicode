package com.alethicode.service.submission;

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

class NatsJudgeDispatchStreamReconcileTest {

    @Test
    void transportShouldUpdateExistingStreamWhenRequiredSubjectIsMissing() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration existingConfiguration = StreamConfiguration.builder()
                .name("ALETHICODE_JUDGE")
                .storageType(StorageType.File)
                .subjects(List.of("legacy.topic"))
                .build();
        StreamInfo existingInfo = streamInfo(existingConfiguration);
        when(management.getStreamInfo("ALETHICODE_JUDGE")).thenReturn(existingInfo);
        NatsJudgeDispatchTransport transport = new TestableTransport();

        transport.ensureStream(management);

        verify(management).updateStream(argThat(configuration ->
                configuration.getSubjects().contains("legacy.topic")
                        && configuration.getSubjects().contains("judge.dispatch")
                        && configuration.getSubjects().size() == 2
        ));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void transportShouldNotUpdateWhenRequiredSubjectAlreadyExists() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration existingConfiguration = StreamConfiguration.builder()
                .name("ALETHICODE_JUDGE")
                .storageType(StorageType.File)
                .subjects(List.of("judge.dispatch"))
                .build();
        StreamInfo existingInfo = streamInfo(existingConfiguration);
        when(management.getStreamInfo("ALETHICODE_JUDGE")).thenReturn(existingInfo);
        NatsJudgeDispatchTransport transport = new TestableTransport();

        transport.ensureStream(management);

        verify(management, never()).updateStream(any(StreamConfiguration.class));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void consumerShouldUpdateExistingStreamWhenRequiredSubjectIsMissing() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration existingConfiguration = StreamConfiguration.builder()
                .name("ALETHICODE_JUDGE")
                .storageType(StorageType.File)
                .subjects(List.of("legacy.topic"))
                .build();
        StreamInfo existingInfo = streamInfo(existingConfiguration);
        when(management.getStreamInfo("ALETHICODE_JUDGE")).thenReturn(existingInfo);
        NatsJudgeDispatchConsumer consumer = new TestableConsumer();

        consumer.ensureStream(management);

        verify(management).updateStream(argThat(configuration ->
                configuration.getSubjects().contains("legacy.topic")
                        && configuration.getSubjects().contains("judge.dispatch")
                        && configuration.getSubjects().size() == 2
        ));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void consumerShouldNotUpdateWhenRequiredSubjectAlreadyExists() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration existingConfiguration = StreamConfiguration.builder()
                .name("ALETHICODE_JUDGE")
                .storageType(StorageType.File)
                .subjects(List.of("judge.dispatch"))
                .build();
        StreamInfo existingInfo = streamInfo(existingConfiguration);
        when(management.getStreamInfo("ALETHICODE_JUDGE")).thenReturn(existingInfo);
        NatsJudgeDispatchConsumer consumer = new TestableConsumer();

        consumer.ensureStream(management);

        verify(management, never()).updateStream(any(StreamConfiguration.class));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    private static StreamInfo streamInfo(StreamConfiguration configuration) {
        StreamInfo streamInfo = mock(StreamInfo.class);
        when(streamInfo.getConfiguration()).thenReturn(configuration);
        return streamInfo;
    }

    private static final class TestableTransport extends NatsJudgeDispatchTransport {

        private TestableTransport() {
            super(
                    new ObjectMapper(),
                    "nats://127.0.0.1:4222",
                    "ALETHICODE_JUDGE",
                    "judge.dispatch"
            );
        }
    }

    private static final class TestableConsumer extends NatsJudgeDispatchConsumer {

        private TestableConsumer() {
            super(
                    mock(SubmissionJudgeExecutor.class),
                    new ObjectMapper(),
                    "nats://127.0.0.1:4222",
                    "ALETHICODE_JUDGE",
                    "judge.dispatch",
                    "judge-workers"
            );
        }
    }
}
