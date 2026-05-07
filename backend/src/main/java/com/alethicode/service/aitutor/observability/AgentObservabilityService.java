package com.alethicode.service.aitutor.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 面向管理端 AI 观测驾驶舱的聚合查询服务。
 *
 * <p>数据源：
 * <ul>
 *   <li>{@code ai_tutor_workflow_event} 表中的 {@code trace_span} / {@code tool_call} / {@code quality_trend_score} 事件；</li>
 *   <li>{@code ai_tutor_workflow_event} 的 {@code trace_id} 列；</li>
 *   <li>所有聚合都使用 V52 为 {@code event_type + created_at} / agent_name / tool_name 建的表达式索引。</li>
 * </ul>
 *
 * <p>不新建业务表；任何增强指标都应先以 JSONB 字段表达。
 */
@Service
public class AgentObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(AgentObservabilityService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentObservabilityService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Agent 概览：调用量、平均时延、失败率、按 agent 分组、小时趋势。
     * 本地 LangGraph 投影当前主要写 TASK_COMPLETED / TASK_FAILED 终态事件；
     * trace_span 作为增强埋点存在时也会被纳入同一统计口径。
     *
     * @param range 支持 today、7d、30d
     */
    public Map<String, Object> getAgentsOverview(String range) {
        RangeSpec spec = resolveRange(range);
        Timestamp since = spec.sinceTimestamp();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("range", spec.canonical());

        Map<String, Object> aggregate = jdbcTemplate.queryForMap(
                """
                WITH observed_events AS (
                    SELECT
                        COALESCE(event_data->>'status',
                                 CASE WHEN runtime_state = 'FAILED' THEN 'FAILED' ELSE 'OK' END) AS status,
                        COALESCE(event_data->>'span_type',
                                 CASE WHEN event_type = 'trace_span' THEN 'TRACE_SPAN' ELSE 'DISPATCH' END) AS span_type,
                        CASE
                            WHEN (event_data->>'duration_ms') ~ '^[0-9]+(\\.[0-9]+)?$'
                                THEN (event_data->>'duration_ms')::numeric
                            ELSE 0
                        END AS duration_ms
                    FROM ai_tutor_workflow_event
                    WHERE created_at > ?
                      AND (
                          event_type = 'trace_span'
                          OR server_event IN ('TASK_COMPLETED', 'TASK_FAILED', 'TASK_EXPIRED')
                          OR runtime_state IN ('COMPLETED', 'FAILED', 'EXPIRED')
                      )
                )
                SELECT COUNT(*) AS total_calls,
                       COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failure_count,
                       COALESCE(SUM(CASE WHEN span_type = 'DISPATCH' THEN 1 ELSE 0 END), 0) AS total_dispatches,
                       COALESCE(SUM(CASE WHEN span_type = 'MEMORY_RECALL'
                                           AND status = 'OK' THEN 1 ELSE 0 END), 0) AS memory_hits,
                       COALESCE(SUM(CASE WHEN span_type = 'MEMORY_RECALL' THEN 1 ELSE 0 END), 0) AS memory_queries,
                       COALESCE(AVG(duration_ms), 0) AS avg_latency_ms
                FROM observed_events
                """,
                since
        );

        long totalCalls = asLong(aggregate.get("total_calls"));
        long failures = asLong(aggregate.get("failure_count"));
        long dispatches = asLong(aggregate.get("total_dispatches"));
        long memoryHits = asLong(aggregate.get("memory_hits"));
        long memoryQueries = asLong(aggregate.get("memory_queries"));
        double avgLatency = asDouble(aggregate.get("avg_latency_ms"));
        double failureRate = totalCalls == 0 ? 0.0 : (double) failures / totalCalls;
        double memoryHitRate = memoryQueries == 0 ? 0.0 : (double) memoryHits / memoryQueries;

        overview.put("total_calls", totalCalls);
        overview.put("total_dispatches", dispatches);
        overview.put("avg_latency_ms", Math.round(avgLatency));
        overview.put("failure_count", failures);
        overview.put("failure_rate", round4(failureRate));
        overview.put("memory_hit_rate", round4(memoryHitRate));

        List<Map<String, Object>> byAgentRows = jdbcTemplate.queryForList(
                """
                WITH span_rows AS (
                    SELECT COALESCE(
                               event_data #>> '{metadata,agent}',
                               CASE
                                   WHEN event_data #>> '{metadata,event}' IS NULL THEN NULL
                                   ELSE 'event:' || (event_data #>> '{metadata,event}')
                               END,
                               client_event,
                               event_type
                           ) AS agent,
                           CASE
                               WHEN (event_data->>'duration_ms') ~ '^[0-9]+(\\.[0-9]+)?$'
                                   THEN (event_data->>'duration_ms')::numeric
                               ELSE 0
                           END AS duration_ms,
                           CASE
                               WHEN COALESCE(event_data->>'status', runtime_state) = 'FAILED' THEN 1
                               ELSE 0
                           END AS failed
                    FROM ai_tutor_workflow_event
                    WHERE created_at > ?
                      AND (
                          event_type = 'trace_span'
                          OR server_event IN ('TASK_COMPLETED', 'TASK_FAILED', 'TASK_EXPIRED')
                          OR runtime_state IN ('COMPLETED', 'FAILED', 'EXPIRED')
                      )
                )
                SELECT agent,
                       COUNT(*) AS calls,
                       COALESCE(SUM(duration_ms), 0) AS total_duration_ms,
                       COALESCE(SUM(failed), 0) AS failure_count
                FROM span_rows
                WHERE agent IS NOT NULL
                GROUP BY agent
                ORDER BY calls DESC
                """,
                since
        );
        List<Map<String, Object>> byAgentList = new ArrayList<>();
        for (Map<String, Object> row : byAgentRows) {
            long calls = asLong(row.get("calls"));
            double totalDuration = asDouble(row.get("total_duration_ms"));
            long failCount = asLong(row.get("failure_count"));
            Map<String, Object> agentRow = new LinkedHashMap<>();
            agentRow.put("agent", row.get("agent"));
            agentRow.put("calls", calls);
            agentRow.put("avg_latency_ms", calls == 0 ? 0L : Math.round((double) totalDuration / calls));
            agentRow.put("failure_count", failCount);
            agentRow.put("failure_rate", calls == 0 ? 0.0 : round4((double) failCount / calls));
            byAgentList.add(agentRow);
        }
        overview.put("by_agent", byAgentList);

        List<Map<String, Object>> hourlyRows = jdbcTemplate.queryForList(
                "SELECT date_trunc('hour', created_at) AS bucket, "
                        + "       COUNT(*) AS call_count "
                        + "FROM ai_tutor_workflow_event "
                        + "WHERE created_at > ? "
                        + "  AND (event_type = 'trace_span' "
                        + "       OR server_event IN ('TASK_COMPLETED', 'TASK_FAILED', 'TASK_EXPIRED') "
                        + "       OR runtime_state IN ('COMPLETED', 'FAILED', 'EXPIRED')) "
                        + "GROUP BY bucket "
                        + "ORDER BY bucket ASC",
                since
        );
        overview.put("hourly_trend", hourlyRows);

        return overview;
    }

    /**
     * 单个 trace 的时间轴：按 created_at 排列所有与 trace_id 关联的事件。
     */
    public Map<String, Object> getTraceTimeline(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        List<Map<String, Object>> rawRows = jdbcTemplate.queryForList(
                "SELECT id, event_type, event_data::text AS payload, session_id, created_at "
                        + "FROM ai_tutor_workflow_event "
                        + "WHERE trace_id = ? "
                        + "ORDER BY created_at ASC, id ASC",
                traceId
        );

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (Map<String, Object> raw : rawRows) {
            Map<String, Object> decoded = decodePayload(raw.get("payload"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("event_id", raw.get("id"));
            entry.put("event_type", raw.get("event_type"));
            entry.put("session_id", raw.get("session_id"));
            entry.put("created_at", raw.get("created_at"));
            entry.put("payload", decoded);
            timeline.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trace_id", traceId);
        result.put("event_count", timeline.size());
        result.put("entries", timeline);
        return result;
    }

    /**
     * 质量评测看板：最新一条 quality_trend_score + 趋势序列 + 失败桶分布（取最近 sample）。
     */
    public Map<String, Object> getEvaluationsDashboard(String range) {
        RangeSpec spec = resolveRange(range);
        Timestamp since = spec.sinceTimestamp();

        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT event_data::text AS payload, created_at "
                        + "FROM ai_tutor_workflow_event "
                        + "WHERE event_type = 'quality_trend_score' AND created_at > ? "
                        + "ORDER BY created_at ASC",
                since
        );

        List<Map<String, Object>> trend = new ArrayList<>();
        Map<String, Object> latest = null;
        for (Map<String, Object> row : raw) {
            Map<String, Object> decoded = decodePayload(row.get("payload"));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("created_at", row.get("created_at"));
            point.put("avg_overall_score", decoded.get("avg_overall_score"));
            point.put("sample_count", decoded.get("sample_count"));
            point.put("quality_source", "llm_judge");
            trend.add(point);
            latest = decoded;
        }
        if (trend.isEmpty()) {
            trend = buildWorkflowSuccessTrend(since);
            if (!trend.isEmpty()) {
                latest = new LinkedHashMap<>();
                Map<String, Object> last = trend.getLast();
                latest.put("avg_overall_score", last.get("avg_overall_score"));
                latest.put("sample_count", last.get("sample_count"));
                latest.put("quality_source", "workflow_success_rate");
                latest.put("quality_source_label", "工作流成功率");
            }
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("range", spec.canonical());
        dashboard.put("latest", latest == null ? Map.of() : latest);
        dashboard.put("trend", trend);

        List<Map<String, Object>> bucketRows = jdbcTemplate.queryForList(
                "SELECT failure_bucket, COUNT(*) AS fail_count "
                        + "FROM ai_tutor_workflow_event "
                        + "WHERE failure_bucket IS NOT NULL AND created_at > ? "
                        + "GROUP BY failure_bucket "
                        + "ORDER BY fail_count DESC",
                since
        );
        dashboard.put("failure_buckets", bucketRows);

        return dashboard;
    }

    private List<Map<String, Object>> buildWorkflowSuccessTrend(Timestamp since) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT date_trunc('hour', created_at) AS created_at,
                       COUNT(*) AS sample_count,
                       COALESCE(AVG(CASE
                           WHEN runtime_state = 'COMPLETED' OR server_event = 'TASK_COMPLETED' THEN 1.0
                           ELSE 0.0
                       END), 0) AS avg_overall_score
                FROM ai_tutor_workflow_event
                WHERE created_at > ?
                  AND (
                      server_event IN ('TASK_COMPLETED', 'TASK_FAILED', 'TASK_EXPIRED')
                      OR runtime_state IN ('COMPLETED', 'FAILED', 'EXPIRED')
                  )
                GROUP BY date_trunc('hour', created_at)
                ORDER BY created_at ASC
                """,
                since
        );
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("created_at", row.get("created_at"));
            point.put("avg_overall_score", round4(asDouble(row.get("avg_overall_score"))));
            point.put("sample_count", asLong(row.get("sample_count")));
            point.put("quality_source", "workflow_success_rate");
            trend.add(point);
        }
        return trend;
    }

    /**
     * 行为分析：工具使用排行 + span_type 分布 + memory_hit 有/无的对比。
     */
    public Map<String, Object> getBehaviorAnalytics(String range) {
        RangeSpec spec = resolveRange(range);
        Timestamp since = spec.sinceTimestamp();

        List<Map<String, Object>> toolUsage = jdbcTemplate.queryForList(
                "SELECT event_data->>'tool' AS tool_name, "
                        + "       COUNT(*) AS call_count, "
                        + "       AVG((event_data->>'latency_ms')::numeric) AS avg_latency_ms "
                        + "FROM ai_tutor_workflow_event "
                        + "WHERE event_type = 'tool_call' AND created_at > ? "
                        + "  AND event_data->>'tool' IS NOT NULL "
                        + "GROUP BY event_data->>'tool' "
                        + "ORDER BY call_count DESC",
                since
        );

        List<Map<String, Object>> spanTypeDist = jdbcTemplate.queryForList(
                "SELECT event_data->>'span_type' AS span_type, "
                        + "       COUNT(*) AS call_count, "
                        + "       AVG(COALESCE((event_data->>'duration_ms')::numeric, 0)) AS avg_duration_ms "
                        + "FROM ai_tutor_workflow_event "
                        + "WHERE event_type = 'trace_span' AND created_at > ? "
                        + "  AND event_data->>'span_type' IS NOT NULL "
                        + "GROUP BY event_data->>'span_type' "
                        + "ORDER BY call_count DESC",
                since
        );

        Map<String, Object> memoryCompareRow = jdbcTemplate.queryForMap(
                "SELECT "
                        + "  SUM(CASE WHEN event_data->>'span_type' = 'MEMORY_RECALL' AND event_data->>'status' = 'OK' THEN 1 ELSE 0 END) AS memory_ok, "
                        + "  SUM(CASE WHEN event_data->>'span_type' = 'MEMORY_RECALL' AND event_data->>'status' = 'FAILED' THEN 1 ELSE 0 END) AS memory_failed, "
                        + "  SUM(CASE WHEN event_data->>'span_type' = 'MEMORY_RECALL' THEN 1 ELSE 0 END) AS memory_total "
                        + "FROM ai_tutor_workflow_event "
                        + "WHERE event_type = 'trace_span' AND created_at > ?",
                since
        );

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("range", spec.canonical());
        analytics.put("tool_usage", toolUsage);
        analytics.put("span_type_distribution", spanTypeDist);
        analytics.put("memory_recall", memoryCompareRow);
        return analytics;
    }

    private Map<String, Object> decodePayload(Object rawPayload) {
        if (rawPayload == null) {
            return new LinkedHashMap<>();
        }
        String json = String.valueOf(rawPayload);
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to decode event_data JSON (len={}): {}",
                    json.length(), e.getOriginalMessage());
            return new LinkedHashMap<>();
        }
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private RangeSpec resolveRange(String range) {
        String normalized = range == null ? "" : range.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        return switch (normalized) {
            case "", "today" -> new RangeSpec("today", LocalDate.now(zone).atStartOfDay(zone).toInstant());
            case "7d" -> new RangeSpec("7d", now.minus(Duration.ofDays(7)));
            case "30d" -> new RangeSpec("30d", now.minus(Duration.ofDays(30)));
            default -> throw new IllegalArgumentException("range must be one of today|7d|30d, got: " + range);
        };
    }

    private record RangeSpec(String canonical, Instant since) {
        Timestamp sinceTimestamp() {
            return Timestamp.from(since);
        }
    }
}
