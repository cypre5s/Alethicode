package com.alethicode.service.languagepack.impl;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.GroundedAnswer;
import com.alethicode.service.languagepack.PageRetrievalHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerSynthesisServiceImplTest {

    @Test
    void shouldNotMarkAnswerGroundedWhenModelOmitsCitedPageNos() {
        AiModelGateway aiModelGateway = mock(AiModelGateway.class);
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "answer_markdown", "变量是编程中用于保存数据的名称。",
                "insufficient_evidence", false
        ));

        AnswerSynthesisServiceImpl service = new AnswerSynthesisServiceImpl(aiModelGateway, new ObjectMapper());

        GroundedAnswer answer = service.synthesizeAnswer("变量是什么？", List.of(variableHit()));

        assertThat(answer.grounded()).isFalse();
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void shouldBuildCitationsFromLegacyCitationPageNos() {
        AiModelGateway aiModelGateway = mock(AiModelGateway.class);
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "answer_markdown", "变量是编程中用于保存数据的名称。",
                "citations", List.of(Map.of("page_no", 2)),
                "insufficient_evidence", false
        ));
        when(aiModelGateway.readConfigOrDefault("QA_GROUNDING_CRITIC_ENABLED", "false")).thenReturn("false");

        AnswerSynthesisServiceImpl service = new AnswerSynthesisServiceImpl(aiModelGateway, new ObjectMapper());

        GroundedAnswer answer = service.synthesizeAnswer("变量是什么？", List.of(variableHit()));

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.citations().getFirst()).containsEntry("page_no", 2);
    }

    @Test
    void shouldMarkAnswerGroundedOnlyWhenCitationsCanBeBuiltFromCitedPageNos() {
        AiModelGateway aiModelGateway = mock(AiModelGateway.class);
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "answer_markdown", "变量是编程中用于保存数据的名称。",
                "cited_page_nos", List.of(2),
                "insufficient_evidence", false
        ));
        when(aiModelGateway.readConfigOrDefault("QA_GROUNDING_CRITIC_ENABLED", "false")).thenReturn("false");

        AnswerSynthesisServiceImpl service = new AnswerSynthesisServiceImpl(aiModelGateway, new ObjectMapper());

        GroundedAnswer answer = service.synthesizeAnswer("变量是什么？", List.of(variableHit()));

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.citations().getFirst())
                .containsEntry("document_id", 101L)
                .containsEntry("document_title", "intro.pdf")
                .containsEntry("page_no", 2);
    }

    private PageRetrievalHit variableHit() {
        return new PageRetrievalHit(
                1001L,
                101L,
                "intro.pdf",
                2,
                "变量",
                "变量是用来保存数据的名称。",
                "变量是用来保存数据的名称。",
                "/tmp/intro.pdf",
                0.98
        );
    }
}
