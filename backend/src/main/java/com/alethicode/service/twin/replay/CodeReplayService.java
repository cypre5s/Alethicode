package com.alethicode.service.twin.replay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码重放服务：从 ai_code_snapshot 表加载指定 (user, problem) 的编码历史快照，
 * 支持前端逐帧回放学生的编码过程。
 */
@Service
public class CodeReplayService {

    private static final int MAX_FRAMES_PER_REQUEST = 500;

    private final JdbcTemplate jdbcTemplate;

    public CodeReplayService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getReplayData(Long userId, Long problemId) {
        List<Map<String, Object>> frames = loadFrames(userId, problemId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("problem_id", problemId);
        result.put("frame_count", frames.size());
        result.put("frames", frames);
        result.put("has_replay", !frames.isEmpty());

        if (!frames.isEmpty()) {
            Map<String, Object> stats = computeStats(frames);
            result.put("stats", stats);
        }
        return result;
    }

    public List<Map<String, Object>> listReplayableProblems(Long userId) {
        return jdbcTemplate.query("""
            SELECT cs.problem_id, p.title, COUNT(*) AS frame_count,
                   MIN(cs.create_time) AS first_frame_at,
                   MAX(cs.create_time) AS last_frame_at,
                   SUM(cs.diff_chars_added) AS total_chars_added,
                   SUM(cs.diff_chars_deleted) AS total_chars_deleted
            FROM ai_code_snapshot cs
            JOIN problem p ON p.id = cs.problem_id
            WHERE cs.user_id = ?
            GROUP BY cs.problem_id, p.title
            HAVING COUNT(*) >= 3
            ORDER BY MAX(cs.create_time) DESC
            LIMIT 50
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("problem_id", rs.getLong("problem_id"));
            row.put("title", rs.getString("title"));
            row.put("frame_count", rs.getInt("frame_count"));
            row.put("first_frame_at", rs.getTimestamp("first_frame_at").toInstant().toString());
            row.put("last_frame_at", rs.getTimestamp("last_frame_at").toInstant().toString());
            row.put("total_chars_added", rs.getInt("total_chars_added"));
            row.put("total_chars_deleted", rs.getInt("total_chars_deleted"));
            return row;
        }, userId);
    }

    private List<Map<String, Object>> loadFrames(Long userId, Long problemId) {
        return jdbcTemplate.query("""
            SELECT id, code, trigger, char_count, line_count,
                   diff_chars_added, diff_chars_deleted, session_id, create_time
            FROM ai_code_snapshot
            WHERE user_id = ? AND problem_id = ?
            ORDER BY create_time ASC
            LIMIT ?
            """, (rs, rowNum) -> {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("frame_id", rs.getLong("id"));
            frame.put("code", rs.getString("code"));
            frame.put("trigger", rs.getString("trigger"));
            frame.put("char_count", rs.getInt("char_count"));
            frame.put("line_count", rs.getInt("line_count"));
            frame.put("diff_chars_added", rs.getInt("diff_chars_added"));
            frame.put("diff_chars_deleted", rs.getInt("diff_chars_deleted"));
            frame.put("session_id", rs.getString("session_id"));
            Timestamp ts = rs.getTimestamp("create_time");
            frame.put("timestamp", ts != null ? ts.toInstant().toString() : null);
            return frame;
        }, userId, problemId, MAX_FRAMES_PER_REQUEST);
    }

    private Map<String, Object> computeStats(List<Map<String, Object>> frames) {
        Map<String, Object> stats = new LinkedHashMap<>();
        int totalAdded = 0, totalDeleted = 0;
        int maxLineCount = 0;

        for (var f : frames) {
            totalAdded += (int) f.getOrDefault("diff_chars_added", 0);
            totalDeleted += (int) f.getOrDefault("diff_chars_deleted", 0);
            int lc = (int) f.getOrDefault("line_count", 0);
            if (lc > maxLineCount) maxLineCount = lc;
        }

        stats.put("total_frames", frames.size());
        stats.put("total_chars_added", totalAdded);
        stats.put("total_chars_deleted", totalDeleted);
        stats.put("max_line_count", maxLineCount);

        String firstTs = (String) frames.get(0).get("timestamp");
        String lastTs = (String) frames.get(frames.size() - 1).get("timestamp");
        if (firstTs != null && lastTs != null) {
            long durationMs = Instant.parse(lastTs).toEpochMilli() - Instant.parse(firstTs).toEpochMilli();
            stats.put("duration_seconds", durationMs / 1000);
        }
        return stats;
    }
}
