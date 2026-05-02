package com.alethicode.config;

import com.alethicode.service.submission.SubmissionJudgeStreamConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.net.InetAddress;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "alethicode.stream.judge-dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConfig.class);

    public static final String STREAM_KEY = "alethicode:judge:dispatch";
    public static final String CONSUMER_GROUP = "judge-dispatch-group";

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "alethicode.stream.judge-dispatch.transport", havingValue = "redis")
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> judgeStreamListenerContainer(
            RedisConnectionFactory connectionFactory,
            SubmissionJudgeStreamConsumer consumer
    ) {
        ensureConsumerGroup(connectionFactory);

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .batchSize(10)
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        String consumerName = resolveConsumerName();

        Subscription subscription = container.receive(
                Consumer.from(CONSUMER_GROUP, consumerName),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                consumer
        );

        container.start();
        log.info("Redis Stream consumer started: group={}, consumer={}, stream={}",
                CONSUMER_GROUP, consumerName, STREAM_KEY);
        return container;
    }

    private void ensureConsumerGroup(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        try {
            template.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("Created Redis Stream consumer group: {}", CONSUMER_GROUP);
        } catch (Exception e) {
            if (containsBusyGroup(e)) {
                log.debug("Consumer group already exists: {}", CONSUMER_GROUP);
                return;
            }

            log.warn("Failed to create consumer group, stream may not exist yet: {}", e.getMessage());
            template.opsForStream().add(STREAM_KEY, java.util.Map.of("init", "true"));
            try {
                template.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
                log.info("Created stream and consumer group: {}/{}", STREAM_KEY, CONSUMER_GROUP);
            } catch (Exception ex) {
                if (containsBusyGroup(ex)) {
                    log.debug("Consumer group already exists after stream bootstrap: {}", CONSUMER_GROUP);
                    return;
                }
                throw ex;
            }
        }
    }

    private boolean containsBusyGroup(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "consumer-" + ProcessHandle.current().pid();
        }
    }
}
