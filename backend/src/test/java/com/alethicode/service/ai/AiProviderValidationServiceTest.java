package com.alethicode.service.ai;

import com.alethicode.dto.request.AiProviderValidationRunRequest;
import com.alethicode.dto.response.AiProviderValidationCaseResult;
import com.alethicode.dto.response.AiProviderValidationRunResponse;
import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import com.alethicode.service.aitutor.react.ToolTraceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiProviderValidationServiceTest {

    private SpringAiModelGateway gateway;
    private SpringAiToolLoopService toolLoop;
    private AiModelProfileResolver profileResolver;
    private AiProviderValidationService service;

    @BeforeEach
    void setUp() {
        gateway = mock(SpringAiModelGateway.class);
        toolLoop = mock(SpringAiToolLoopService.class);
        profileResolver = mock(AiModelProfileResolver.class);
        when(profileResolver.resolveChat(any()))
                .thenReturn(new AiModelProfile("", "k", "https://e", "m", 30, 2));
        service = new AiProviderValidationService(gateway, toolLoop, profileResolver);
    }

    @Test
    void shouldFailFastWhenAllIncludeFlagsFalse() {
        AiProviderValidationRunRequest request = new AiProviderValidationRunRequest(
                null, false, false, false, false);

        assertThatThrownBy(() -> service.createValidationRun(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one validation case");
    }

    @Test
    void shouldFailFastWhenRequestIsNull() {
        assertThatThrownBy(() -> service.createValidationRun(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Validation request is required");
    }

    @Test
    void jsonCaseShouldPassWhenAllRequiredKeysPresent() {
        when(gateway.callForJson(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", "ok", "steps", List.of("read"), "score", 1));

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, true, false, false, false));

        assertThat(resp.cases()).hasSize(1);
        AiProviderValidationCaseResult c = resp.cases().getFirst();
        assertThat(c.caseName()).isEqualTo("json");
        assertThat(c.passed()).isTrue();
        assertThat(c.shapeMatched()).isTrue();
        assertThat(c.failureMessage()).isNull();
        assertThat(resp.passed()).isTrue();
    }

    @Test
    void jsonCaseShouldFailWhenRequiredKeyMissing() {
        when(gateway.callForJson(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", "ok"));

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, true, false, false, false));

        AiProviderValidationCaseResult c = resp.cases().getFirst();
        assertThat(c.shapeMatched()).isFalse();
        assertThat(c.failureMessage()).contains("missing_keys");
    }

    @Test
    void jsonCaseShouldReportFailureOnGatewayException() {
        when(gateway.callForJson(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("provider exploded"));

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, true, false, false, false));

        AiProviderValidationCaseResult c = resp.cases().getFirst();
        assertThat(c.passed()).isFalse();
        assertThat(c.shapeMatched()).isFalse();
        assertThat(c.failureMessage()).contains("provider exploded");
        assertThat(resp.passed()).isFalse();
    }

    @Test
    void contentCaseShouldPassForNonEmptyResponse() {
        String reply = "AI 正在改变教育";
        when(gateway.callForContent(anyString())).thenReturn(reply);

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, false, true, false, false));

        assertThat(resp.cases().getFirst().shapeMatched()).isTrue();
        assertThat(resp.cases().getFirst().summary().get("length")).isEqualTo(reply.length());
    }

    // Phase 3 切流：embedding case 已删除 — embedding 链路迁到 alethicode-rag 微服务，
    // 它有自己的 /metrics + /health 端点，不再由 SpringAiModelGateway 验证。

    @Test
    void embeddingFlagOnRequestIsIgnoredButRunStillSucceeds() {
        when(gateway.callForJson(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", "ok", "steps", List.of("x"), "score", 1));

        // includeEmbedding=true 仍然合法（向后兼容前端管理后台），但运行期被忽略。
        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, true, false, true, false));

        assertThat(resp.passed()).isTrue();
        assertThat(resp.summary().get("totalCases")).isEqualTo(1);
    }

    @Test
    void toolLoopCaseShouldPassWhenToolSeenAndTraceNonEmpty() {
        ReactResult result = new ReactResult(
                Map.of("tool_seen", true),
                2,
                List.of(),
                List.of(new ToolTraceEntry(1, "validation_echo", Map.of("message", "hello"),
                        true, "", 5L, "{\"echo\":\"hello\"}", ""))
        );
        when(toolLoop.execute(anyString(), any(), any(), any(), anyInt(),
                any(ToolContext.class), any(StoppingCondition.class),
                any(AiModelProfile.class), eq("required"))).thenReturn(result);
        // Invocation with null toolContext also possible:
        when(toolLoop.execute(anyString(), any(), any(), any(), anyInt(),
                (ToolContext) org.mockito.ArgumentMatchers.isNull(),
                any(StoppingCondition.class),
                any(AiModelProfile.class), eq("required"))).thenReturn(result);

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, false, false, false, true));

        AiProviderValidationCaseResult c = resp.cases().getFirst();
        assertThat(c.shapeMatched()).isTrue();
        assertThat(c.summary()).containsEntry("traceCount", 1);
    }

    @Test
    void toolLoopCaseShouldFailWhenToolNeverCalled() {
        ReactResult result = new ReactResult(
                Map.of("tool_seen", true),
                1,
                List.of(),
                List.of()
        );
        when(toolLoop.execute(anyString(), any(), any(), any(), anyInt(),
                (ToolContext) org.mockito.ArgumentMatchers.isNull(),
                any(StoppingCondition.class),
                any(AiModelProfile.class), eq("required"))).thenReturn(result);

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, false, false, false, true));

        AiProviderValidationCaseResult c = resp.cases().getFirst();
        assertThat(c.shapeMatched()).isFalse();
        assertThat(c.failureMessage()).contains("no trace entries");
    }

    @Test
    void responseShouldNotExposePromptsOrCompletions() {
        when(gateway.callForJson(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", "ok", "steps", List.of("x"), "score", 1));
        when(gateway.callForContent(anyString())).thenReturn("短文本回复");

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, true, true, false, false));

        // Walk every summary and failure message; none should include the actual prompt.
        String forbidden = "Return exactly";
        for (AiProviderValidationCaseResult c : resp.cases()) {
            assertThat(c.failureMessage() == null || !c.failureMessage().contains(forbidden)).isTrue();
            assertThat(c.summary().toString()).doesNotContain(forbidden);
        }
    }

    @Test
    void runShouldAggregateOverallPassedFlag() {
        when(gateway.callForJson(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", "ok", "steps", List.of("x"), "score", 1));
        when(gateway.callForContent(anyString())).thenReturn("ok");

        AiProviderValidationRunResponse resp = service.createValidationRun(
                new AiProviderValidationRunRequest(null, true, true, false, false));

        assertThat(resp.passed()).isTrue();
        assertThat(resp.summary().get("totalCases")).isEqualTo(2);
        assertThat(resp.summary().get("passedCases")).isEqualTo(2L);
    }
}
