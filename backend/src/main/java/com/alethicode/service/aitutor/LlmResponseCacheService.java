package com.alethicode.service.aitutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 非个性化 LLM 响应的进程内缓存。
 *
 * key 按 problemId 与 event 组合，避免缓存依赖学习者状态的输出。
 */
@Service
public class LlmResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(LlmResponseCacheService.class);
    private static final int MAX_ENTRIES = 200;
    private static final long TTL_MS = 10 * 60 * 1000;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Map<String, Object> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            cache.remove(key);
            return null;
        }
        log.debug("LLM cache HIT: key={}", key);
        return entry.value;
    }

    public void put(String key, Map<String, Object> value) {
        if (cache.size() >= MAX_ENTRIES) {
            evictOldest();
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
        log.debug("LLM cache PUT: key={}, cacheSize={}", key, cache.size());
    }

    public static String buildCacheKey(Long problemId, String event) {
        return "agent:" + problemId + ":" + event;
    }

    public int size() {
        return cache.size();
    }

    public void invalidate(Long problemId) {
        cache.keySet().removeIf(k -> k.startsWith("agent:" + problemId + ":"));
    }

    private void evictOldest() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now - e.getValue().createdAt > TTL_MS);
        if (cache.size() >= MAX_ENTRIES) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (var entry : cache.entrySet()) {
                if (entry.getValue().createdAt < oldestTime) {
                    oldestTime = entry.getValue().createdAt;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) cache.remove(oldestKey);
        }
    }

    private record CacheEntry(Map<String, Object> value, long createdAt) {}
}
