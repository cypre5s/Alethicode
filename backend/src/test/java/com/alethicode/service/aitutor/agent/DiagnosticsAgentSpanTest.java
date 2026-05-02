package com.alethicode.service.aitutor.agent;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.observability.AgentTraceContext;
import com.alethicode.service.aitutor.observability.AgentTraceRecorder;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 DiagnosticsAgent 在 traceContext 注入时会产生 LLM_CALL + GUARDRAIL span，
 * 并且即便 JdbcTemplate 写 span 失败主链路仍然能返回诊断结果（failfast 只针对主链路，span 记录失败仅 warn）。
 */
class DiagnosticsAgentSpanTest {

    private AiModelGateway aiModelGateway;
    private ReflectionService reflectionService;
    private JdbcTemplate spanJdbcTemplate;
    private AgentTraceRecorder traceRecorder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        aiModelGateway = mock(AiModelGateway.class);
        reflectionService = mock(ReflectionService.class);
        spanJdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        traceRecorder = new AgentTraceRecorder(spanJdbcTemplate, objectMapper);

        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("root_cause", "循环边界错误");
        diagnosis.put("tool_calls", List.of(Map.of("tool_name", "get_learner_history", "result_summary", "ok")));
        when(aiModelGateway.callForJson(anyString(), anyString(), any())).thenReturn(diagnosis);
        when(reflectionService.reflectAndRefine(any(CardType.class), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ReflectionResult(diagnosis, true, 0, "ok"));
    }

    @Test
    void singleShotExecutionEmitsLlmCallAndGuardrailSpans() throws Exception {
        DiagnosticsAgent agent = new DiagnosticsAgent(aiModelGateway, reflectionService);

        AgentTraceContext traceContext = new AgentTraceContext(
                traceRecorder, "trace-sst-1", "sess-sst", "parent-sst");

        AgentContext context = new AgentContext(
                "ERROR_FEEDBACK",
                "ERROR_FEEDBACK",
                Map.of(),
                null,
                null,
                null,
                "题目上下文",
                new LinkedHashMap<>(),
                10L,
                20L,
                List.of(),
                traceContext
        );

        Map<String, Object> output = agent.execute(context);
        assertThat(output).containsKey("root_cause");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(spanJdbcTemplate, times(2)).update(
                anyString(), any(Object.class), jsonCaptor.capture(), any(Object.class)
        );

        List<String> captured = jsonCaptor.getAllValues();
        assertThat(captured).hasSize(2);

        Map<String, Object> first = objectMapper.readValue(captured.get(0), Map.class);
        Map<String, Object> second = objectMapper.readValue(captured.get(1), Map.class);

        assertThat(first.get("span_type")).isEqualTo("LLM_CALL");
        assertThat(first.get("trace_id")).isEqualTo("trace-sst-1");
        assertThat(first.get("parent_span_id")).isEqualTo("parent-sst");
        assertThat(first.get("status")).isEqualTo("OK");

        assertThat(second.get("span_type")).isEqualTo("GUARDRAIL");
        assertThat(second.get("trace_id")).isEqualTo("trace-sst-1");
        assertThat(second.get("status")).isEqualTo("OK");
    }

    @Test
    void withoutTraceContextNoSpanIsRecorded() {
        DiagnosticsAgent agent = new DiagnosticsAgent(aiModelGateway, reflectionService);

        AgentContext context = new AgentContext(
                "ERROR_FEEDBACK",
                "ERROR_FEEDBACK",
                Map.of(),
                null,
                null,
                null,
                "题目上下文",
                new LinkedHashMap<>(),
                10L,
                20L
        );

        Map<String, Object> output = agent.execute(context);
        assertThat(output).containsKey("root_cause");

        verify(spanJdbcTemplate, org.mockito.Mockito.never())
                .update(anyString(), any(Object.class), any(Object.class), any(Object.class));
    }

    @Test
    void guardrailFailureRefinedMarksSpanAsFailed() throws Exception {
        Map<String, Object> refined = new LinkedHashMap<>(Map.of("root_cause", "refined cause"));
        when(reflectionService.reflectAndRefine(any(CardType.class), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ReflectionResult(refined, false, 1, "critic rejected"));

        DiagnosticsAgent agent = new DiagnosticsAgent(aiModelGateway, reflectionService);

        AgentTraceContext traceContext = new AgentTraceContext(
                traceRecorder, "trace-fail-1", "sess-fail", null);

        AgentContext context = new AgentContext(
                "ERROR_FEEDBACK",
                "ERROR_FEEDBACK",
                Map.of(),
                null,
                null,
                null,
                "题目上下文",
                new LinkedHashMap<>(),
                10L,
                20L,
                List.of(),
                traceContext
        );

        Map<String, Object> output = agent.execute(context);
        assertThat(output).containsEntry("root_cause", "refined cause");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(spanJdbcTemplate, times(2)).update(
                anyString(), any(Object.class), jsonCaptor.capture(), any(Object.class)
        );
        Map<String, Object> guardrailSpan = objectMapper.readValue(jsonCaptor.getAllValues().get(1), Map.class);
        assertThat(guardrailSpan.get("span_type")).isEqualTo("GUARDRAIL");
        assertThat(guardrailSpan.get("status")).isEqualTo("FAILED");
    }
}
