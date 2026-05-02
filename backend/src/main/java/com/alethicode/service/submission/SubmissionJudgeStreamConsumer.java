package com.alethicode.service.submission;

import com.alethicode.config.RedisStreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SubmissionJudgeStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(SubmissionJudgeStreamConsumer.class);

    private final SubmissionJudgeExecutor judgeExecutor;
    private final StringRedisTemplate redisTemplate;

    public SubmissionJudgeStreamConsumer(SubmissionJudgeExecutor judgeExecutor,
                                         StringRedisTemplate redisTemplate) {
        this.judgeExecutor = judgeExecutor;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        String submissionId = body.get("submissionId");
        String type = body.get("type");

        log.info("Received stream event: type={}, submissionId={}, recordId={}",
                type, submissionId, message.getId());

        try {
            judgeExecutor.executeJudge(submissionId);

            redisTemplate.opsForStream().acknowledge(
                    RedisStreamConfig.STREAM_KEY,
                    RedisStreamConfig.CONSUMER_GROUP,
                    message.getId()
            );

            log.info("Judge dispatch completed and acknowledged: submissionId={}", submissionId);
        } catch (Exception e) {
            log.error("Judge dispatch failed for submissionId={}: {}", submissionId, e.getMessage(), e);
        }
    }
}
