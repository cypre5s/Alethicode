package com.alethicode.service.submission;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SubmissionThrottleService {

    private static final ThrottlePolicy DEFAULT_IP_THROTTLE = new ThrottlePolicy(100.0, 0.1, 50.0);
    private static final ThrottlePolicy DEFAULT_USER_THROTTLE = new ThrottlePolicy(20.0, 0.03, 10.0);
    private static final ThrottlePolicy API_KEY_THROTTLE = new ThrottlePolicy(100.0, 0.5, 50.0);
    private static final ConcurrentMap<String, TokenBucketState> THROTTLE_BUCKETS = new ConcurrentHashMap<>();

    public String checkSubmissionThrottle(Long userId, String clientIp, boolean apiKeyAuth, Map<String, Object> throttlingConfig) {
        return checkThrottle(userId, clientIp, apiKeyAuth, "", throttlingConfig);
    }

    public String checkDebugThrottle(Long userId, String clientIp, boolean apiKeyAuth, Map<String, Object> throttlingConfig) {
        return checkThrottle(userId, clientIp, apiKeyAuth, "_debug", throttlingConfig);
    }

    public void resetBucketsForTesting() {
        THROTTLE_BUCKETS.clear();
    }

    private String checkThrottle(Long userId,
                                 String clientIp,
                                 boolean apiKeyAuth,
                                 String keySuffix,
                                 Map<String, Object> throttlingConfig) {
        if (userId == null) {
            return null;
        }
        if (apiKeyAuth) {
            BucketConsumeResult apiResult = consumeToken("apikey:" + userId + keySuffix, API_KEY_THROTTLE);
            if (!apiResult.allowed()) {
                return "API rate limit exceeded, wait " + (int) apiResult.waitSeconds() + " seconds";
            }
            return null;
        }

        ThrottlingPolicySet policySet = resolveThrottlingPolicies(throttlingConfig);
        BucketConsumeResult userResult = consumeToken(String.valueOf(userId) + keySuffix, policySet.userPolicy());
        if (!userResult.allowed()) {
            return "Please wait " + (int) userResult.waitSeconds() + " seconds";
        }

        String normalizedIp = trimToNull(clientIp);
        if (normalizedIp != null) {
            BucketConsumeResult ipResult = consumeToken("ip:" + normalizedIp + keySuffix, policySet.ipPolicy());
            if (!ipResult.allowed()) {
                return "Too many requests from this IP, wait " + (int) ipResult.waitSeconds() + " seconds";
            }
        }
        return null;
    }

    private ThrottlingPolicySet resolveThrottlingPolicies(Map<String, Object> throttling) {
        if (throttling == null || throttling.isEmpty()) {
            return new ThrottlingPolicySet(DEFAULT_IP_THROTTLE, DEFAULT_USER_THROTTLE);
        }
        ThrottlePolicy ipPolicy = parseThrottlePolicy(throttling.get("ip"), DEFAULT_IP_THROTTLE);
        ThrottlePolicy userPolicy = parseThrottlePolicy(throttling.get("user"), DEFAULT_USER_THROTTLE);
        return new ThrottlingPolicySet(ipPolicy, userPolicy);
    }

    private ThrottlePolicy parseThrottlePolicy(Object raw, ThrottlePolicy fallback) {
        if (!(raw instanceof Map<?, ?> map)) {
            return fallback;
        }
        double capacity = parseDouble(map.get("capacity"), fallback.capacity());
        double fillRate = parseDouble(map.get("fill_rate"), fallback.fillRate());
        double defaultCapacity = parseDouble(map.get("default_capacity"), fallback.defaultCapacity());
        if (capacity <= 0) {
            capacity = fallback.capacity();
        }
        if (fillRate <= 0) {
            fillRate = fallback.fillRate();
        }
        if (defaultCapacity <= 0) {
            defaultCapacity = fallback.defaultCapacity();
        }
        if (defaultCapacity > capacity) {
            defaultCapacity = capacity;
        }
        return new ThrottlePolicy(capacity, fillRate, defaultCapacity);
    }

    private double parseDouble(Object raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        String normalized = trimToNull(String.valueOf(raw));
        if (normalized == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BucketConsumeResult consumeToken(String key, ThrottlePolicy policy) {
        synchronized (THROTTLE_BUCKETS) {
            long now = System.nanoTime();
            TokenBucketState previous = THROTTLE_BUCKETS.get(key);

            double tokens;
            if (previous == null) {
                tokens = policy.defaultCapacity();
            } else {
                double elapsedSeconds = (now - previous.lastRefillNanos()) / 1_000_000_000.0;
                tokens = Math.min(policy.capacity(), previous.tokens() + elapsedSeconds * policy.fillRate());
            }

            if (tokens >= 1.0) {
                THROTTLE_BUCKETS.put(key, new TokenBucketState(tokens - 1.0, now));
                return new BucketConsumeResult(true, 0.0);
            }

            THROTTLE_BUCKETS.put(key, new TokenBucketState(tokens, now));
            double waitSeconds = (1.0 - tokens) / policy.fillRate();
            if (waitSeconds < 0.0) {
                waitSeconds = 0.0;
            }
            return new BucketConsumeResult(false, waitSeconds);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ThrottlePolicy(
            double capacity,
            double fillRate,
            double defaultCapacity
    ) {
    }

    private record ThrottlingPolicySet(
            ThrottlePolicy ipPolicy,
            ThrottlePolicy userPolicy
    ) {
    }

    private record TokenBucketState(
            double tokens,
            long lastRefillNanos
    ) {
    }

    private record BucketConsumeResult(
            boolean allowed,
            double waitSeconds
    ) {
    }
}
