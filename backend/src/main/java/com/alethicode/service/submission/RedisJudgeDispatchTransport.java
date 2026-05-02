package com.alethicode.service.submission;

import com.alethicode.config.RedisStreamConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnExpression("'${alethicode.stream.judge-dispatch.enabled:true}' == 'true' and '${alethicode.stream.judge-dispatch.transport:nats}' == 'redis'")
public class RedisJudgeDispatchTransport implements JudgeDispatchTransport {

    private final StringRedisTemplate redisTemplate;

    public RedisJudgeDispatchTransport(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String transportName() {
        return "redis";
    }

    @Override
    public String publish(Map<String, String> fields) {
        RecordId recordId = redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(RedisStreamConfig.STREAM_KEY)
                        .ofMap(fields)
        );
        return recordId == null ? "" : recordId.getValue();
    }
}
