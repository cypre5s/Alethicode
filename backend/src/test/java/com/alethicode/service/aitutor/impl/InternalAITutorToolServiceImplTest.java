package com.alethicode.service.aitutor.impl;

import com.alethicode.service.aitutor.context.ConversationContextService;
import com.alethicode.service.aitutor.context.ConversationMode;
import com.alethicode.service.aitutor.parsons.ParsonsCapabilityService;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import com.alethicode.service.aitutor.retrieval.SimilarErrorRetrievalService;
import com.alethicode.service.aitutor.visualize.VisualizeCapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAITutorToolServiceImplTest {

    private NamedParameterJdbcTemplate jdbc;
    private ConversationContextService conversationContextService;
    private InternalAITutorToolServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        conversationContextService = mock(ConversationContextService.class);
        service = new InternalAITutorToolServiceImpl(
                jdbc,
                mock(CoursewareRetrievalService.class),
                mock(SimilarErrorRetrievalService.class),
                mock(LearnerProfileProjector.class),
                mock(VisualizeCapabilityService.class),
                conversationContextService,
                mock(ParsonsCapabilityService.class)
        );
    }

    @Test
    void recordWorkflowEventStampsCompletedCardWithActiveModeAndReferences() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(conversationContextService.getActiveMode("twf_a")).thenReturn(ConversationMode.CHAT);

        service.recordWorkflowEvent(Map.of(
                "session_id", "twf_a",
                "run_id", "run_a",
                "thread_id", "thread_a",
                "runtime_state", "COMPLETED",
                "client_event", "CHAT",
                "phase", "READING",
                "node_outputs", Map.of(
                        "chat", Map.of(
                                "content", "可以，我们继续看刚才的图。",
                                "referenced_card_ids", List.of("C-K-12345678")
                        )
                )
        ));

        verify(conversationContextService).stampCardForLatestEvent(
                "twf_a",
                "run_a",
                "ai_reply",
                ConversationMode.CHAT,
                List.of("C-K-12345678")
        );
    }
}
