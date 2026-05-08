package com.alethicode.service.classroom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearnerCourseProgressServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long LANGUAGE_PACK_ID = 43L;

    private JdbcTemplate jdbcTemplate;
    private LearnerCourseProgressService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new LearnerCourseProgressService(jdbcTemplate);
    }

    @Test
    void getOrCreateProgressShouldReplaySubmissionHistoryWhenMasteryIsMissing() {
        Map<String, Object> emptyProgress = new LinkedHashMap<>();
        emptyProgress.put("user_id", USER_ID);
        emptyProgress.put("language_pack_id", LANGUAGE_PACK_ID);
        emptyProgress.put("overall_mastery", 0.0);
        emptyProgress.put("problems_attempted", 0);
        emptyProgress.put("problems_solved", 0);
        emptyProgress.put("last_activity_at", null);

        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null
                        && sql.contains("FROM learner_course_progress")
                        && sql.contains("WHERE user_id = ? AND language_pack_id = ?")),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        )).thenReturn(List.of(emptyProgress));

        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null
                        && sql.contains("FROM learner_kc_mastery")
                        && sql.contains("COUNT(*)")),
                eq(Integer.class),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        )).thenReturn(0);

        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null
                        && sql.contains("FROM submission s")
                        && sql.contains("JOIN ai_problem_kc_mapping m")
                        && sql.contains("ORDER BY s.create_time ASC")),
                eq(LANGUAGE_PACK_ID),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        )).thenReturn(List.of(
                submissionRow(100L, -1, "2026-04-28T09:00:00Z"),
                submissionRow(100L, 0, "2026-04-28T09:05:00Z"),
                submissionRow(101L, 0, "2026-04-28T09:05:00Z")
        ));

        Map<String, Object> rebuiltProgress = new LinkedHashMap<>();
        rebuiltProgress.put("user_id", USER_ID);
        rebuiltProgress.put("language_pack_id", LANGUAGE_PACK_ID);
        rebuiltProgress.put("overall_mastery", 0.85);
        rebuiltProgress.put("problems_attempted", 3);
        rebuiltProgress.put("problems_solved", 2);

        when(jdbcTemplate.queryForMap(
                argThat(sql -> sql != null
                        && sql.contains("FROM learner_course_progress")
                        && sql.contains("WHERE user_id = ? AND language_pack_id = ?")),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        )).thenReturn(rebuiltProgress);

        Map<String, Object> progress = service.getOrCreateProgress(USER_ID, LANGUAGE_PACK_ID);

        assertThat(progress)
                .containsEntry("problems_attempted", 3)
                .containsEntry("problems_solved", 2)
                .containsEntry("overall_mastery", 0.85);
    }

    @Test
    void getOrCreateProgressShouldRefreshBlankRowWhenMasteryAlreadyExists() {
        Map<String, Object> emptyProgress = new LinkedHashMap<>();
        emptyProgress.put("user_id", USER_ID);
        emptyProgress.put("language_pack_id", LANGUAGE_PACK_ID);
        emptyProgress.put("overall_mastery", 0.0);
        emptyProgress.put("problems_attempted", 0);
        emptyProgress.put("problems_solved", 0);
        emptyProgress.put("last_activity_at", null);

        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null
                        && sql.contains("FROM learner_course_progress")
                        && sql.contains("WHERE user_id = ? AND language_pack_id = ?")),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        )).thenReturn(List.of(emptyProgress));

        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null
                        && sql.contains("FROM learner_kc_mastery")
                        && sql.contains("COUNT(*)")),
                eq(Integer.class),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        )).thenReturn(2);

        Map<String, Object> refreshedProgress = new LinkedHashMap<>();
        refreshedProgress.put("user_id", USER_ID);
        refreshedProgress.put("language_pack_id", LANGUAGE_PACK_ID);
        refreshedProgress.put("overall_mastery", 0.7);
        refreshedProgress.put("problems_attempted", 4);
        refreshedProgress.put("problems_solved", 3);

        when(jdbcTemplate.queryForMap(
                argThat(sql -> sql != null
                        && sql.contains("FROM learner_course_progress")
                        && sql.contains("WHERE user_id = ? AND language_pack_id = ?")),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        )).thenReturn(refreshedProgress);

        Map<String, Object> progress = service.getOrCreateProgress(USER_ID, LANGUAGE_PACK_ID);

        assertThat(progress)
                .containsEntry("problems_attempted", 4)
                .containsEntry("problems_solved", 3)
                .containsEntry("overall_mastery", 0.7);
        verify(jdbcTemplate).update(
                argThat(sql -> sql != null
                        && sql.contains("INSERT INTO learner_course_progress")
                        && sql.contains("ON CONFLICT (user_id, language_pack_id)")),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID),
                eq(USER_ID),
                eq(LANGUAGE_PACK_ID)
        );
    }

    private Map<String, Object> submissionRow(Long kcId, int resultCode, String createdAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kc_id", kcId);
        row.put("result", resultCode);
        row.put("create_time", Timestamp.from(Instant.parse(createdAt)));
        return row;
    }
}
