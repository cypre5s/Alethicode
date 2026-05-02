package com.alethicode.service.submission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.nats.NatsStreamSupport;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.PublishAck;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnExpression("'${alethicode.stream.judge-dispatch.enabled:true}' == 'true' and '${alethicode.stream.judge-dispatch.transport:nats}' == 'nats'")
public class NatsJudgeDispatchTransport implements JudgeDispatchTransport, InitializingBean, DisposableBean {

    private final ObjectMapper objectMapper;
    private final String natsUrl;
    private final String streamName;
    private final String subject;

    private Connection connection;
    private JetStream jetStream;

    public NatsJudgeDispatchTransport(
            ObjectMapper objectMapper,
            @Value("${alethicode.stream.judge-dispatch.nats-url:}") String natsUrl,
            @Value("${alethicode.stream.judge-dispatch.stream-name:ALETHICODE_JUDGE}") String streamName,
            @Value("${alethicode.stream.judge-dispatch.subject:judge.dispatch}") String subject
    ) {
        this.objectMapper = objectMapper;
        this.natsUrl = natsUrl == null ? "" : natsUrl.strip();
        this.streamName = streamName == null ? "ALETHICODE_JUDGE" : streamName.strip();
        this.subject = subject == null ? "judge.dispatch" : subject.strip();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (natsUrl.isBlank()) {
            throw new IllegalStateException("alethicode.stream.judge-dispatch.nats-url is required when transport=nats");
        }
        NatsStreamSupport.requireNonBlank(streamName, "alethicode.stream.judge-dispatch.stream-name is required");
        NatsStreamSupport.requireNonBlank(subject, "alethicode.stream.judge-dispatch.subject is required");
        Options options = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(3))
                .build();
        this.connection = Nats.connect(options);
        JetStreamManagement management = connection.jetStreamManagement();
        ensureStream(management);
        this.jetStream = connection.jetStream();
    }

    @Override
    public String transportName() {
        return "nats";
    }

    @Override
    public String publish(Map<String, String> fields) {
        if (jetStream == null) {
            throw new IllegalStateException("NATS JetStream transport is not initialized");
        }
        try {
            PublishAck ack = jetStream.publish(subject, objectMapper.writeValueAsBytes(fields));
            return ack.getStream() + ":" + ack.getSeqno();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize judge dispatch payload", exception);
        } catch (IOException | JetStreamApiException exception) {
            throw new IllegalStateException("Failed to publish judge dispatch event to NATS JetStream", exception);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    void ensureStream(JetStreamManagement management) throws IOException, JetStreamApiException {
        NatsStreamSupport.ensureStream(management, streamName, List.of(subject));
    }
}
