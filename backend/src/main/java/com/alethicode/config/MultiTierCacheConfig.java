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
 * Multi-tier cache (Caffeine + optional Redis) configuration.
 *
 * <p>Tier 1 — Caffeine (in-process): fast and cheap, bounded size, short TTL
 * to limit staleness. Perfect for hot request-scope lookups like
 * "is this user allowed to access problem X" that fire on every turn.
 *
 * <p>Tier 2 — Redis (process-shared): not wired here by default because our
 * Spring Session Redis instance already shoulders session load; when we take
 * ADR-0005's Redis upgrade, this configuration grows a {@code CompositeCacheManager}
 * so Caffeine remains L1 with Redis as L2.
 *
 * <p>Per-cache parameters (TTL, max size) live in {@link #caches()} so
 * Dependabot / humans can tune them without touching the manager plumbing.
 */
@Configuration
@EnableCaching
public class MultiTierCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiTierCacheConfig.class);

    /** Cache name → (max entries, TTL in seconds). */
    private static final CacheSpec[] CACHES = new CacheSpec[]{
            new CacheSpec("problemAccess", 2_000, 60),         // ownership / allowed languages
            new CacheSpec("sessionOwnership", 5_000, 30),      // tutor session owner check
            new CacheSpec("learnerState", 2_000, 30),          // learner profile snapshot
            new CacheSpec("courseware", 500, 300),             // courseware retrieval result
            new CacheSpec("aiProviderConfig", 50, 60),         // admin-mutable provider config
    };

    /**
     * Per-entry expiry jitter (0% .. {@value JITTER_RATIO_PERCENT}% of base TTL).
     * Each write generates an independent expiry so cold-restart entries do not
     * all expire simultaneously and produce a thundering herd against the upstream
     * data source. Reads do not extend TTL — that role belongs to refresh strategies.
     */
    static final int JITTER_RATIO_PERCENT = 30;

    @Bean
    @Primary
    CacheManager caffeineCacheManager(@Autowired(required = false) MeterRegistry meterRegistry) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(true);  // short-TTL null caching mitigates cache penetration
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
            // Micrometer auto-binding for Caffeine metrics happens lazily on first get;
            // once a cache is queried, hit/miss rates show up under
            // cache.gets{cache=...,result=...}
            log.info("Caffeine metrics will be exposed via Micrometer on first cache access");
        }
        return manager;
    }

    private record CacheSpec(String name, int maxSize, int ttlSeconds) {}

    /**
     * Per-entry random expiry that lives in {@code [base, base + base * jitter]}.
     * Used by every cache in {@link #CACHES} so that mass writes (hot reload,
     * bulk warm-up) do not produce a synchronized expiry storm.
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
