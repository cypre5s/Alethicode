package com.alethicode.service.aitutor.profile;

import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.rlhf.PromptVariantSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 记录用户对 AI 教学策略卡片的反馈，写入 ai_learner_memory。
 * 仅接受白名单内的 strategy_type / rating，避免前端注入任意 memory_key 污染学习记忆表。
 *
 * <p>同一次反馈，如果前端额外带了 {@code prompt_variant_id}，还会联动
 * {@link PromptVariantSelector#recordOutcome(String, String, boolean)} 更新该变体的 ELO；
 * 没带则不触发，保持向后兼容。
 */
@Service
public class StrategyFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(StrategyFeedbackService.class);

    static final Set<String> ALLOWED_STRATEGIES = Set.of(
            "error_diagnosis",
            "problem_guide",
            "ideate_analysis",
            "post_ac",
            "transfer_problem",
            "worked_example",
            "minimal_hint"
    );

    static final Set<String> ALLOWED_RATINGS = Set.of("positive", "negative");

    private static final double CONFIDENCE_POSITIVE = 0.8;
    private static final double CONFIDENCE_NEGATIVE = 0.3;

    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private PromptVariantSelector promptVariantSelector;

    public StrategyFeedbackService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void recordFeedback(Long userId, String strategyType, String rating) {
        recordFeedback(userId, strategyType, rating, null);
    }

    public static boolean isAllowedStrategy(String strategyType) {
        return ALLOWED_STRATEGIES.contains(normalizeStrategyType(strategyType));
    }

    public static String allowedStrategiesDescription() {
        return String.join(", ", ALLOWED_STRATEGIES);
    }

    @Transactional
    public void recordFeedback(Long userId, String strategyType, String rating, String promptVariantId) {
        if (userId == null) {
            throw BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String type = normalizeStrategyType(strategyType);
        String rate = rating == null ? "" : rating.trim().toLowerCase();
        if (!ALLOWED_STRATEGIES.contains(type)) {
            throw BusinessExceptions.fromLegacy("error",
                    "strategy_type 必须是: " + String.join(", ", ALLOWED_STRATEGIES));
        }
        if (!ALLOWED_RATINGS.contains(rate)) {
            throw BusinessExceptions.fromLegacy("error", "rating 必须是 positive 或 negative");
        }
        String memoryKey = "strategy_pref_" + type;
        double confidence = "positive".equals(rate) ? CONFIDENCE_POSITIVE : CONFIDENCE_NEGATIVE;
        String memoryValue = type + " 策略评价: " + rate;
        Map<String, Object> payload = Map.of(
                "strategy_type", type,
                "rating", rate
        );
        jdbcTemplate.update("""
            INSERT INTO ai_learner_memory (
                user_id, memory_key, memory_type, memory_value, confidence,
                enabled, created_at, updated_at,
                memory_payload, source_type
            )
            VALUES (?, ?, 'teaching_strategy_preference', ?, ?,
                    true, now(), now(),
                    cast(? as jsonb), 'strategy_feedback')
            ON CONFLICT (user_id, memory_key) DO UPDATE SET
                memory_value = EXCLUDED.memory_value,
                confidence = EXCLUDED.confidence,
                memory_payload = EXCLUDED.memory_payload,
                enabled = true,
                updated_at = now()
            """, userId, memoryKey, memoryValue, confidence, toJson(payload));

        if (promptVariantSelector != null && promptVariantId != null && !promptVariantId.isBlank()) {
            try {
                promptVariantSelector.recordOutcome(type, promptVariantId, "positive".equals(rate));
            } catch (RuntimeException e) {
                log.warn("Failed to record prompt variant outcome: agent={}, variant={}, err={}",
                        type, promptVariantId, e.getMessage());
            }
        }
    }

    private static String normalizeStrategyType(String strategyType) {
        return strategyType == null ? "" : strategyType.trim().toLowerCase(Locale.ROOT);
    }

    private static String toJson(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number n) {
                sb.append(n);
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
