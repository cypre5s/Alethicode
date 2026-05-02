package com.alethicode.service.aitutor.assessment;

import com.alethicode.service.ai.AiModelGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeQualityAssessmentServiceTest {

    private final AiModelGateway aiModelGateway = mock(AiModelGateway.class);
    private final CodeQualityAssessmentService service = new LanguageRoutedCodeQualityAssessmentService(
            new PythonCodeQualityAssessmentService(aiModelGateway),
            new GenericCodeQualityAssessmentService(aiModelGateway)
    );

    @Test
    void shouldReturnValidatedAssessmentForPythonCode() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "readability", 4,
                "readability_comment", "变量命名比较清晰",
                "efficiency", 5,
                "efficiency_comment", "时间复杂度已经足够好",
                "style", 3,
                "style_comment", "可以再补一行空行增强可读性"
        ));

        Map<String, Object> result = service.assess("print(1)", "Python3", "输出一个数字");

        assertThat(result.get("readability")).isEqualTo(4);
        assertThat(result.get("efficiency")).isEqualTo(5);
        assertThat(result.get("style")).isEqualTo(3);
        assertThat(result.get("overall")).isEqualTo(4.0);
    }

    @Test
    void shouldFailFastWhenScoreIsOutsideSupportedRange() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "readability", 0,
                "readability_comment", "变量太短",
                "efficiency", 4,
                "efficiency_comment", "复杂度合理",
                "style", 3,
                "style_comment", "格式基本规范"
        ));

        assertThatThrownBy(() -> service.assess("print(1)", "Python3", "输出一个数字"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("readability must be in [1, 5]");
    }

    @Test
    void shouldRouteNonPythonLanguagesToGenericAssessment() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "readability", 4,
                "readability_comment", "结构还算清晰",
                "efficiency", 4,
                "efficiency_comment", "复杂度符合预期",
                "style", 5,
                "style_comment", "语言风格比较统一"
        ));

        Map<String, Object> result = service.assess("cout << 1;", "C++", "输出一个数字");

        assertThat(result.get("readability")).isEqualTo(4);
        assertThat(result.get("efficiency")).isEqualTo(4);
        assertThat(result.get("style")).isEqualTo(5);
        assertThat(result.get("overall")).isEqualTo(4.3);
    }
}
