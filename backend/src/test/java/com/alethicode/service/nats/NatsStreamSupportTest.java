package com.alethicode.service.nats;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NatsStreamSupportTest {

    @Test
    void ensureStreamShouldAddStreamWhenMissing() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        JetStreamApiException notFound = streamNotFound();
        when(management.getStreamInfo("ALETHICODE_JUDGE")).thenThrow(notFound);

        NatsStreamSupport.ensureStream(
                management,
                "ALETHICODE_JUDGE",
                List.of("judge.dispatch")
        );

        verify(management).addStream(argThat(configuration ->
                configuration.getName().equals("ALETHICODE_JUDGE")
                        && configuration.getStorageType() == StorageType.File
                        && configuration.getSubjects().equals(List.of("judge.dispatch"))
        ));
        verify(management, never()).updateStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStreamShouldUpdateExistingStreamWhenRequiredSubjectIsMissing() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamInfo existingInfo = streamInfo(StreamConfiguration.builder()
                .name("ALETHICODE_JUDGE")
                .storageType(StorageType.File)
                .subjects(List.of("legacy.topic"))
                .build());
        when(management.getStreamInfo("ALETHICODE_JUDGE")).thenReturn(existingInfo);

        NatsStreamSupport.ensureStream(
                management,
                "ALETHICODE_JUDGE",
                List.of("judge.dispatch")
        );

        verify(management).updateStream(argThat(configuration ->
                configuration.getSubjects().contains("legacy.topic")
                        && configuration.getSubjects().contains("judge.dispatch")
                        && configuration.getSubjects().size() == 2
        ));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStreamShouldNotUpdateExistingStreamWhenAllSubjectsExist() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamInfo existingInfo = streamInfo(StreamConfiguration.builder()
                .name("ALETHICODE_JUDGE")
                .storageType(StorageType.File)
                .subjects(List.of("judge.dispatch"))
                .build());
        when(management.getStreamInfo("ALETHICODE_JUDGE")).thenReturn(existingInfo);

        NatsStreamSupport.ensureStream(
                management,
                "ALETHICODE_JUDGE",
                List.of("judge.dispatch")
        );

        verify(management, never()).updateStream(any(StreamConfiguration.class));
        verify(management, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStreamShouldReconcileAfterConcurrentAddStreamAlreadyExists() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration concurrentConfiguration = StreamConfiguration.builder()
                .name("ALETHICODE_JUDGE")
                .storageType(StorageType.File)
                .subjects(List.of("legacy.topic"))
                .build();
        JetStreamApiException notFound = streamNotFound();
        StreamInfo concurrentInfo = streamInfo(concurrentConfiguration);
        when(management.getStreamInfo("ALETHICODE_JUDGE"))
                .thenThrow(notFound)
                .thenReturn(concurrentInfo);
        JetStreamApiException alreadyExists = streamAlreadyExists();
        doThrow(alreadyExists).when(management).addStream(any(StreamConfiguration.class));

        NatsStreamSupport.ensureStream(
                management,
                "ALETHICODE_JUDGE",
                List.of("judge.dispatch")
        );

        verify(management).updateStream(argThat(configuration ->
                configuration.getSubjects().contains("legacy.topic")
                        && configuration.getSubjects().contains("judge.dispatch")
                        && configuration.getSubjects().size() == 2
        ));
    }

    @Test
    void ensureStreamShouldFailFastWhenStreamNameOrSubjectsAreBlank() {
        JetStreamManagement management = mock(JetStreamManagement.class);

        assertThatThrownBy(() -> NatsStreamSupport.ensureStream(management, " ", List.of("judge.dispatch")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("streamName is required");
        assertThatThrownBy(() -> NatsStreamSupport.ensureStream(management, "ALETHICODE_JUDGE", List.of(" ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NATS subject is required");
    }

    private static StreamInfo streamInfo(StreamConfiguration configuration) {
        StreamInfo streamInfo = mock(StreamInfo.class);
        when(streamInfo.getConfiguration()).thenReturn(configuration);
        return streamInfo;
    }

    private static JetStreamApiException streamNotFound() {
        JetStreamApiException exception = mock(JetStreamApiException.class);
        when(exception.getApiErrorCode()).thenReturn(10059);
        when(exception.getErrorDescription()).thenReturn("stream not found");
        return exception;
    }

    private static JetStreamApiException streamAlreadyExists() {
        JetStreamApiException exception = mock(JetStreamApiException.class);
        when(exception.getApiErrorCode()).thenReturn(10058);
        when(exception.getErrorDescription()).thenReturn("stream name already in use");
        return exception;
    }
}
