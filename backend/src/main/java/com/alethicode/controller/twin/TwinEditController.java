package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/edit")
public class TwinEditController {

    private final JdbcTemplate jdbcTemplate;

    public TwinEditController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/mastery-override")
    public ApiResponse<Map<String, Object>> overrideMastery(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        Number kcId = (Number) body.get("kc_id");
        Number overriddenMastery = (Number) body.get("overridden_mastery");
        if (kcId == null || overriddenMastery == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kc_id and overridden_mastery required");
        }
        double mastery = overriddenMastery.doubleValue();
        if (mastery < 0 || mastery > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mastery must be 0-1");
        }
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;

        Double original = null;
        try {
            original = jdbcTemplate.queryForObject(
                    "SELECT mastery::DOUBLE PRECISION FROM learner_kc_mastery WHERE user_id = ? AND kc_id = ?",
                    Double.class, userId, kcId.longValue());
        } catch (Exception ignored) {}

        jdbcTemplate.update("""
            INSERT INTO ai_learner_mastery_override (user_id, kc_id, original_mastery, overridden_mastery, reason)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, kc_id)
            DO UPDATE SET overridden_mastery = EXCLUDED.overridden_mastery, reason = EXCLUDED.reason, created_at = NOW()
            """, userId, kcId.longValue(), original, mastery, reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("original_mastery", original);
        result.put("overridden_mastery", mastery);
        return ApiResponse.success(result);
    }

    @GetMapping("/mastery-overrides")
    public ApiResponse<List<Map<String, Object>>> listOverrides(Authentication authentication) {
        Long userId = requireUserId(authentication);
        List<Map<String, Object>> overrides = jdbcTemplate.query("""
            SELECT o.kc_id, kc.name AS kc_name, o.original_mastery, o.overridden_mastery, o.reason, o.created_at
            FROM ai_learner_mastery_override o
            JOIN language_pack_kc kc ON kc.id = o.kc_id
            WHERE o.user_id = ?
            ORDER BY o.created_at DESC
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kc_id", rs.getLong("kc_id"));
            row.put("kc_name", rs.getString("kc_name"));
            row.put("original_mastery", rs.getDouble("original_mastery"));
            row.put("overridden_mastery", rs.getDouble("overridden_mastery"));
            row.put("reason", rs.getString("reason"));
            row.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
            return row;
        }, userId);
        return ApiResponse.success(overrides);
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
