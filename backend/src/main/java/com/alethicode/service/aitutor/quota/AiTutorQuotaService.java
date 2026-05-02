package com.alethicode.service.aitutor.quota;

import com.alethicode.service.aitutor.graph.TutorWorkflowProjectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Enforces per-user quotas for the LangGraph tutor workflow to prevent
 * single-account token-burn attacks (CRIT-3, 2026-05-02 渗透报告):
 *
 * <ul>
 *   <li>{@code maxActiveSessionsPerUser} — hard cap on concurrent active
 *       tutor sessions (default 10). Backed by SQL on the projection.</li>
 *   <li>{@code dailyLlmRunsPerUser} — hard cap on tutor workflow runs per
 *       UTC day (default 50). Backed by an atomic Redis INCR with a
 *       1-day TTL keyed by {@code ai_quota:daily_runs:<userId>:<UTC date>}.</li>
 * </ul>
 *
 * Quota violations throw {@link QuotaExceededException}, which the caller
 * (controller) maps to HTTP 429 with a {@code Retry-After} hint.
 */
@Service
public class AiTutorQuotaService {

    private static final DefaultRedisScript<Long> INCR_WITH_TTL_SCRIPT;

    static {
        INCR_WITH_TTL_SCRIPT = new DefaultRedisScript<>();
        INCR_WITH_TTL_SCRIPT.setScriptText(
                "local cnt = redis.call('INCR', KEYS[1])\n" +
                "if cnt == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
                "return cnt"
        );
        INCR_WITH_TTL_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final TutorWorkflowProjectionService projectionService;
    private final int dailyLlmRunsPerUser;
    private final int maxActiveSessionsPerUser;

    public AiTutorQuotaService(
            StringRedisTemplate redis,
            TutorWorkflowProjectionService projectionService,
            @Value("${alethicode.ai-quota.daily-llm-runs-per-user:50}") int dailyLlmRunsPerUser,
            @Value("${alethicode.ai-quota.max-active-sessions-per-user:10}") int maxActiveSessionsPerUser
    ) {
        if (dailyLlmRunsPerUser <= 0) {
            throw new IllegalArgumentException(
                    "alethicode.ai-quota.daily-llm-runs-per-user must be > 0 (got "
                            + dailyLlmRunsPerUser + ")");
        }
        if (maxActiveSessionsPerUser <= 0) {
            throw new IllegalArgumentException(
                    "alethicode.ai-quota.max-active-sessions-per-user must be > 0 (got "
                            + maxActiveSessionsPerUser + ")");
        }
        this.redis = redis;
        this.projectionService = projectionService;
        this.dailyLlmRunsPerUser = dailyLlmRunsPerUser;
        this.maxActiveSessionsPerUser = maxActiveSessionsPerUser;
    }

    public void enforceActiveSessionQuota(long userId) {
        long active = projectionService.countActiveSessionsForUser(userId);
        if (active >= maxActiveSessionsPerUser) {
            throw new QuotaExceededException(
                    QuotaKind.ACTIVE_SESSIONS,
                    maxActiveSessionsPerUser,
                    "active_sessions cap reached: " + active + "/" + maxActiveSessionsPerUser
            );
        }
    }

    public void enforceDailyLlmRunQuota(long userId) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String key = "ai_quota:daily_runs:" + userId + ":" + today;
        Long count = redis.execute(INCR_WITH_TTL_SCRIPT, List.of(key), "86400");
        if (count != null && count > dailyLlmRunsPerUser) {
            throw new QuotaExceededException(
                    QuotaKind.DAILY_LLM_RUNS,
                    dailyLlmRunsPerUser,
                    "daily_llm_runs cap reached: " + count + "/" + dailyLlmRunsPerUser
            );
        }
    }

    public int dailyLlmRunsPerUser() {
        return dailyLlmRunsPerUser;
    }

    public int maxActiveSessionsPerUser() {
        return maxActiveSessionsPerUser;
    }

    public enum QuotaKind {
        DAILY_LLM_RUNS,
        ACTIVE_SESSIONS
    }

    public static class QuotaExceededException extends RuntimeException {
        private final QuotaKind kind;
        private final int limit;

        public QuotaExceededException(QuotaKind kind, int limit, String message) {
            super(message);
            this.kind = kind;
            this.limit = limit;
        }

        public QuotaKind kind() {
            return kind;
        }

        public int limit() {
            return limit;
        }
    }
}
