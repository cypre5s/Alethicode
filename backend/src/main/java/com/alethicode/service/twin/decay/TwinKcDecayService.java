package com.alethicode.service.twin.decay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KC 级 FSRS 遗忘衰减服务。
 * 基于 twin_kc_fsrs_state 表管理每个 KC 的记忆状态：
 * fresh → fading → forgotten。
 * 不调 LLM，纯算法驱动。
 */
@Service
public class TwinKcDecayService {

    private final JdbcTemplate jdbcTemplate;

    public TwinKcDecayService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getDecayQueue(Long userId) {
        List<Map<String, Object>> fading = queryByState(userId, "fading");
        List<Map<String, Object>> forgotten = queryByState(userId, "forgotten");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fading", fading);
        result.put("forgotten", forgotten);
        result.put("fading_count", fading.size());
        result.put("forgotten_count", forgotten.size());
        return result;
    }

    public Map<String, Object> reviewKc(Long userId, Long kcId) {
        jdbcTemplate.update("""
            INSERT INTO twin_kc_fsrs_state (user_id, kc_id, decay_state, fsrs_reps, fsrs_last_review_at, fsrs_due_at, updated_at)
            VALUES (?, ?, 'fresh', 1, NOW(), NOW() + INTERVAL '3 days', NOW())
            ON CONFLICT (user_id, kc_id)
            DO UPDATE SET
                decay_state = 'fresh',
                fsrs_reps = twin_kc_fsrs_state.fsrs_reps + 1,
                fsrs_stability = twin_kc_fsrs_state.fsrs_stability * 1.3,
                fsrs_last_review_at = NOW(),
                fsrs_due_at = NOW() + (twin_kc_fsrs_state.fsrs_stability * 1.3)::INTEGER * INTERVAL '1 day',
                updated_at = NOW()
            """, userId, kcId);

        String newDueAt = jdbcTemplate.queryForObject(
                "SELECT fsrs_due_at::TEXT FROM twin_kc_fsrs_state WHERE user_id = ? AND kc_id = ?",
                String.class, userId, kcId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("new_decay_state", "fresh");
        result.put("next_due_at", newDueAt);
        return result;
    }

    public void updateDecayStates(Long userId) {
        jdbcTemplate.update("""
            UPDATE twin_kc_fsrs_state
            SET decay_state = CASE
                WHEN fsrs_due_at < NOW() - INTERVAL '7 days' THEN 'forgotten'
                WHEN fsrs_due_at < NOW() THEN 'fading'
                ELSE 'fresh'
            END, updated_at = NOW()
            WHERE user_id = ?
            """, userId);
    }

    private List<Map<String, Object>> queryByState(Long userId, String state) {
        return jdbcTemplate.query("""
            SELECT s.kc_id, kc.name AS kc_name, s.fsrs_stability, s.fsrs_reps,
                   s.fsrs_last_review_at, s.fsrs_due_at, s.decay_state
            FROM twin_kc_fsrs_state s
            JOIN language_pack_kc kc ON kc.id = s.kc_id
            WHERE s.user_id = ? AND s.decay_state = ?
            ORDER BY s.fsrs_due_at ASC
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kc_id", rs.getLong("kc_id"));
            row.put("kc_name", rs.getString("kc_name"));
            row.put("fsrs_stability", rs.getDouble("fsrs_stability"));
            row.put("fsrs_reps", rs.getInt("fsrs_reps"));
            Timestamp ts = rs.getTimestamp("fsrs_due_at");
            row.put("fsrs_due_at", ts != null ? ts.toInstant().toString() : null);
            return row;
        }, userId, state);
    }
}
