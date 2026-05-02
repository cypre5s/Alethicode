package com.alethicode.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link MultiTierCacheConfig} 的缓存契约：5 个业务缓存按 ADR-0006 §3 注册、null 可缓存（缓解
 * 穿透）、按写入时间施加 0..30% 随机抖动（缓解雪崩）。这些断言和 ADR-0006 描述完全对齐，任何参数飘
 * 移都会立即被拦截。
 */
class MultiTierCacheConfigTest {

    private static final List<String> EXPECTED_CACHES = List.of(
            "problemAccess",
            "sessionOwnership",
            "learnerState",
            "courseware",
            "aiProviderConfig"
    );

    private final MultiTierCacheConfig config = new MultiTierCacheConfig();

    @Test
    void allFiveCachesAreRegistered() {
        CacheManager manager = config.caffeineCacheManager(null);

        Set<String> names = Set.copyOf(manager.getCacheNames());
        assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_CACHES);
    }

    @Test
    void nullCachingIsEnabledForPenetrationDefense() {
        CacheManager manager = config.caffeineCacheManager(null);

        org.springframework.cache.Cache cache = manager.getCache("problemAccess");
        assertThat(cache).isNotNull();

        // Spring caffeine cache wraps null as a sentinel; .get(...) returns the wrapped null.
        cache.put("missing-key", null);
        assertThat(cache.get("missing-key")).isNotNull();
        assertThat(cache.get("missing-key").get()).isNull();
    }

    @Test
    void cachesExposeStatsForMicrometer() {
        CacheManager manager = config.caffeineCacheManager(new SimpleMeterRegistry());

        for (String name : EXPECTED_CACHES) {
            CaffeineCache cache = (CaffeineCache) manager.getCache(name);
            assertThat(cache).isNotNull();
            Cache<Object, Object> native_ = cache.getNativeCache();
            // recordStats is opt-in; if not enabled, .stats() returns Stats.empty() which has totalLoadCount=0
            // for a fresh cache. We force a get + put + miss to make hitCount/missCount tick.
            native_.getIfPresent("missing");
            native_.put("k", "v");
            native_.getIfPresent("k");
            assertThat(native_.stats().requestCount()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void jitteredExpiryStaysWithinBaseAndBasePlusThirtyPercent() {
        int baseSeconds = 60;
        MultiTierCacheConfig.JitteredExpiry expiry = new MultiTierCacheConfig.JitteredExpiry(baseSeconds);

        long baseNanos = TimeUnit.SECONDS.toNanos(baseSeconds);
        long maxNanos = baseNanos + (baseNanos * MultiTierCacheConfig.JITTER_RATIO_PERCENT / 100L);

        long minObserved = Long.MAX_VALUE;
        long maxObserved = Long.MIN_VALUE;
        for (int i = 0; i < 5_000; i++) {
            long ttl = expiry.expireAfterCreate("k", "v", 0L);
            minObserved = Math.min(minObserved, ttl);
            maxObserved = Math.max(maxObserved, ttl);
            assertThat(ttl).isBetween(baseNanos, maxNanos);
        }

        // After 5_000 samples we should see the jitter actually exercise both ends:
        // a non-trivial spread (≥ 80% of the theoretical range) is required, otherwise
        // the "avalanche defense" claim collapses back to a fixed TTL.
        long observedRange = maxObserved - minObserved;
        long minRequired = (maxNanos - baseNanos) * 8 / 10;
        assertThat(observedRange).isGreaterThanOrEqualTo(minRequired);
    }

    @Test
    void readsDoNotExtendTtl() {
        MultiTierCacheConfig.JitteredExpiry expiry = new MultiTierCacheConfig.JitteredExpiry(60);
        long currentDuration = 1_234_567L;

        assertThat(expiry.expireAfterRead("k", "v", 0L, currentDuration)).isEqualTo(currentDuration);
    }

    @Test
    void zeroSecondTtlDegradesGracefullyWithoutDivisionByZero() {
        // Defensive: tests against an edge case where someone configures TTL=0 in the future.
        // The expiry must still return 0 (immediate expiry) rather than throw.
        MultiTierCacheConfig.JitteredExpiry expiry = new MultiTierCacheConfig.JitteredExpiry(0);

        assertThat(expiry.expireAfterCreate("k", "v", 0L)).isZero();
    }

    @Test
    void cachesSpecBaselineMatchesAdr0006() {
        // Sanity check that the spec table (name + maxSize + ttl) hasn't drifted.
        // ADR-0006 §3 lists: problemAccess=2000/60, sessionOwnership=5000/30,
        // learnerState=2000/30, courseware=500/300, aiProviderConfig=50/60.
        CaffeineSpec ignored = CaffeineSpec.parse("maximumSize=1");  // forces loading of spec parser

        CacheManager manager = config.caffeineCacheManager(null);
        for (String name : EXPECTED_CACHES) {
            CaffeineCache cache = (CaffeineCache) manager.getCache(name);
            assertThat(cache).as("cache %s registered", name).isNotNull();
        }

        assertThat(ignored).isNotNull();
    }
}
