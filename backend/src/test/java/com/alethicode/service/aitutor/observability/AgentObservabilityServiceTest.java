package com.alethicode.service.aitutor.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentObservabilityServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private AgentObservabilityService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        service = new AgentObservabilityService(jdbcTemplate, objectMapper);
    }

    @Test
    void getAgentsOverviewAggregatesSpanMetrics() {
        when(jdbcTemplate.queryForMap(ArgumentMatchers.contains("COUNT(*) AS total_calls"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(Map.of(
                        "total_calls", 4L,
                        "failure_count", 1L,
                        "total_dispatches", 1L,
                        "memory_hits", 1L,
                        "memory_queries", 1L,
                        "avg_latency_ms", 61.25
                ));
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("GROUP BY agent"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("agent", "diagnostics_v1", "calls", 2L, "total_duration_ms", 110L, "failure_count", 1L),
                        Map.of("agent", "Dispatcher", "calls", 1L, "total_duration_ms", 120L, "failure_count", 0L),
                        Map.of("agent", "LearnerMemoryService", "calls", 1L, "total_duration_ms", 15L, "failure_count", 0L)
                ));
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("date_trunc('hour', created_at)"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("bucket", "2026-04-17T10:00:00Z", "call_count", 2L),
                        Map.of("bucket", "2026-04-17T11:00:00Z", "call_count", 2L)
                ));
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("event_data::text AS payload")))
                .thenReturn(List.of(
                        row("{\"span_type\":\"DISPATCH\",\"status\":\"OK\",\"duration_ms\":120,\"metadata\":{\"agent\":\"Dispatcher\"}}"),
                        row("{\"span_type\":\"LLM_CALL\",\"status\":\"OK\",\"duration_ms\":80,\"metadata\":{\"agent\":\"diagnostics_v1\"}}"),
                        row("{\"span_type\":\"GUARDRAIL\",\"status\":\"FAILED\",\"duration_ms\":30,\"metadata\":{\"agent\":\"diagnostics_v1\"}}"),
                        row("{\"span_type\":\"MEMORY_RECALL\",\"status\":\"OK\",\"duration_ms\":15,\"metadata\":{\"agent\":\"LearnerMemoryService\"}}")
                ));

        Map<String, Object> overview = service.getAgentsOverview("today");

        assertThat(overview.get("range")).isEqualTo("today");
        assertThat(overview.get("total_calls")).isEqualTo(4L);
        assertThat(overview.get("total_dispatches")).isEqualTo(1L);
        assertThat(overview.get("avg_latency_ms")).isEqualTo(61L);
        assertThat(overview.get("failure_count")).isEqualTo(1L);
        assertThat(overview.get("failure_rate")).isEqualTo(0.25);
        assertThat(overview.get("memory_hit_rate")).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byAgent = (List<Map<String, Object>>) overview.get("by_agent");
        assertThat(byAgent).hasSize(3);
        assertThat(byAgent.get(0).get("agent")).isEqualTo("diagnostics_v1");
        assertThat(byAgent.get(0).get("calls")).isEqualTo(2L);
        assertThat(byAgent.get(0).get("failure_count")).isEqualTo(1L);
        assertThat(byAgent.get(0).get("failure_rate")).isEqualTo(0.5);

        assertThat(overview.get("hourly_trend")).isInstanceOf(List.class);
        verify(jdbcTemplate, never()).queryForList(ArgumentMatchers.contains("event_data::text AS payload"));
        verify(jdbcTemplate).queryForMap(ArgumentMatchers.contains("FROM ai_tutor_workflow_event"),
                ArgumentMatchers.any(Timestamp.class));
    }

    @Test
    void getAgentsOverviewRejectsUnknownRange() {
        assertThatThrownBy(() -> service.getAgentsOverview("weird"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTraceTimelineOrdersEventsAndDecodesPayload() {
        when(jdbcTemplate.queryForList(anyString(), ArgumentMatchers.eq("trace-42")))
                .thenReturn(List.of(
                        Map.of(
                                "id", 10L,
                                "event_type", "trace_span",
                                "payload", "{\"span_type\":\"DISPATCH\",\"status\":\"OK\",\"duration_ms\":5}",
                                "session_id", "sess-1",
                                "created_at", "2026-04-17T10:00:00Z"
                        ),
                        Map.of(
                                "id", 11L,
                                "event_type", "tool_call",
                                "payload", "{\"tool\":\"get_learner_history\"}",
                                "session_id", "sess-1",
                                "created_at", "2026-04-17T10:00:01Z"
                        )
                ));

        Map<String, Object> timeline = service.getTraceTimeline("trace-42");

        assertThat(timeline.get("trace_id")).isEqualTo("trace-42");
        assertThat(timeline.get("event_count")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) timeline.get("entries");
        assertThat(entries).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstPayload = (Map<String, Object>) entries.get(0).get("payload");
        assertThat(firstPayload).containsEntry("span_type", "DISPATCH");
    }

    @Test
    void getTraceTimelineRejectsBlankTraceId() {
        assertThatThrownBy(() -> service.getTraceTimeline(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getEvaluationsDashboardReturnsLatestAndFailureBuckets() {
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("event_type = 'quality_trend_score'"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        row("{\"avg_overall_score\":0.72,\"sample_count\":20}"),
                        row("{\"avg_overall_score\":0.78,\"sample_count\":22}")
                ));
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("failure_bucket IS NOT NULL"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("failure_bucket", "answer_leakage", "fail_count", 3L),
                        Map.of("failure_bucket", "pedagogy_mismatch", "fail_count", 1L)
                ));

        Map<String, Object> dashboard = service.getEvaluationsDashboard("7d");

        assertThat(dashboard.get("range")).isEqualTo("7d");
        @SuppressWarnings("unchecked")
        Map<String, Object> latest = (Map<String, Object>) dashboard.get("latest");
        assertThat(latest).containsEntry("avg_overall_score", 0.78);
        assertThat(((List<?>) dashboard.get("trend"))).hasSize(2);
        assertThat(((List<?>) dashboard.get("failure_buckets"))).hasSize(2);
        verify(jdbcTemplate, atLeastOnce()).queryForList(ArgumentMatchers.contains("FROM ai_tutor_workflow_event"),
                ArgumentMatchers.any(Timestamp.class));
    }

    @Test
    void getEvaluationsDashboardFallsBackToWorkflowSuccessRateWhenNoJudgeScores() {
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("event_type = 'quality_trend_score'"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("GROUP BY date_trunc('hour', created_at)"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("created_at", "2026-04-17T10:00:00Z", "sample_count", 4L, "avg_overall_score", 0.75),
                        Map.of("created_at", "2026-04-17T11:00:00Z", "sample_count", 2L, "avg_overall_score", 0.5)
                ));
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("failure_bucket IS NOT NULL"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of());

        Map<String, Object> dashboard = service.getEvaluationsDashboard("7d");

        @SuppressWarnings("unchecked")
        Map<String, Object> latest = (Map<String, Object>) dashboard.get("latest");
        assertThat(latest).containsEntry("avg_overall_score", 0.5);
        assertThat(latest).containsEntry("sample_count", 2L);
        assertThat(latest).containsEntry("quality_source", "workflow_success_rate");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) dashboard.get("trend");
        assertThat(trend).hasSize(2);
        assertThat(trend.get(0)).containsEntry("quality_source", "workflow_success_rate");
    }

    @Test
    void getBehaviorAnalyticsAggregatesToolUsageAndSpanDistribution() {
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("event_type = 'tool_call'"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("tool_name", "get_learner_history", "call_count", 10L, "avg_latency_ms", 40.0),
                        Map.of("tool_name", "search_courseware", "call_count", 3L, "avg_latency_ms", 12.0)
                ));
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("event_type = 'trace_span' AND created_at"),
                ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("span_type", "DISPATCH", "call_count", 5L, "avg_duration_ms", 110.0),
                        Map.of("span_type", "LLM_CALL", "call_count", 5L, "avg_duration_ms", 80.0)
                ));
        when(jdbcTemplate.queryForMap(anyString(), ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(Map.of(
                        "memory_ok", 7L,
                        "memory_failed", 1L,
                        "memory_total", 8L
                ));

        Map<String, Object> analytics = service.getBehaviorAnalytics("7d");

        assertThat(analytics.get("range")).isEqualTo("7d");
        assertThat(((List<?>) analytics.get("tool_usage"))).hasSize(2);
        assertThat(((List<?>) analytics.get("span_type_distribution"))).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> memoryBlock = (Map<String, Object>) analytics.get("memory_recall");
        assertThat(memoryBlock).containsEntry("memory_total", 8L);
    }

    private static Map<String, Object> row(String payload) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("payload", payload);
        map.put("created_at", "2026-04-17T10:00:00Z");
        return map;
    }
}
