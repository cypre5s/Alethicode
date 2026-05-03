package com.alethicode.controller;

import com.alethicode.service.aitutor.AiTraceService;
import com.alethicode.service.aitutor.observability.AgentObservabilityService;
import com.alethicode.service.aitutor.rlhf.PromptVariantSelector;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 契约测试：{@link AdminAiObservabilityController} 的 4 个新 API 路由 + 返回结构。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup} 以 controller 为中心，
 * 避免拉起完整 Spring 容器导致不相关的 Redis/DB 自动装配失败。
 */
class AdminAiObservabilityControllerContractTest {

    private AgentObservabilityService observabilityService;
    private AiTraceService aiTraceService;
    private RolloutPolicyService rolloutPolicyService;
    private PromptVariantSelector promptVariantSelector;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        observabilityService = mock(AgentObservabilityService.class);
        aiTraceService = mock(AiTraceService.class);
        rolloutPolicyService = mock(RolloutPolicyService.class);
        promptVariantSelector = mock(PromptVariantSelector.class);

        AdminAiObservabilityController controller = new AdminAiObservabilityController(
                aiTraceService, rolloutPolicyService, observabilityService, promptVariantSelector,
                mock(com.alethicode.service.aitutor.graph.TutorWorkflowProjectionService.class));

        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new com.alethicode.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void agentsOverviewEndpointReturnsAggregatedMetrics() throws Exception {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("range", "7d");
        overview.put("total_calls", 42L);
        overview.put("total_dispatches", 8L);
        overview.put("avg_latency_ms", 125L);
        overview.put("failure_count", 1L);
        overview.put("failure_rate", 0.0238);
        overview.put("memory_hit_rate", 0.8);
        overview.put("by_agent", List.of(Map.of(
                "agent", "diagnostics_v1",
                "calls", 5L,
                "avg_latency_ms", 90L,
                "failure_count", 0L,
                "failure_rate", 0.0
        )));
        overview.put("hourly_trend", List.of());
        when(observabilityService.getAgentsOverview(eq("7d"))).thenReturn(overview);

        mockMvc.perform(get("/api/admin/ai/agents/overview").param("range", "7d"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.range").value("7d"))
                .andExpect(jsonPath("$.data.total_calls").value(42))
                .andExpect(jsonPath("$.data.by_agent[0].agent").value("diagnostics_v1"))
                .andExpect(jsonPath("$.data.hourly_trend").isArray());
    }

    @Test
    void agentsOverviewUsesDefaultRangeWhenNotSupplied() throws Exception {
        when(observabilityService.getAgentsOverview(eq("7d"))).thenReturn(Map.of("range", "7d"));

        mockMvc.perform(get("/api/admin/ai/agents/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value("7d"));
    }

    @Test
    void traceTimelineEndpointReturnsOrderedEntries() throws Exception {
        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("trace_id", "trace-007");
        timeline.put("event_count", 2);
        timeline.put("entries", List.of(
                Map.of("event_id", 10, "event_type", "trace_span",
                        "payload", Map.of("span_type", "DISPATCH", "status", "OK")),
                Map.of("event_id", 11, "event_type", "tool_call",
                        "payload", Map.of("tool", "search_courseware"))
        ));
        when(observabilityService.getTraceTimeline(eq("trace-007"))).thenReturn(timeline);

        mockMvc.perform(get("/api/admin/ai/traces/trace-007/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.trace_id").value("trace-007"))
                .andExpect(jsonPath("$.data.event_count").value(2))
                .andExpect(jsonPath("$.data.entries[0].event_type").value("trace_span"))
                .andExpect(jsonPath("$.data.entries[0].payload.span_type").value("DISPATCH"));
    }

    @Test
    void evaluationsDashboardEndpointExposesLatestTrendAndBuckets() throws Exception {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("range", "7d");
        dashboard.put("latest", Map.of("avg_overall_score", 0.82, "sample_count", 20));
        dashboard.put("trend", List.of(
                Map.of("avg_overall_score", 0.78, "sample_count", 18),
                Map.of("avg_overall_score", 0.82, "sample_count", 20)
        ));
        dashboard.put("failure_buckets", List.of(
                Map.of("failure_bucket", "answer_leakage", "fail_count", 3L)
        ));
        when(observabilityService.getEvaluationsDashboard(any())).thenReturn(dashboard);

        mockMvc.perform(get("/api/admin/ai/evaluations/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.latest.avg_overall_score").value(0.82))
                .andExpect(jsonPath("$.data.trend[1].sample_count").value(20))
                .andExpect(jsonPath("$.data.failure_buckets[0].failure_bucket").value("answer_leakage"));
    }

    @Test
    void promptVariantsEndpointReturnsVariantRanking() throws Exception {
        when(promptVariantSelector.listVariants(eq("error_diagnosis")))
                .thenReturn(List.of(
                        Map.of("variant_id", "v1", "pulls", 10, "positive", 7, "negative", 3, "elo", 1032.0),
                        Map.of("variant_id", "v2", "pulls", 8, "positive", 3, "negative", 5, "elo", 968.0)
                ));

        mockMvc.perform(get("/api/admin/ai/prompt-variants").param("agent_key", "error_diagnosis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.agent_key").value("error_diagnosis"))
                .andExpect(jsonPath("$.data.variants[0].variant_id").value("v1"))
                .andExpect(jsonPath("$.data.variants[0].elo").value(1032.0));
    }

    @Test
    void promptVariantsEndpointRejectsUnknownAgentKeyBeforeQueryingVariants() throws Exception {
        mockMvc.perform(get("/api/admin/ai/prompt-variants").param("agent_key", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"));

        verifyNoInteractions(promptVariantSelector);
    }

    @Test
    void behaviorAnalyticsEndpointReturnsToolUsageAndMemoryCompare() throws Exception {
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("range", "7d");
        analytics.put("tool_usage", List.of(
                Map.of("tool_name", "search_courseware", "call_count", 5L, "avg_latency_ms", 22.0)
        ));
        analytics.put("span_type_distribution", List.of(
                Map.of("span_type", "LLM_CALL", "call_count", 10L, "avg_duration_ms", 90.0)
        ));
        analytics.put("memory_recall", Map.of(
                "memory_total", 8L,
                "memory_ok", 7L,
                "memory_failed", 1L
        ));
        when(observabilityService.getBehaviorAnalytics(any())).thenReturn(analytics);

        mockMvc.perform(get("/api/admin/ai/behavior-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.tool_usage[0].tool_name").value("search_courseware"))
                .andExpect(jsonPath("$.data.span_type_distribution[0].span_type").value("LLM_CALL"))
                .andExpect(jsonPath("$.data.memory_recall.memory_total").value(8));
    }
}
