package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/arena")
public class AiArenaController {

    private final JdbcTemplate jdbcTemplate;

    public AiArenaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> startMatch(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        Number problemId = (Number) body.get("problem_id");
        if (problemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "problem_id required");
        }

        String aiCode = generateAiCode(problemId.longValue());
        String diffLevel = "normal";

        Long matchId = jdbcTemplate.queryForObject("""
            INSERT INTO ai_arena_match (user_id, problem_id, ai_code, ai_difficulty_level)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """, Long.class, userId, problemId.longValue(), aiCode, diffLevel);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("match_id", matchId);
        result.put("ai_code", aiCode);
        result.put("ai_difficulty_level", diffLevel);
        result.put("hint", "看看 AI 的代码，觉得哪里做得好、哪里可以改进？");
        return ApiResponse.success(result);
    }

    @PostMapping("/{matchId}/judge-ai")
    public ApiResponse<Map<String, Object>> judgeAi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String evaluation = (String) body.get("evaluation");
        Number score = (Number) body.get("score");
        if (evaluation == null || score == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "evaluation and score required");
        }

        jdbcTemplate.update("""
            UPDATE ai_arena_match
            SET student_evaluation = ?, student_score_for_ai = ?
            WHERE id = ? AND user_id = ?
            """, evaluation, score.intValue(), matchId, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "感谢你的评审！批判性思维是编程进步的关键。");
        return ApiResponse.success(result);
    }

    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> getHistory(Authentication authentication) {
        Long userId = requireUserId(authentication);
        List<Map<String, Object>> history = jdbcTemplate.query("""
            SELECT m.id, m.problem_id, p.title, m.ai_difficulty_level,
                   m.student_score_for_ai, m.created_at
            FROM ai_arena_match m
            JOIN problem p ON p.id = m.problem_id
            WHERE m.user_id = ?
            ORDER BY m.created_at DESC
            LIMIT 20
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("match_id", rs.getLong("id"));
            row.put("problem_title", rs.getString("title"));
            row.put("ai_difficulty_level", rs.getString("ai_difficulty_level"));
            row.put("student_score_for_ai", rs.getObject("student_score_for_ai"));
            row.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
            return row;
        }, userId);
        return ApiResponse.success(history);
    }

    private String generateAiCode(Long problemId) {
        return "# AI 的解答\n# 这段代码可能有一些可以改进的地方\nresult = []\nfor i in range(n):\n    result.append(i * 2)\nprint(result)\n";
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
