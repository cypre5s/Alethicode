package com.alethicode.service.account.impl;

import com.alethicode.service.account.PasswordResetThrottle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 基于 Redis 的按邮箱冷却限流。
 *
 * <p>实现：{@code SET key value NX EX 60}（{@link
 * org.springframework.data.redis.core.ValueOperations#setIfAbsent} 的语义）。
 * 第一次成功落 key 即获得发送窗口；窗口期内（60 秒）再请求同一邮箱，{@code setIfAbsent}
 * 返回 false，调用方应据此返回限流错误。</p>
 *
 * <p>邮箱在 key 中统一小写归一，避免攻击者通过大小写绕过限流。</p>
 */
@Component
public class RedisPasswordResetThrottle implements PasswordResetThrottle {

    static final String KEY_PREFIX = "password_reset_throttle:";
    static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordResetThrottle(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String email) {
        String key = KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", COOLDOWN);
        return Boolean.TRUE.equals(ok);
    }
}
