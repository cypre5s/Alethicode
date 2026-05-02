package com.alethicode.service.aitutor.parsons;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NFK 训练集 KC 覆盖度缓存。
 *
 * <p>启动时一次性扫描 {@code ai_problem_kc_mapping × submission}（180 天窗口）
 * 统计每个 KC 的样本计数；落到 Caffeine 与 ConcurrentMap 双层。
 * 后台按 {@code alethicode.parsons.routing.kc-coverage-refresh-interval} 周期刷新（默认 1 小时）。</p>
 *
 * <p>Spring 全局未启用 {@code @EnableScheduling}，本服务自维护一个单线程
 * {@link ScheduledExecutorService} 守护线程做周期刷新，关停时随容器一起退出。</p>
 *
 * <p>启动尚未首次刷新完成时返回 0，触发 {@link MasteryNfkProjectionService}
 * 走 BKT 兜底，failfast 风格而非防御性逻辑。</p>
 */
@Service
public class KcCoverageRegistry {

    private static final Logger log = LoggerFactory.getLogger(KcCoverageRegistry.class);

    private final JdbcTemplate jdbcTemplate;
    private final ParsonsProperties parsonsProperties;
    private final AtomicReference<Map<Long, Integer>> snapshot = new AtomicReference<>(new ConcurrentHashMap<>());
    private final Cache<Long, Integer> cache;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;

    public KcCoverageRegistry(JdbcTemplate jdbcTemplate, ParsonsProperties parsonsProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.parsonsProperties = parsonsProperties;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(parsonsProperties.getRouting().getKcCoverageCacheTtl())
                .maximumSize(20_000)
                .build();
    }

    @PostConstruct
    public void warmup() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("KcCoverageRegistry warmup failed; coverage will be 0 until next refresh: {}", e.getMessage());
        }
        Duration interval = parsonsProperties.getRouting().getKcCoverageRefreshInterval();
        long millis = Math.max(60_000L, interval.toMillis());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kc-coverage-refresh");
            t.setDaemon(true);
            return t;
        });
        this.refreshTask = scheduler.scheduleAtFixedRate(this::safeRefresh, millis, millis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safeRefresh() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("KcCoverageRegistry scheduled refresh failed: {}", e.getMessage());
        }
    }

    public void refresh() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT m.kc_id AS kc_id,
                       count(DISTINCT (s.user_id, s.problem_id)) AS interaction_count
                FROM ai_problem_kc_mapping m
                JOIN submission s ON s.problem_id = m.problem_id
                WHERE s.create_time > now() - interval '180 day'
                GROUP BY m.kc_id
                """);
        Map<Long, Integer> next = new ConcurrentHashMap<>(Math.max(16, rows.size() * 2));
        for (Map<String, Object> row : rows) {
            Object kcRaw = row.get("kc_id");
            Object cntRaw = row.get("interaction_count");
            if (kcRaw == null || cntRaw == null) {
                continue;
            }
            long kcId = ((Number) kcRaw).longValue();
            int count = ((Number) cntRaw).intValue();
            next.put(kcId, count);
            cache.put(kcId, count);
        }
        snapshot.set(next);
        log.info("KcCoverageRegistry refreshed: kcCount={}, threshold={}",
                next.size(), parsonsProperties.getRouting().getNfkCoverageThreshold());
    }

    public int getCoverage(long kcId) {
        Integer fromCache = cache.getIfPresent(kcId);
        if (fromCache != null) {
            return fromCache;
        }
        Integer fromSnap = snapshot.get().get(kcId);
        return fromSnap == null ? 0 : fromSnap;
    }

    public Map<Long, Integer> snapshot() {
        return Map.copyOf(snapshot.get());
    }
}
