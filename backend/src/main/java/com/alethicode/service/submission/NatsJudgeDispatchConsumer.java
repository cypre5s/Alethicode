package com.alethicode.service.submission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.nats.NatsStreamSupport;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PushSubscribeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnExpression("'${alethicode.stream.judge-dispatch.enabled:true}' == 'true' and '${alethicode.stream.judge-dispatch.transport:nats}' == 'nats'")
public class NatsJudgeDispatchConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NatsJudgeDispatchConsumer.class);

    private final SubmissionJudgeExecutor judgeExecutor;
    private final ObjectMapper objectMapper;
    private final String natsUrl;
    private final String streamName;
    private final String subject;
    private final String queueGroup;

    private volatile boolean running;
    private Connection connection;
    private Dispatcher dispatcher;
    private JetStreamSubscription subscription;

    public NatsJudgeDispatchConsumer(
            SubmissionJudgeExecutor judgeExecutor,
            ObjectMapper objectMapper,
            @Value("${alethicode.stream.judge-dispatch.nats-url:}") String natsUrl,
            @Value("${alethicode.stream.judge-dispatch.stream-name:ALETHICODE_JUDGE}") String streamName,
            @Value("${alethicode.stream.judge-dispatch.subject:judge.dispatch}") String subject,
            @Value("${alethicode.stream.judge-dispatch.queue-group:judge-workers}") String queueGroup
    ) {
        this.judgeExecutor = judgeExecutor;
        this.objectMapper = objectMapper;
        this.natsUrl = natsUrl == null ? "" : natsUrl.strip();
        this.streamName = streamName == null ? "ALETHICODE_JUDGE" : streamName.strip();
        this.subject = subject == null ? "judge.dispatch" : subject.strip();
        this.queueGroup = queueGroup == null ? "judge-workers" : queueGroup.strip();
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (natsUrl.isBlank()) {
            throw new IllegalStateException("alethicode.stream.judge-dispatch.nats-url is required when transport=nats");
        }
        NatsStreamSupport.requireNonBlank(streamName, "alethicode.stream.judge-dispatch.stream-name is required");
        NatsStreamSupport.requireNonBlank(subject, "alethicode.stream.judge-dispatch.subject is required");
        NatsStreamSupport.requireNonBlank(queueGroup, "alethicode.stream.judge-dispatch.queue-group is required");
        try {
            Options options = new Options.Builder()
                    .server(natsUrl)
                    .connectionTimeout(Duration.ofSeconds(3))
                    .build();
            connection = Nats.connect(options);
            JetStreamManagement management = connection.jetStreamManagement();
            ensureStream(management);
            JetStream jetStream = connection.jetStream();
            dispatcher = connection.createDispatcher();
            subscription = jetStream.subscribe(
                    subject,
                    queueGroup,
                    dispatcher,
                    this::handleMessage,
                    false,
                    PushSubscribeOptions.builder()
                            .stream(streamName)
                            .durable(queueGroup)
                            .build()
            );
            running = true;
            log.info("NATS JetStream consumer started: stream={}, subject={}, durable={}", streamName, subject, queueGroup);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to start NATS JetStream judge consumer", exception);
        }
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (dispatcher != null && subscription != null) {
                dispatcher.unsubscribe(subscription);
            }
        } catch (Exception exception) {
            log.warn("Failed to unsubscribe NATS judge consumer cleanly: {}", exception.getMessage());
        }
        try {
            if (connection != null) {
                if (dispatcher != null) {
                    connection.closeDispatcher(dispatcher);
                }
                connection.close();
            }
        } catch (Exception exception) {
            log.warn("Failed to close NATS connection cleanly: {}", exception.getMessage());
        } finally {
            subscription = null;
            dispatcher = null;
            connection = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private void handleMessage(Message message) {
        String submissionId = null;
        try {
            Map<String, String> payload = objectMapper.readValue(message.getData(), new TypeReference<>() {
            });
            submissionId = payload.get("submissionId");
            String type = payload.get("type");
            log.info("Received NATS judge event: type={}, submissionId={}", type, submissionId);
            judgeExecutor.executeJudge(submissionId);
            message.ack();
        } catch (Exception exception) {
            log.error("NATS judge dispatch failed for submissionId={}: {}", submissionId, exception.getMessage(), exception);
            try {
                message.nak();
            } catch (Exception ackException) {
                log.warn("Failed to NAK NATS judge message: {}", ackException.getMessage());
            }
        }
    }

    void ensureStream(JetStreamManagement management) throws IOException, JetStreamApiException {
        NatsStreamSupport.ensureStream(management, streamName, List.of(subject));
    }
}
