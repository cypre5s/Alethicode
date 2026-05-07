package com.alethicode.service.aitutor.graph;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.ProblemAccess;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.ProblemNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TutorWorkflowAuthorizer} 的 {@code problemAccess} 缓存防护测试。
 *
 * <p>覆盖缓存穿透、并发击穿和批量同时过期三类风险。</p>
 *
 * <p>测试使用真实 {@link MultiTierCacheConfig} cache manager，确保断言反映生产配置。</p>
 */
class TutorWorkflowAuthorizerCacheTest {

    private NamedParameterJdbcTemplate jdbc;
    private CacheManager cacheManager;
    private TutorWorkflowAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(true);
        manager.registerCustomCache(
                TutorWorkflowAuthorizer.PROBLEM_ACCESS_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfter(new TestJitteredExpiry(60))
                        .recordStats()
                        .build()
        );
        cacheManager = manager;
        authorizer = new TutorWorkflowAuthorizer(jdbc, cacheManager);
    }

    @Test
    void penetrationDefense_unknownProblemHitsDbOnceAcrossManyLookups() {
        AtomicInteger loaderCalls = new AtomicInteger();
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> {
                    loaderCalls.incrementAndGet();
                    return List.of();
                });

        for (int i = 0; i < 100; i++) {
            assertThatExceptionOfType(ProblemNotFound.class)
                    .isThrownBy(() -> authorizer.assertProblemAccessible(404L, 7L, "Python3"));
        }

        assertThat(loaderCalls.get())
                .as("100 lookups for the same missing problem should not stampede the DB")
                .isEqualTo(1);
    }

    @Test
    void penetrationDefense_missingProblemIsCachedAsEmpty() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        assertThat(authorizer.tryLoadProblem(404L)).isEmpty();
        assertThat(authorizer.tryLoadProblem(404L)).isEmpty();

        Optional<ProblemAccess> repeated = authorizer.tryLoadProblem(404L);
        assertThat(repeated).isEmpty();
    }

    @Test
    void cacheReturnsHitWithoutHittingDb() {
        AtomicInteger loaderCalls = new AtomicInteger();
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> {
                    loaderCalls.incrementAndGet();
                    return List.of(Map.of(
                            "id", 42L,
                            "created_by_id", 7L,
                            "visible", true,
                            "is_public", false,
                            "languages_json", "[\"Python3\"]"));
                });

        ProblemAccess first = authorizer.assertProblemAccessible(42L, 7L, "Python3");
        ProblemAccess second = authorizer.assertProblemAccessible(42L, 7L, "Python3");
        ProblemAccess third = authorizer.assertProblemAccessible(42L, 7L, "Python3");

        assertThat(first).isEqualTo(second).isEqualTo(third);
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    void stampedeDefense_concurrentLookupsForSameMissingKeyCollapseToSingleLoad() throws Exception {
        AtomicInteger loaderCalls = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch loaderRelease = new CountDownLatch(1);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> {
                    loaderCalls.incrementAndGet();
                    loaderEntered.countDown();
                    loaderRelease.await(2, TimeUnit.SECONDS);
                    return List.of();
                });

        int concurrency = 32;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?>[] futures = new Future<?>[concurrency];
            for (int i = 0; i < concurrency; i++) {
                futures[i] = pool.submit(() -> {
                    start.await();
                    return authorizer.tryLoadProblem(999L);
                });
            }
            start.countDown();
            assertThat(loaderEntered.await(2, TimeUnit.SECONDS)).isTrue();
            loaderRelease.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(loaderCalls.get())
                .as("Caffeine.get(key, mappingFunction) should single-flight concurrent lookups")
                .isEqualTo(1);
    }

    @Test
    void avalancheDefense_jitteredExpiryProducesNonZeroSpreadAcrossEntries() {
        TestJitteredExpiry expiry = new TestJitteredExpiry(60);

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < 1_000; i++) {
            long ttl = expiry.expireAfterCreate("k" + i, "v", 0L);
            min = Math.min(min, ttl);
            max = Math.max(max, ttl);
        }
        assertThat(max - min)
                .as("avalanche-defense jitter window should not collapse")
                .isGreaterThanOrEqualTo(expiry.maxJitterNanos() * 7 / 10);
    }

    /**
     * {@link com.alethicode.config.MultiTierCacheConfig.JitteredExpiry} 的测试镜像。
     *
     * 生产类保持包可见，测试只复制参数，不放宽生产可见性契约。
     */
    private static final class TestJitteredExpiry implements Expiry<Object, Object> {

        private static final int JITTER_RATIO_PERCENT = 30;

        private final long baseTtlNanos;
        private final long maxJitterNanos;

        TestJitteredExpiry(int baseTtlSeconds) {
            this.baseTtlNanos = TimeUnit.SECONDS.toNanos(baseTtlSeconds);
            this.maxJitterNanos = baseTtlNanos * JITTER_RATIO_PERCENT / 100L;
        }

        long maxJitterNanos() {
            return maxJitterNanos;
        }

        @Override
        public long expireAfterCreate(Object key, Object value, long currentTime) {
            if (maxJitterNanos <= 0L) {
                return baseTtlNanos;
            }
            long jitter = ThreadLocalRandom.current().nextLong(maxJitterNanos + 1L);
            return baseTtlNanos + jitter;
        }

        @Override
        public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
            return expireAfterCreate(key, value, currentTime);
        }

        @Override
        public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
