package com.alethicode.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record LanguagePackQaMessageRequest(
        @JsonProperty("content")
        @NotBlank(message = "content is required")
        String content,

        /**
         * ChatComposer 从输入文本中抽出的引用 token 列表（@page:&lt;n&gt; / @kc:&lt;id&gt; /
         * @notebook:&lt;id&gt; 等），由后端 ReferenceResolver 解析并注入 LLM context。
         * 可空：旧客户端不传此字段时退化为纯文本问答，引用 token 仅以字面字符串出现在 query。
         */
        @JsonProperty("references")
        List<String> references
) {

    public List<String> referencesOrEmpty() {
        return references == null ? List.of() : references;
    }
}
