package com.alethicode.service.account.impl;

import com.alethicode.service.account.PasswordResetThrottle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Redis 限流：
 * - 新邮箱第一次申请获得发送窗口；setIfAbsent 60s TTL
 * - 同邮箱 60s 内第二次返回 false
 * - 邮箱大小写归一化（共享同一冷却窗口）
 */
@ExtendWith(MockitoExtension.class)
class RedisPasswordResetThrottleTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private PasswordResetThrottle newThrottle() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new RedisPasswordResetThrottle(redisTemplate);
    }

    @Test
    void firstAcquireForFreshEmailReturnsTrueAndPersists60sTtl() {
        doReturn(Boolean.TRUE).when(valueOps).setIfAbsent(
                eq("password_reset_throttle:alice@example.com"),
                anyString(),
                any(Duration.class)
        );

        boolean acquired = newThrottle().tryAcquire("alice@example.com");

        assertThat(acquired).isTrue();
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).setIfAbsent(
                eq("password_reset_throttle:alice@example.com"),
                anyString(),
                ttlCaptor.capture()
        );
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void secondAcquireWithinWindowReturnsFalse() {
        doReturn(Boolean.FALSE).when(valueOps).setIfAbsent(
                eq("password_reset_throttle:alice@example.com"),
                anyString(),
                any(Duration.class)
        );

        assertThat(newThrottle().tryAcquire("alice@example.com")).isFalse();
    }

    @Test
    void normalizesEmailCaseSoMixedCaseShareSameCooldown() {
        doReturn(Boolean.TRUE).when(valueOps).setIfAbsent(
                eq("password_reset_throttle:alice@example.com"),
                anyString(),
                any(Duration.class)
        );

        PasswordResetThrottle throttle = newThrottle();

        assertThat(throttle.tryAcquire("Alice@Example.COM")).isTrue();
        verify(valueOps, times(1)).setIfAbsent(
                eq("password_reset_throttle:alice@example.com"),
                anyString(),
                any(Duration.class)
        );
    }
}
