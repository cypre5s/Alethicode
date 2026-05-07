package com.alethicode.service.aitutor.context;

import com.alethicode.exception.LegacyBusinessException;
import com.alethicode.service.aitutor.contract.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationContextServiceTest {

    private NamedParameterJdbcTemplate jdbc;
    private ConversationContextService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new ConversationContextService(jdbc);
    }

    @Test
    void getActiveModeFallsBackToReadingWhenSessionMissing() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(service.getActiveMode("twf_x")).isEqualTo(ConversationMode.READING);
    }

    @Test
    void getActiveModeReturnsPersistedValue() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("visualize");

        assertThat(service.getActiveMode("twf_x")).isEqualTo(ConversationMode.VISUALIZE);
    }

    @Test
    void switchModeRejectsModeNotAllowedInCurrentPhase() {
        assertThatThrownBy(() -> service.switchMode("twf_a", ConversationMode.VISUALIZE, Phase.AC_REVIEW))
                .isInstanceOf(LegacyBusinessException.class);
        assertThatThrownBy(() -> service.switchMode("twf_a", ConversationMode.IDEATE, Phase.AC_REVIEW))
                .isInstanceOf(LegacyBusinessException.class);
    }

    @Test
    void switchModeRejectsMissingArgs() {
        assertThatThrownBy(() -> service.switchMode(null, ConversationMode.CHAT, Phase.READING))
                .isInstanceOf(LegacyBusinessException.class);
        assertThatThrownBy(() -> service.switchMode("twf_x", null, Phase.READING))
                .isInstanceOf(LegacyBusinessException.class);
        assertThatThrownBy(() -> service.switchMode("twf_x", ConversationMode.CHAT, null))
                .isInstanceOf(LegacyBusinessException.class);
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void switchModeFailsWhenSessionRowMissing() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThatThrownBy(() -> service.switchMode("twf_missing", ConversationMode.CHAT, Phase.READING))
                .isInstanceOf(LegacyBusinessException.class);
    }

    @Test
    void switchModePersistsActiveMode() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        ConversationMode result = service.switchMode("twf_a", ConversationMode.CHAT, Phase.READING);

        assertThat(result).isEqualTo(ConversationMode.CHAT);
        verify(jdbc).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void listLastCardsRespectsLimitClamp() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(
                        Map.of(
                                "card_id", "C-G-001",
                                "card_type", "problem_guide",
                                "mode_when_produced", "reading",
                                "event_data_json", "{\"node_outputs\":{\"problem_guide\":{\"plain_task\":\"读两个数求和\"}}}",
                                "created_at", Timestamp.from(Instant.parse("2026-04-25T08:00:00Z"))
                        )
                ));

        List<CardSummary> cards = service.listLastCards("twf_a", 3);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).cardId()).isEqualTo("C-G-001");
        assertThat(cards.get(0).cardType()).isEqualTo("problem_guide");
        assertThat(cards.get(0).shortText()).isEqualTo("读两个数求和");
    }

    @Test
    void listLastCardsReturnsEmptyWhenSessionMissing() {
        assertThat(service.listLastCards(null, 5)).isEmpty();
        assertThat(service.listLastCards("", 5)).isEmpty();
    }

    @Test
    void resolveReferencesScopesByLatestCardOfType() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    MapSqlParameterSource params = invocation.getArgument(1);
                    String cardType = (String) params.getValue("ctype");
                    if (!"error_diagnosis".equals(cardType)) {
                        throw new EmptyResultDataAccessException(1);
                    }
                    RowMapper<CardSummary> mapper = invocation.getArgument(2);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("card_id")).thenReturn("C-E-002");
                    when(rs.getString("card_type")).thenReturn("error_diagnosis");
                    when(rs.getString("mode_when_produced")).thenReturn("error_diag");
                    when(rs.getString("event_data_json"))
                            .thenReturn("{\"node_outputs\":{\"error_diagnosis\":{\"root_cause\":\"range 边界差一\"}}}");
                    when(rs.getTimestamp("created_at"))
                            .thenReturn(Timestamp.from(Instant.parse("2026-04-25T09:00:00Z")));
                    return mapper.mapRow(rs, 0);
                });

        List<CardSummary> resolved = service.resolveReferences("twf_a", List.of("@last_error", "@last_unknown"));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).cardId()).isEqualTo("C-E-002");
        assertThat(resolved.get(0).shortText()).isEqualTo("range 边界差一");
    }

    @Test
    void resolveLastReferenceCanUseLegacyEventWithoutStampedCardId() throws Exception {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<CardSummary> mapper = invocation.getArgument(2);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("card_id")).thenReturn(null);
                    when(rs.getString("card_type")).thenReturn("knowledge_review");
                    when(rs.getString("mode_when_produced")).thenReturn(null);
                    when(rs.getString("event_data_json"))
                            .thenReturn("{\"node_outputs\":{\"knowledge_review\":{\"reply\":\"循环变量每轮都会更新。\"}}}");
                    when(rs.getTimestamp("created_at"))
                            .thenReturn(Timestamp.from(Instant.parse("2026-04-25T09:00:00Z")));
                    return mapper.mapRow(rs, 0);
                });

        List<CardSummary> resolved = service.resolveReferences("twf_a", List.of("@last_review"));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).cardId()).isNull();
        assertThat(resolved.get(0).shortText()).isEqualTo("循环变量每轮都会更新。");
    }

    @Test
    void knowledgeReviewSummaryPrefersReviewContentOverRawJson() throws Exception {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<CardSummary> mapper = invocation.getArgument(2);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("card_id")).thenReturn(null);
                    when(rs.getString("card_type")).thenReturn("knowledge_review");
                    when(rs.getString("mode_when_produced")).thenReturn(null);
                    when(rs.getString("event_data_json"))
                            .thenReturn("{\"node_outputs\":{\"knowledge_review\":{\"related_kcs\":[\"程序运行原理\"],\"review_content\":\"先看变量如何随循环更新。\"}}}");
                    when(rs.getTimestamp("created_at"))
                            .thenReturn(Timestamp.from(Instant.parse("2026-04-25T09:00:00Z")));
                    return mapper.mapRow(rs, 0);
                });

        List<CardSummary> resolved = service.resolveReferences("twf_a", List.of("@last_review"));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).shortText()).isEqualTo("先看变量如何随循环更新。");
    }

    @Test
    void resolveReferencesSkipsUnknownAndCrossSessionRefs() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        List<CardSummary> resolved = service.resolveReferences(
                "twf_a",
                java.util.Arrays.asList("@card:C-X-NOTFOUND", "@last_error", null, "@card:")
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveReferencesEmptyInputReturnsEmpty() {
        assertThat(service.resolveReferences("twf_a", null)).isEmpty();
        assertThat(service.resolveReferences("twf_a", List.of())).isEmpty();
        assertThat(service.resolveReferences(null, List.of("@last_error"))).isEmpty();
    }

    @Test
    void stampCardForLatestEventAssignsTypedCardId() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        String cardId = service.stampCardForLatestEvent(
                "twf_a", "run_a", "ideate_analysis",
                ConversationMode.IDEATE, List.of("C-G-001")
        );

        assertThat(cardId).startsWith("C-I-");
        assertThat(cardId.length()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void stampCardForLatestEventReturnsNullWhenNoRowMatched() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThat(service.stampCardForLatestEvent(
                "twf_a", "run_a", "ideate_analysis",
                ConversationMode.IDEATE, List.of()
        )).isNull();
    }
}
