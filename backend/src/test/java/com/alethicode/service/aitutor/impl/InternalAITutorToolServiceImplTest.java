package com.alethicode.service.aitutor.impl;

import com.alethicode.service.aitutor.SessionUsage;
import com.alethicode.service.aitutor.context.ConversationContextService;
import com.alethicode.service.aitutor.context.ConversationMode;
import com.alethicode.service.aitutor.context.CoursewareContextProvider;
import com.alethicode.service.aitutor.context.KcContextProvider;
import com.alethicode.service.aitutor.context.KcSummary;
import com.alethicode.service.aitutor.context.NotebookContextProvider;
import com.alethicode.service.aitutor.context.NotebookSummary;
import com.alethicode.service.aitutor.context.PageContextProvider;
import com.alethicode.service.aitutor.context.PageSummary;
import com.alethicode.service.aitutor.parsons.ParsonsCapabilityService;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.retrieval.CoursewareRetrievalService;
import com.alethicode.service.aitutor.retrieval.SimilarErrorRetrievalService;
import com.alethicode.service.aitutor.visualize.VisualizeCapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAITutorToolServiceImplTest {

    private NamedParameterJdbcTemplate jdbc;
    private ConversationContextService conversationContextService;
    private PageContextProvider pageContextProvider;
    private KcContextProvider kcContextProvider;
    private NotebookContextProvider notebookContextProvider;
    private InternalAITutorToolServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        conversationContextService = mock(ConversationContextService.class);
        pageContextProvider = mock(PageContextProvider.class);
        kcContextProvider = mock(KcContextProvider.class);
        notebookContextProvider = mock(NotebookContextProvider.class);
        service = new InternalAITutorToolServiceImpl(
                jdbc,
                mock(CoursewareRetrievalService.class),
                mock(SimilarErrorRetrievalService.class),
                mock(LearnerProfileProjector.class),
                mock(VisualizeCapabilityService.class),
                conversationContextService,
                mock(CoursewareContextProvider.class),
                pageContextProvider,
                kcContextProvider,
                notebookContextProvider,
                mock(ParsonsCapabilityService.class)
        );
    }

    @Test
    void resolveReferencesIncludesPageKcAndNotebookContexts() {
        List<String> refs = List.of("@page:42:7", "@kc:123", "@notebook:N-001");
        when(conversationContextService.resolveReferences("twf_ctx", refs)).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("alice");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(9L);
        when(pageContextProvider.resolvePageReferences("alice", null, refs)).thenReturn(List.of(
                new PageSummary(42L, "Python 入门", 5L, "第一章", 7, "变量", Instant.parse("2026-05-06T00:00:00Z"))
        ));
        when(kcContextProvider.resolveKcReferences(9L, refs)).thenReturn(List.of(
                new KcSummary("123", "变量", "变量基础", List.of("第一章"), 0.75, Instant.parse("2026-05-06T00:00:00Z"))
        ));
        when(notebookContextProvider.resolveNotebookReferences(9L, refs)).thenReturn(List.of(
                new NotebookSummary("N-001", "边界错误", "注意 off-by-one", Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-05-06T00:00:00Z"))
        ));

        Map<String, Object> result = service.resolveReferences("twf_ctx", refs, "解释这些引用");

        assertThat((List<?>) result.get("pages")).hasSize(1);
        assertThat((List<?>) result.get("kcs")).hasSize(1);
        assertThat((List<?>) result.get("notebooks")).hasSize(1);
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

    @Test
    void getSessionUsageReturnsCurrentCounters() {
        Instant updatedAt = Instant.parse("2026-05-06T12:34:56Z");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "tokens_used", 1234L,
                        "tokens_limit", 8000L,
                        "model_name", "deepseek-chat",
                        "updated_at", Timestamp.from(updatedAt)
                )));

        SessionUsage usage = service.getSessionUsage("twf_x");

        assertThat(usage.tokensUsed()).isEqualTo(1234L);
        assertThat(usage.tokensLimit()).isEqualTo(8000L);
        assertThat(usage.modelName()).isEqualTo("deepseek-chat");
        assertThat(usage.lastUpdated()).isEqualTo(updatedAt);
        assertThat(usage.toMap())
                .containsEntry("tokens_used", 1234L)
                .containsEntry("tokens_limit", 8000L)
                .containsEntry("model_name", "deepseek-chat");
    }

    @Test
    void getSessionUsageDefaultsZeroWhenColumnsMissing() {
        // schema 预留期：三列默认值都是 0 / ""，service 不应抛错。
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("tokens_used", 0L);
        row.put("tokens_limit", 0L);
        row.put("model_name", "");
        row.put("updated_at", null);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(row));

        SessionUsage usage = service.getSessionUsage("twf_y");

        assertThat(usage.tokensUsed()).isZero();
        assertThat(usage.tokensLimit()).isZero();
        assertThat(usage.modelName()).isEmpty();
        assertThat(usage.lastUpdated()).isNull();
    }

    @Test
    void getSessionUsageThrowsWhenSessionMissing() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getSessionUsage("twf_missing"))
                .isInstanceOf(InternalAITutorToolServiceImpl.ProblemNotFoundException.class)
                .hasMessageContaining("twf_missing");
    }

    @Test
    void getSessionUsageRejectsBlankSessionId() {
        assertThatThrownBy(() -> service.getSessionUsage(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getSessionUsage(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
