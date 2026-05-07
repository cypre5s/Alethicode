package com.alethicode.service.languagepack.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BusinessException;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.SessionUsage;
import com.alethicode.service.languagepack.AnswerSynthesisService;
import com.alethicode.service.languagepack.ConversationContextService;
import com.alethicode.service.languagepack.PageRetrievalService;
import com.alethicode.service.languagepack.VideoJobService;
import com.alethicode.websocket.WorkflowRealtimeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 1 chat composer plan 1.7 节单测：验证 LanguagePackQaServiceImpl.getSessionUsage
 * 在「鉴权通过 / 行存在 / 字段缺省」三种场景下的行为。
 *
 * <p>不启动 SpringContext，纯 Mockito mock 全部依赖；其它 LP QA 行为由
 * {@code LanguagePackQaIntegrationTest} 覆盖。</p>
 */
class LanguagePackQaServiceImplUsageTest {

    private JdbcTemplate jdbcTemplate;
    private LanguagePackQaServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        AlethicodeProperties properties = new AlethicodeProperties();
        service = new LanguagePackQaServiceImpl(
                jdbcTemplate,
                mock(PageRetrievalService.class),
                mock(AnswerSynthesisService.class),
                mock(ConversationContextService.class),
                new ObjectMapper(),
                properties,
                mock(VideoJobService.class),
                mock(WorkflowRealtimeSupport.class),
                mock(AiModelGateway.class),
                mock(com.alethicode.service.aitutor.context.PageContextProvider.class),
                mock(com.alethicode.service.aitutor.context.KcContextProvider.class),
                mock(com.alethicode.service.aitutor.context.NotebookContextProvider.class)
        );
    }

    @Test
    void getSessionUsageReturnsCountersForOwnedSession() {
        stubOwnedSession("alice", 11L, 11L);
        Instant updatedAt = Instant.parse("2026-05-06T12:34:56Z");
        when(jdbcTemplate.queryForMap(
                argThat(sql -> sql != null && sql.contains("FROM language_pack_chat_session")),
                eq(11L)
        )).thenReturn(Map.of(
                "tokens_used", 512L,
                "tokens_limit", 4096L,
                "model_name", "deepseek-chat",
                "update_time", Timestamp.from(updatedAt)
        ));

        SessionUsage usage = service.getSessionUsage("alice", 11L);

        assertThat(usage.tokensUsed()).isEqualTo(512L);
        assertThat(usage.tokensLimit()).isEqualTo(4096L);
        assertThat(usage.modelName()).isEqualTo("deepseek-chat");
        assertThat(usage.lastUpdated()).isEqualTo(updatedAt);
    }

    @Test
    void getSessionUsageDefaultsZeroWhenColumnsAreSchemaPlaceholder() {
        stubOwnedSession("bob", 22L, 22L);
        java.util.HashMap<String, Object> row = new java.util.HashMap<>();
        row.put("tokens_used", 0L);
        row.put("tokens_limit", 0L);
        row.put("model_name", "");
        row.put("update_time", null);
        when(jdbcTemplate.queryForMap(anyString(), eq(22L))).thenReturn(row);

        SessionUsage usage = service.getSessionUsage("bob", 22L);

        assertThat(usage.tokensUsed()).isZero();
        assertThat(usage.tokensLimit()).isZero();
        assertThat(usage.modelName()).isEmpty();
        assertThat(usage.lastUpdated()).isNull();
    }

    @Test
    void getSessionUsageRejectsForeignSession() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString()))
                .thenReturn(9L);
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("FROM language_pack_chat_session")),
                any(ResultSetExtractor.class),
                anyLong(), anyLong()
        )).thenReturn(null);

        assertThatThrownBy(() -> service.getSessionUsage("eve", 33L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("QA session not found");
    }

    private void stubOwnedSession(String username, long userId, long sessionId) {
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("FROM \"user\" WHERE username")),
                eq(Long.class),
                eq(username)
        )).thenReturn(userId);
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("FROM language_pack_chat_session")),
                any(ResultSetExtractor.class),
                eq(sessionId), eq(userId)
        )).thenReturn(sessionId);
    }
}
