package com.alethicode.service.aitutor.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TutorWorkflowProjectionServiceTest {

    private NamedParameterJdbcTemplate jdbc;
    private TutorWorkflowProjectionService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new TutorWorkflowProjectionService(jdbc);
    }

    @Test
    void getSession_shouldExposeFailureBucketAndLastErrorFromProjection() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("session_id", "twf_failure");
        row.put("thread_id", "thread_1");
        row.put("user_id", 7L);
        row.put("problem_id", 479L);
        row.put("language", "Python3");
        row.put("phase", "IDEATING");
        row.put("runtime_state", "FAILED");
        row.put("pending_human_action", "");
        row.put("node_outputs", "{\"last_event\":{\"event\":\"IDEATING\"}}");
        row.put("behavior_metrics", "{}");
        row.put("available_actions", "[]");
        row.put("plan", "{}");
        row.put("recommendation_reason", "");
        row.put("failure_bucket", "SYSTEM_ERROR");
        row.put("last_error", "LLM generation failed");
        row.put("last_checkpoint_id", null);
        row.put("last_run_id", "run_failure");
        row.put("is_active", true);
        row.put("created_at", "2026-04-26T00:00:00Z");
        row.put("updated_at", "2026-04-26T00:00:01Z");
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(row));

        Optional<Map<String, Object>> session = service.getSession("twf_failure");

        assertThat(session).isPresent();
        assertThat(session.get())
                .containsEntry("failure_bucket", "SYSTEM_ERROR")
                .containsEntry("last_error", "LLM generation failed");
    }

    @Test
    void markRunQueued_shouldClearProjectedFailureState() {
        when(jdbc.update(anyString(), ArgMatchers.any())).thenReturn(1);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        service.markRunQueued("twf_failure", "run_retry");

        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("failure_bucket = NULL");
        assertThat(sqlCaptor.getValue()).contains("last_error = ''");
        assertThat(paramsCaptor.getValue().getValue("sid")).isEqualTo("twf_failure");
        assertThat(paramsCaptor.getValue().getValue("rid")).isEqualTo("run_retry");
    }

    private static final class ArgMatchers {
        static MapSqlParameterSource any() {
            return org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class);
        }
    }
}
