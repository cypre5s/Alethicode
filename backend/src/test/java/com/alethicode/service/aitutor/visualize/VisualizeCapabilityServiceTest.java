package com.alethicode.service.aitutor.visualize;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisualizeCapabilityServiceTest {

    private AiModelGateway aiModelGateway;
    private VisualizeCapabilityService service;

    @BeforeEach
    void setUp() {
        aiModelGateway = mock(AiModelGateway.class);
        ObjectMapper mapper = new ObjectMapper();
        ChartConfigValidator chartConfigValidator = new ChartConfigValidator(mapper);
        chartConfigValidator.initSchema();
        service = new VisualizeCapabilityService(
                aiModelGateway,
                new VisualizePromptCatalog(),
                new MermaidValidator(),
                chartConfigValidator,
                new SvgSanitizer(),
                mapper
        );
    }

    @Test
    void dispatchForLoopTrace_returnsValidatedMermaid() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "format", "mermaid",
                "payload", "flowchart TD\nstart([开始]) --> i0[i=0]\ni0 --> stop([结束])",
                "alt_text", "for-loop 图示"
        ));

        VisualizeResult result = service.dispatch(new VisualizeRequest(
                VisualizeIntent.FOR_LOOP_TRACE,
                "画 range(1) 的循环过程",
                Map.of("phase", "ERROR_FEEDBACK"),
                1L,
                42L,
                "twf_1",
                "Yoshino"
        ));

        assertThat(result.format()).isEqualTo("mermaid");
        assertThat(result.payload()).contains("flowchart TD");
        assertThat(result.altText()).isEqualTo("for-loop 图示");
    }

    @Test
    void dispatchComplexityCompare_acceptsChartJsonPayload() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "format", "chart",
                "payload", "{\"type\":\"line\",\"data\":{\"labels\":[\"1\",\"10\",\"100\"],\"datasets\":[{\"label\":\"O(n)\",\"data\":[1,10,100]}]}}",
                "alt_text", "复杂度对比图"
        ));

        VisualizeResult result = service.dispatch(new VisualizeRequest(
                VisualizeIntent.COMPLEXITY_COMPARE,
                "比较 O(n) 和 O(n^2)",
                Map.of(),
                2L,
                100L,
                "twf_2",
                "Kanna"
        ));

        assertThat(result.format()).isEqualTo("chart");
        assertThat(result.payload()).contains("\"type\":\"line\"");
        assertThat(result.altText()).isEqualTo("复杂度对比图");
    }

    @Test
    void dispatchDataStructureState_sanitizesSvgPayload() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "format", "svg",
                "payload", "<svg width=\"400\" height=\"200\"><rect x=\"10\" y=\"10\" width=\"50\" height=\"50\" style=\"fill:red\" onclick=\"alert(1)\"/></svg>",
                "alt_text", "列表状态图"
        ));

        VisualizeResult result = service.dispatch(new VisualizeRequest(
                VisualizeIntent.DATA_STRUCTURE_STATE,
                "画列表变化",
                Map.of(),
                3L,
                101L,
                "twf_3",
                "Nene"
        ));

        assertThat(result.format()).isEqualTo("svg");
        assertThat(result.payload()).doesNotContain("onclick=");
        assertThat(result.payload()).doesNotContain("style=");
        assertThat(result.payload()).contains("<rect");
    }

    @Test
    void failsFastWhenLlmReturnsUnknownFormat() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "format", "foo",
                "payload", "bar"
        ));

        assertThatThrownBy(() -> service.dispatch(new VisualizeRequest(
                VisualizeIntent.FLOWCHART,
                "画流程图",
                Map.of(),
                1L,
                1L,
                "twf_x",
                "AI"
        ))).isInstanceOf(VisualizeValidationException.class)
                .hasMessageContaining("unknown format");
    }
}
