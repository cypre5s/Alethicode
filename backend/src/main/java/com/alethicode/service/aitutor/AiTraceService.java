package com.alethicode.service.aitutor;

import com.alethicode.service.aitutor.react.ToolTraceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiTraceService {

    private static final Logger log = LoggerFactory.getLogger(AiTraceService.class);
    private final JdbcTemplate jdbcTemplate;

    public AiTraceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public void recordTrace(String traceId, Long languagePackId, String domain,
                            int iterations, List<ToolTraceEntry> entries) {
        for (ToolTraceEntry entry : entries) {
            try {
                jdbcTemplate.update("""
                    INSERT INTO ai_workflow_event (session_id, event_type, event_data, trace_id, created_at)
                    VALUES (?, 'tool_call', ?::jsonb, ?, now())
                    """,
                    traceId,
                    String.format(
                        "{\"iteration\":%d,\"tool\":\"%s\",\"guard_passed\":%s,\"latency_ms\":%d,\"domain\":\"%s\",\"language_pack_id\":%s,\"result_excerpt\":\"%s\"}",
                        entry.iteration(), escapeJson(entry.toolName()), entry.guardPassed(),
                        entry.latencyMs(), escapeJson(domain),
                        languagePackId == null ? "null" : languagePackId.toString(),
                        escapeJson(entry.resultSummary())),
                    traceId);
            } catch (Exception e) {
                log.warn("Failed to record trace entry for trace={}, tool={}: {}", traceId, entry.toolName(), e.getMessage());
            }
        }
    }

    public List<Map<String, Object>> getTraceDetails(String traceId) {
        return jdbcTemplate.queryForList("""
            SELECT id, trace_id, session_id, event_type, event_data::text AS event_data_json, created_at
            FROM ai_workflow_event
            WHERE trace_id = ?
            ORDER BY created_at
            """, traceId);
    }

    public Map<String, Object> getQualityReport(Long languagePackId) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("language_pack_id", languagePackId);

        List<Map<String, Object>> recentTraces = jdbcTemplate.queryForList("""
            SELECT trace_id, COUNT(*) AS event_count, MIN(created_at) AS started_at, MAX(created_at) AS ended_at
            FROM ai_workflow_event
            WHERE trace_id IS NOT NULL
              AND (event_data->>'language_pack_id')::bigint = ?
            GROUP BY trace_id
            ORDER BY MIN(created_at) DESC
            LIMIT 50
            """, languagePackId);
        report.put("recent_traces", recentTraces);

        List<Map<String, Object>> toolUsage = jdbcTemplate.queryForList("""
            SELECT event_data->>'tool' AS tool_name, COUNT(*) AS call_count,
                   AVG((event_data->>'latency_ms')::numeric) AS avg_latency_ms
            FROM ai_workflow_event
            WHERE (event_data->>'language_pack_id')::bigint = ? AND event_type = 'tool_call'
            GROUP BY event_data->>'tool'
            ORDER BY call_count DESC
            """, languagePackId);
        report.put("tool_usage", toolUsage);

        return report;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
