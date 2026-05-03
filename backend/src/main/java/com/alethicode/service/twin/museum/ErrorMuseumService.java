package com.alethicode.service.twin.museum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ErrorMuseumService {

    private static final Logger log = LoggerFactory.getLogger(ErrorMuseumService.class);
    private static final int MAX_PINS = 9;

    private final JdbcTemplate jdbcTemplate;

    public ErrorMuseumService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listPins(Long userId) {
        return jdbcTemplate.query("""
            SELECT p.id AS pin_id, p.memory_id, p.annotation, p.pin_order, p.created_at,
                   m.memory_key, m.memory_value, m.memory_type, m.memory_payload, m.confidence,
                   m.source_problem_id, pr.title AS problem_title
            FROM ai_learner_misconception_pin p
            JOIN ai_learner_memory m ON m.id = p.memory_id
            LEFT JOIN problem pr ON pr.id = m.source_problem_id
            WHERE p.user_id = ?
            ORDER BY p.pin_order ASC
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pin_id", rs.getLong("pin_id"));
            row.put("memory_id", rs.getLong("memory_id"));
            row.put("annotation", rs.getString("annotation"));
            row.put("pin_order", rs.getInt("pin_order"));
            row.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
            row.put("memory_key", rs.getString("memory_key"));
            row.put("memory_value", rs.getString("memory_value"));
            row.put("memory_type", rs.getString("memory_type"));
            row.put("confidence", rs.getDouble("confidence"));
            row.put("problem_title", rs.getString("problem_title"));
            return row;
        }, userId);
    }

    @Transactional
    public long pinMemory(Long userId, Long memoryId, String annotation) {
        Long memoryOwner = jdbcTemplate.queryForObject(
                "SELECT user_id FROM ai_learner_memory WHERE id = ?",
                Long.class, memoryId);
        if (memoryOwner == null || !memoryOwner.equals(userId)) {
            throw new IllegalArgumentException("memory-not-yours");
        }

        int currentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_learner_misconception_pin WHERE user_id = ?",
                Integer.class, userId);
        if (currentCount >= MAX_PINS) {
            throw new IllegalArgumentException("max-pins-reached");
        }

        int nextOrder = currentCount;
        jdbcTemplate.update("""
            INSERT INTO ai_learner_misconception_pin (user_id, memory_id, annotation, pin_order)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, memory_id) DO UPDATE SET annotation = EXCLUDED.annotation, updated_at = NOW()
            """, userId, memoryId, annotation, nextOrder);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM ai_learner_misconception_pin WHERE user_id = ? AND memory_id = ?",
                Long.class, userId, memoryId);
    }

    public void updatePin(Long userId, Long pinId, String annotation, Integer pinOrder) {
        int updated = jdbcTemplate.update("""
            UPDATE ai_learner_misconception_pin
            SET annotation = COALESCE(?, annotation),
                pin_order = COALESCE(?, pin_order),
                updated_at = NOW()
            WHERE id = ? AND user_id = ?
            """, annotation, pinOrder, pinId, userId);
        if (updated == 0) {
            throw new IllegalArgumentException("pin-not-found");
        }
    }

    public void unpinMemory(Long userId, Long pinId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM ai_learner_misconception_pin WHERE id = ? AND user_id = ?",
                pinId, userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("pin-not-found");
        }
    }
}
