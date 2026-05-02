package com.alethicode.service.submission;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionEventPublisherTest {

    @Test
    void shouldRouteEventsToConfiguredTransport() {
        RecordingTransport redis = new RecordingTransport("redis");
        RecordingTransport nats = new RecordingTransport("nats");
        SubmissionEventPublisher publisher = new SubmissionEventPublisher("nats", List.of(redis, nats));

        publisher.publishJudgeDispatch("submission-1");

        assertThat(nats.lastPayload).containsEntry("submissionId", "submission-1");
        assertThat(nats.lastPayload).containsEntry("type", "JUDGE_DISPATCH");
        assertThat(redis.lastPayload).isNull();
    }

    @Test
    void shouldUseNatsAsDefaultTransport() {
        RecordingTransport redis = new RecordingTransport("redis");
        RecordingTransport nats = new RecordingTransport("nats");
        SubmissionEventPublisher publisher = new SubmissionEventPublisher("", List.of(redis, nats));

        publisher.publishRejudge("submission-2");

        assertThat(nats.lastPayload).containsEntry("submissionId", "submission-2");
        assertThat(nats.lastPayload).containsEntry("type", "REJUDGE");
        assertThat(redis.lastPayload).isNull();
    }

    @Test
    void shouldFailFastWhenConfiguredTransportDoesNotExist() {
        assertThatThrownBy(() -> new SubmissionEventPublisher("nats", List.of(new RecordingTransport("redis"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported judge dispatch transport");
    }

    private static final class RecordingTransport implements JudgeDispatchTransport {

        private final String name;
        private Map<String, String> lastPayload;

        private RecordingTransport(String name) {
            this.name = name;
        }

        @Override
        public String transportName() {
            return name;
        }

        @Override
        public String publish(Map<String, String> fields) {
            lastPayload = fields;
            return name + ":1";
        }
    }
}
