package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What-If Branch：模拟"如果你在这道题上 AC/WA，各 KC 掌握度会如何变化"。
 * 不修改真实数据，仅做只读推算。
 */
@RestController
@RequestMapping("/api/twin/what-if")
public class WhatIfBranchController {

    private final JdbcTemplate jdbcTemplate;

    public WhatIfBranchController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> simulateBranch(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        Number problemId = (Number) body.get("problem_id");
        String scenario = (String) body.get("scenario");
        if (problemId == null || scenario == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "problem_id and scenario required");
        }
        if (!scenario.equals("ac") && !scenario.equals("wa")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario must be ac or wa");
        }

        List<Map<String, Object>> affectedKcs = jdbcTemplate.query("""
            SELECT kc.id AS kc_id, kc.name, COALESCE(m.mastery, 0) AS current_mastery,
                   m.attempt_count, m.correct_count
            FROM ai_problem_kc_mapping pkm
            JOIN language_pack_kc kc ON kc.synced_ai_kc_id = pkm.kc_id
            LEFT JOIN learner_kc_mastery m ON m.kc_id = kc.id AND m.user_id = ?
            WHERE pkm.problem_id = ?
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kc_id", rs.getLong("kc_id"));
            row.put("kc_name", rs.getString("name"));
            double currentMastery = rs.getDouble("current_mastery");
            row.put("current_mastery", currentMastery);
            int attempts = rs.getInt("attempt_count") + 1;
            int correct = rs.getInt("correct_count") + ("ac".equals(scenario) ? 1 : 0);
            double simMastery = attempts > 0 ? (double) correct / attempts : 0;
            simMastery = Math.min(1.0, Math.max(0.0, simMastery));
            row.put("simulated_mastery", Math.round(simMastery * 10000.0) / 10000.0);
            row.put("delta", Math.round((simMastery - currentMastery) * 10000.0) / 10000.0);
            return row;
        }, userId, problemId.longValue());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("problem_id", problemId);
        result.put("scenario", scenario);
        result.put("affected_kcs", affectedKcs);
        result.put("insight", affectedKcs.isEmpty()
                ? "这道题没有关联的知识点"
                : "如果你" + ("ac".equals(scenario) ? "做对" : "做错") + "这道题，将影响 " + affectedKcs.size() + " 个知识点");
        return ApiResponse.success(result);
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
