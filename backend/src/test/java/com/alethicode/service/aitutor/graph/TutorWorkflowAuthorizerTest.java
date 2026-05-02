package com.alethicode.service.aitutor.graph;

import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.AccessDenied;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.LanguageNotAllowed;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.ProblemAccess;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.ProblemNotFound;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.SubmissionMismatch;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.SubmissionNotAccepted;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.SubmissionNotFound;
import com.alethicode.service.aitutor.graph.TutorWorkflowAuthorizer.SubmissionRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TutorWorkflowAuthorizerTest {

    private NamedParameterJdbcTemplate jdbc;
    private CacheManager cacheManager;
    private TutorWorkflowAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        cacheManager = new ConcurrentMapCacheManager(TutorWorkflowAuthorizer.PROBLEM_ACCESS_CACHE);
        ((ConcurrentMapCacheManager) cacheManager).setAllowNullValues(true);
        authorizer = new TutorWorkflowAuthorizer(jdbc, cacheManager);
    }

    @Test
    void assertProblemAccessible_missingProblem_throwsProblemNotFound() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of());
        assertThatExceptionOfType(ProblemNotFound.class)
                .isThrownBy(() -> authorizer.assertProblemAccessible(42L, 7L, "Python3"));
    }

    @Test
    void assertProblemAccessible_notVisibleAndNotOwner_throwsAccessDenied() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(Map.of(
                "id", 42L,
                "created_by_id", 99L,
                "visible", false,
                "is_public", false,
                "languages_json", "[\"Python3\"]")));
        assertThatExceptionOfType(AccessDenied.class)
                .isThrownBy(() -> authorizer.assertProblemAccessible(42L, 7L, "Python3"));
    }

    @Test
    void assertProblemAccessible_languageNotInList_throwsLanguageNotAllowed() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(Map.of(
                "id", 42L,
                "created_by_id", 99L,
                "visible", true,
                "is_public", false,
                "languages_json", "[\"Python3\"]")));
        assertThatExceptionOfType(LanguageNotAllowed.class)
                .isThrownBy(() -> authorizer.assertProblemAccessible(42L, 7L, "Go"));
    }

    @Test
    void assertProblemAccessible_empty_languages_failsInsteadOfOpening() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(Map.of(
                "id", 42L,
                "created_by_id", 99L,
                "visible", true,
                "is_public", false,
                "languages_json", "[]")));
        assertThatExceptionOfType(LanguageNotAllowed.class)
                .isThrownBy(() -> authorizer.assertProblemAccessible(42L, 7L, "Python3"));
    }

    @Test
    void assertProblemAccessible_happyPath_returnsAccessMetadata() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(Map.of(
                "id", 42L,
                "created_by_id", 7L,
                "visible", false,
                "is_public", true,
                "languages_json", "[\"Python3\",\"Java\"]")));
        ProblemAccess access = authorizer.assertProblemAccessible(42L, 7L, "Python3");
        assertThat(access.allowedLanguages()).containsExactlyInAnyOrderElementsOf(Set.of("Python3", "Java"));
        assertThat(access.isPublic()).isTrue();
        assertThat(access.languagesCorrupt()).isFalse();
    }

    @Test
    void assertProblemAccessible_corruptLanguagesJson_throwsLanguageNotAllowedWithDedicatedMessage() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(Map.of(
                "id", 42L,
                "created_by_id", 99L,
                "visible", true,
                "is_public", false,
                "languages_json", "not valid json")));
        assertThatExceptionOfType(LanguageNotAllowed.class)
                .isThrownBy(() -> authorizer.assertProblemAccessible(42L, 7L, "Python3"))
                .withMessageContaining("corrupt language metadata");
    }

    @Test
    void assertSubmissionBelongsTo_notFound_throwsSubmissionNotFound() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of());
        assertThatExceptionOfType(SubmissionNotFound.class)
                .isThrownBy(() -> authorizer.assertSubmissionBelongsTo("sub_1", 7L, 42L));
    }

    @Test
    void assertSubmissionBelongsTo_wrongUser_throwsSubmissionMismatch() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(Map.of(
                "id", "sub_1",
                "user_id", 99L,
                "problem_id", 42L,
                "result", 0)));
        assertThatExceptionOfType(SubmissionMismatch.class)
                .isThrownBy(() -> authorizer.assertSubmissionBelongsTo("sub_1", 7L, 42L));
    }

    @Test
    void assertSubmissionBelongsTo_wrongProblem_throwsSubmissionMismatch() {
        when(jdbc.queryForList(anyString(), ArgMatchers.any())).thenReturn(List.of(Map.of(
                "id", "sub_1",
                "user_id", 7L,
                "problem_id", 999L,
                "result", 0)));
        assertThatExceptionOfType(SubmissionMismatch.class)
                .isThrownBy(() -> authorizer.assertSubmissionBelongsTo("sub_1", 7L, 42L));
    }

    @Test
    void assertSubmissionAccepted_nonAc_throwsSubmissionNotAccepted() {
        SubmissionRef wa = new SubmissionRef("sub_1", 7L, 42L, 1);
        assertThatExceptionOfType(SubmissionNotAccepted.class)
                .isThrownBy(() -> authorizer.assertSubmissionAccepted(wa));
    }

    @Test
    void assertSubmissionAccepted_ac_passes() {
        SubmissionRef ac = new SubmissionRef("sub_1", 7L, 42L, 0);
        authorizer.assertSubmissionAccepted(ac);
    }

    /** Mockito {@code ArgumentMatchers.any(MapSqlParameterSource.class)} is the correct overload. */
    private static final class ArgMatchers {
        static MapSqlParameterSource any() {
            return org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class);
        }
    }
}
