package com.alethicode.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseNormalizerTest {

    private final AiResponseNormalizer normalizer = new AiResponseNormalizer(new ObjectMapper());

    @Test
    void normalizeJsonObjectContentShouldExtractObjectFromWrappedMiniMaxText() {
        String raw = """
                下面是提取结果，请直接使用：
                ```json
                {
                  "examples": [
                    {
                      "raw_text": "示例：print(name)"
                    }
                  ]
                }
                ```
                """;

        String normalized = normalizer.normalizeJsonObjectContent(raw);

        assertThat(normalized).isEqualTo("""
                {
                  "examples": [
                    {
                      "raw_text": "示例：print(name)"
                    }
                  ]
                }""");
    }

    @Test
    void normalizeJsonObjectContentShouldEscapeRawLineBreaksInsideJsonStrings() throws Exception {
        String raw = "{\n" +
                "  \"examples\": [\n" +
                "    {\n" +
                "      \"raw_text\": \"print('hello')\nprint('world')\",\n" +
                "      \"evidence_excerpt\": \"示例代码\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        String normalized = normalizer.normalizeJsonObjectContent(raw);

        assertThat(normalized).contains("print('hello')\\nprint('world')");
        assertThat(new ObjectMapper().readTree(normalized).path("examples").size()).isEqualTo(1);
    }

    @Test
    void normalizeJsonObjectContentShouldEscapeUnescapedDoubleQuotesInsideJsonString() throws Exception {
        String raw = "{\n" +
                "  \"units\": [\n" +
                "    {\n" +
                "      \"raw_text\": \"所谓\"差分\"，就是把函数表的复杂算式转化为差分运算\",\n" +
                "      \"source_title\": \"机械式计算工具：差分机\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        String normalized = normalizer.normalizeJsonObjectContent(raw);

        assertThat(normalized).contains("所谓\\\"差分\\\"");
        assertThat(new ObjectMapper().readTree(normalized).path("units").size()).isEqualTo(1);
    }

    @Test
    void parseJsonResultShouldSupportTopLevelOutputText() {
        String responseBody = """
                {
                  "id": "resp_1",
                  "output_text": "{\\"units\\":[{\\"raw_text\\":\\"print('hello')\\"}]}"
                }
                """;

        var parsed = normalizer.parseJsonResultFromLlmResponseBody(responseBody);

        assertThat(parsed).containsKey("units");
        assertThat(parsed.get("units")).isInstanceOf(java.util.List.class);
        assertThat((java.util.List<?>) parsed.get("units")).hasSize(1);
    }

    @Test
    void parseJsonResultShouldSupportResponsesOutputStructure() {
        String responseBody = """
                {
                  "id": "resp_2",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"units\\":[{\\"raw_text\\":\\"print('world')\\"}]}"
                        }
                      ]
                    }
                  ]
                }
                """;

        var parsed = normalizer.parseJsonResultFromLlmResponseBody(responseBody);

        assertThat(parsed).containsKey("units");
        assertThat((java.util.List<?>) parsed.get("units")).hasSize(1);
    }

    @Test
    void parseJsonResultShouldSupportDirectTopLevelBusinessPayload() {
        String responseBody = """
                {
                  "units": [
                    {
                      "raw_text": "print('hello')"
                    }
                  ]
                }
                """;

        var parsed = normalizer.parseJsonResultFromLlmResponseBody(responseBody);

        assertThat(parsed).containsKey("units");
        assertThat((java.util.List<?>) parsed.get("units")).hasSize(1);
    }

    @Test
    void parseJsonResultShouldSupportWrappedBusinessPayloadUnderData() {
        String responseBody = """
                {
                  "data": {
                    "units": [
                      {
                        "raw_text": "print('wrapped')"
                      }
                    ]
                  }
                }
                """;

        var parsed = normalizer.parseJsonResultFromLlmResponseBody(responseBody);

        assertThat(parsed).containsKey("units");
        assertThat((java.util.List<?>) parsed.get("units")).hasSize(1);
    }

    @Test
    void parseJsonResultShouldSupportProviderEnvelopeWrappedByExplanatoryText() {
        String responseBody = """
                下面是模型返回：
                {
                  "id": "resp_wrapped",
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"units\\":[{\\"raw_text\\":\\"print('wrapped-envelope')\\"}]}"
                      }
                    }
                  ]
                }
                """;

        var parsed = normalizer.parseJsonResultFromLlmResponseBody(responseBody);

        assertThat(parsed).containsKey("units");
        assertThat((java.util.List<?>) parsed.get("units")).hasSize(1);
    }

    @Test
    void parseJsonResultShouldFailFastWhenProviderReturnsErrorPayload() {
        String responseBody = """
                {
                  "error": {
                    "message": "provider exploded"
                  }
                }
                """;

        assertThatThrownBy(() -> normalizer.parseJsonResultFromLlmResponseBody(responseBody))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider exploded");
    }

    @Test
    void parseJsonResultShouldFailFastWhenNoTextPayloadExists() {
        String responseBody = """
                {
                  "id": "resp_3",
                  "usage": {"total_tokens": 123}
                }
                """;

        assertThatThrownBy(() -> normalizer.parseJsonResultFromLlmResponseBody(responseBody))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM response missing choices");
    }

    @Test
    void parseTextResultShouldExtractChoicesMessageContent() {
        String responseBody = """
                {
                  "id": "resp_text",
                  "choices": [
                    {
                      "message": {
                        "content": "已掌握：循环\\n仍存在：边界\\n下一步：再做一题"
                      }
                    }
                  ]
                }
                """;

        String parsed = normalizer.parseTextResultFromLlmResponseBody(responseBody);

        assertThat(parsed).isEqualTo("已掌握：循环\n仍存在：边界\n下一步：再做一题");
    }

    @Test
    void parseTextResultShouldFailFastWhenProviderReturnsError() {
        String responseBody = """
                {
                  "error": {
                    "message": "provider exploded"
                  }
                }
                """;

        assertThatThrownBy(() -> normalizer.parseTextResultFromLlmResponseBody(responseBody))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider exploded");
    }
}
