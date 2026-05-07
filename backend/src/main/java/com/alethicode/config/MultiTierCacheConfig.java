package com.alethicode.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 多级缓存配置。
 *
 * <p>当前启用 Caffeine 作为进程内 L1 缓存，使用短 TTL 和容量上限控制陈旧数据。
 * Redis L2 暂不在此处启用，避免与 Spring Session Redis 的负载混杂。</p>
 *
 * <p>每个缓存的 TTL 和容量集中在 {@link #CACHES}，便于调参与审计。</p>
 */
@Configuration
@EnableCaching
public class MultiTierCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiTierCacheConfig.class);

    /** 缓存名到容量与 TTL 秒数的映射。 */
    private static final CacheSpec[] CACHES = new CacheSpec[]{
            new CacheSpec("problemAccess", 2_000, 60),         // 题目所有权与语言白名单
            new CacheSpec("sessionOwnership", 5_000, 30),      // 导学会话所有权
            new CacheSpec("learnerState", 2_000, 30),          // 学习画像快照
            new CacheSpec("courseware", 500, 300),             // 课件检索结果
            new CacheSpec("aiProviderConfig", 50, 60),         // 管理端可变 provider 配置
    };

    /**
     * 单条缓存的随机过期抖动比例。
     *
     * 冷启动批量写入后，抖动能避免同一时刻集中失效；读取不延长 TTL，刷新策略应显式处理。
     */
    static final int JITTER_RATIO_PERCENT = 30;

    @Bean
    @Primary
    CacheManager caffeineCacheManager(@Autowired(required = false) MeterRegistry meterRegistry) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(true);  // 短 TTL 空值缓存用于缓解缓存穿透。
        for (CacheSpec spec : CACHES) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder()
                    .maximumSize(spec.maxSize())
                    .expireAfter(new JitteredExpiry(spec.ttlSeconds()))
                    .recordStats();
            manager.registerCustomCache(spec.name(), builder.build());
            log.info("Caffeine cache registered: name={} maxSize={} ttl={}s jitter={}%",
                    spec.name(), spec.maxSize(), spec.ttlSeconds(), JITTER_RATIO_PERCENT);
        }
        if (meterRegistry != null) {
            // Caffeine 指标在首次访问缓存后由 Micrometer 懒绑定。
            log.info("Caffeine metrics will be exposed via Micrometer on first cache access");
        }
        return manager;
    }

    private record CacheSpec(String name, int maxSize, int ttlSeconds) {}

    /**
     * 生成 {@code [base, base + base * jitter]} 范围内的单条随机过期时间。
     */
    static final class JitteredExpiry implements Expiry<Object, Object> {

        private final long baseTtlNanos;
        private final long maxJitterNanos;

        JitteredExpiry(int baseTtlSeconds) {
            this.baseTtlNanos = TimeUnit.SECONDS.toNanos(baseTtlSeconds);
            this.maxJitterNanos = baseTtlNanos * JITTER_RATIO_PERCENT / 100L;
        }

        long jitteredExpiryNanos() {
            if (maxJitterNanos <= 0L) {
                return baseTtlNanos;
            }
            long jitter = ThreadLocalRandom.current().nextLong(maxJitterNanos + 1L);
            return baseTtlNanos + jitter;
        }

        long baseTtlNanos() {
            return baseTtlNanos;
        }

        long maxJitterNanos() {
            return maxJitterNanos;
        }

        @Override
        public long expireAfterCreate(Object key, Object value, long currentTime) {
            return jitteredExpiryNanos();
        }

        @Override
        public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
            return jitteredExpiryNanos();
        }

        @Override
        public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
