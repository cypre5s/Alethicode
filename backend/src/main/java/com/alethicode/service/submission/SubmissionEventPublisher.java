package com.alethicode.service.submission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "alethicode.stream.judge-dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class SubmissionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubmissionEventPublisher.class);

    private final JudgeDispatchTransport transport;

    public SubmissionEventPublisher(
            @org.springframework.beans.factory.annotation.Value("${alethicode.stream.judge-dispatch.transport:nats}") String transportName,
            List<JudgeDispatchTransport> transports
    ) {
        String normalizedTransport = transportName == null || transportName.isBlank() ? "nats" : transportName.strip();
        this.transport = transports.stream()
                .filter(candidate -> normalizedTransport.equals(candidate.transportName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported judge dispatch transport: " + normalizedTransport));
    }

    public void publishJudgeDispatch(String submissionId) {
        Map<String, String> fields = Map.of(
                "submissionId", submissionId,
                "type", "JUDGE_DISPATCH",
                "timestamp", Instant.now().toString()
        );
        String eventId = transport.publish(fields);
        log.info("Published judge dispatch event: submissionId={}, transport={}, eventId={}", submissionId, transport.transportName(), eventId);
    }

    public void publishRejudge(String submissionId) {
        Map<String, String> fields = Map.of(
                "submissionId", submissionId,
                "type", "REJUDGE",
                "timestamp", Instant.now().toString()
        );
        String eventId = transport.publish(fields);
        log.info("Published rejudge event: submissionId={}, transport={}, eventId={}", submissionId, transport.transportName(), eventId);
    }
}
