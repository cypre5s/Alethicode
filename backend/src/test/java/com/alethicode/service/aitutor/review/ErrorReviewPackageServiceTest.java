package com.alethicode.service.aitutor.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.CreateReviewPackageRequest;
import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.service.aitutor.events.LearningEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorReviewPackageServiceTest {

    private static final Long USER_ID = 1L;
    private static final String ERROR_TAXONOMY = "logic_error";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private com.alethicode.service.ai.AiModelGateway aiModelGateway;

    @Mock
    private AlethicodeProperties properties;

    private ErrorReviewPackageService service;
    private SpecializedProblemGenerator specializedProblemGenerator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiProblemTestCaseWriter testCaseWriter = new AiProblemTestCaseWriter(objectMapper, properties);
        specializedProblemGenerator = new SpecializedProblemGenerator(jdbcTemplate, objectMapper, aiModelGateway, testCaseWriter);
        service = new ErrorReviewPackageService(jdbcTemplate, objectMapper, specializedProblemGenerator);
    }

    @Test
    void createPackagesShouldCreateOnePackagePerRequestedTaxonomy() {
        ErrorReviewPackageService spiedService = spy(service);
        ReviewPackageResponse logicPackage = new ReviewPackageResponse(
                "pkg-logic", "logic_error", "逻辑错误", Map.of(),
                3, 0, false, List.of(), "2026-04-25T00:00:00Z",
                "new", "2026-04-26T00:00:00Z", 1.0, 5.0, 1.0
        );
        ReviewPackageResponse inputPackage = new ReviewPackageResponse(
                "pkg-input", "input_parsing", "输入解析错误", Map.of(),
                3, 0, false, List.of(), "2026-04-25T00:00:00Z",
                "new", "2026-04-26T00:00:00Z", 1.0, 5.0, 1.0
        );
        doReturn(logicPackage).when(spiedService)
                .createPackage(USER_ID, "logic_error", 3L, 101L, "wrong_answer");
        doReturn(inputPackage).when(spiedService)
                .createPackage(USER_ID, "input_parsing", 3L, 102L, "wrong_answer");

        List<ReviewPackageResponse> responses = spiedService.createPackages(USER_ID, List.of(
                new CreateReviewPackageRequest("logic_error", 3L, 101L, "wrong_answer"),
                new CreateReviewPackageRequest("input_parsing", 3L, 102L, "wrong_answer")
        ));

        assertThat(responses).containsExactly(logicPackage, inputPackage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void createPackageShouldBuildFallbackSubmissionQueryWithoutJdbcLongArrayBinding() {
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("from ai_learner_notebook where user_id = ? and error_taxonomy = ?")),
                eq(Integer.class),
                eq(USER_ID),
                eq(ERROR_TAXONOMY)
        )).thenReturn(2);

        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("from ai_learning_event where user_id = ? and error_taxonomy = ?")),
                eq(Integer.class),
                eq(USER_ID),
                eq(ERROR_TAXONOMY)
        )).thenReturn(5);

        when(jdbcTemplate.query(
                argThat(sql -> sql != null
                        && sql.contains("from ai_learner_notebook")
                        && sql.contains("group by root_cause")
                        && sql.contains("order by count(*) desc")),
                any(RowMapper.class),
                eq(USER_ID),
                eq(ERROR_TAXONOMY)
        )).thenReturn(List.of("Loop condition not updated"));

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from ai_learner_notebook n")),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.of(101L));

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from submission s")),
                any(RowMapper.class),
                any(Object[].class)
        )).thenAnswer(invocation -> {
            Object[] sqlArgs = normalizeSqlArgs(invocation.getArguments());
            assertThat(containsLongArray(sqlArgs))
                    .as("fallback submission query should not use Long[] binding")
                    .isFalse();
            return List.of(102L, 103L);
        });

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from problem where id = ?")),
                any(RowMapper.class),
                any(Object[].class)
        )).thenAnswer(invocation -> {
            Object[] sqlArgs = normalizeSqlArgs(invocation.getArguments());
            long problemId = ((Number) sqlArgs[0]).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_id", "P" + problemId);
            row.put("title", "Problem " + problemId);
            return List.of(row);
        });

        ReviewPackageResponse response = service.createPackage(USER_ID, ERROR_TAXONOMY, null, null, null);

        assertThat(response.problemCount()).isEqualTo(3);
        assertThat(response.problems()).extracting(ReviewPackageResponse.ReviewProblemItem::problemId)
                .containsExactly(101L, 102L, 103L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void createPackageShouldReturnBeforeAiSpecializedProblemsFinish() throws Exception {
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("from ai_learner_notebook where user_id = ? and error_taxonomy = ?")),
                eq(Integer.class),
                eq(USER_ID),
                eq(ERROR_TAXONOMY)
        )).thenReturn(1);

        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("from ai_learning_event where user_id = ? and error_taxonomy = ?")),
                eq(Integer.class),
                eq(USER_ID),
                eq(ERROR_TAXONOMY)
        )).thenReturn(0);

        when(jdbcTemplate.query(
                argThat(sql -> sql != null
                        && sql.contains("from ai_learner_notebook")
                        && sql.contains("group by root_cause")
                        && sql.contains("order by count(*) desc")),
                any(RowMapper.class),
                eq(USER_ID),
                eq(ERROR_TAXONOMY)
        )).thenReturn(List.of("Loop condition not updated"));

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from ai_learner_notebook n")),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.of(101L, 102L, 103L));

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from problem where id = ?")),
                any(RowMapper.class),
                any(Object[].class)
        )).thenAnswer(invocation -> {
            Object[] sqlArgs = normalizeSqlArgs(invocation.getArguments());
            long problemId = ((Number) sqlArgs[0]).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_id", "P" + problemId);
            row.put("title", "Problem " + problemId);
            return List.of(row);
        });

        CountDownLatch releaseAiGeneration = new CountDownLatch(1);
        org.mockito.Mockito.lenient()
                .when(aiModelGateway.callForJson(any(String.class), any(String.class)))
                .thenAnswer(invocation -> {
                    boolean released = releaseAiGeneration.await(3, TimeUnit.SECONDS);
                    assertThat(released).isTrue();
                    throw new IllegalStateException("mock ai generation should not block package creation");
                });

        try {
            ReviewPackageResponse response = assertTimeoutPreemptively(
                    Duration.ofMillis(300),
                    () -> service.createPackage(USER_ID, ERROR_TAXONOMY, null, null, null)
            );
            assertThat(response.problemCount()).isEqualTo(3);
            assertThat(response.problems()).extracting(ReviewPackageResponse.ReviewProblemItem::problemId)
                    .containsExactly(101L, 102L, 103L);
        } finally {
            releaseAiGeneration.countDown();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void getPackageDetailShouldMarkProblemUnavailableWhenProblemRowIdIsNull() throws Exception {
        ReviewPackageResponse stubPkg = new ReviewPackageResponse(
                "pkg-1", ERROR_TAXONOMY, "逻辑错误",
                Map.of("notebook_count", 1, "event_count", 0),
                3, 0, false, List.of(), "2026-04-25T00:00:00Z",
                "new", "2026-04-26T00:00:00Z", 1.0, 5.0, 1.0
        );
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from ai_error_review_package where id = ?")),
                any(RowMapper.class),
                eq("pkg-1"),
                eq(USER_ID)
        )).thenReturn(List.of(stubPkg));

        java.sql.ResultSet availableRow = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        when(availableRow.getString("id")).thenReturn("rp-1");
        when(availableRow.getObject("problem_id")).thenReturn(101L);
        when(availableRow.getLong("problem_id")).thenReturn(101L);
        when(availableRow.getString("problem_key")).thenReturn("P101");
        when(availableRow.getString("title")).thenReturn("可练习题目");
        when(availableRow.getInt("sequence")).thenReturn(1);
        when(availableRow.getBoolean("submitted")).thenReturn(false);
        when(availableRow.getObject("is_correct")).thenReturn(null);
        when(availableRow.getBoolean("is_ai_generated")).thenReturn(false);
        when(availableRow.getString("user_rating")).thenReturn(null);
        when(availableRow.getString("question_type")).thenReturn("coding");

        java.sql.ResultSet missingRow = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        when(missingRow.getString("id")).thenReturn("rp-2");
        when(missingRow.getObject("problem_id")).thenReturn(null);
        when(missingRow.getString("problem_key")).thenReturn(null);
        when(missingRow.getString("title")).thenReturn(null);
        when(missingRow.getInt("sequence")).thenReturn(2);
        when(missingRow.getBoolean("submitted")).thenReturn(false);
        when(missingRow.getObject("is_correct")).thenReturn(null);
        when(missingRow.getBoolean("is_ai_generated")).thenReturn(false);
        when(missingRow.getString("user_rating")).thenReturn(null);
        when(missingRow.getString("question_type")).thenReturn("coding");

        when(jdbcTemplate.query(
                argThat(sql -> sql != null
                        && sql.contains("from ai_error_review_problem rp")
                        && sql.contains("left join problem p on p.id = rp.problem_id")),
                any(RowMapper.class),
                eq("pkg-1")
        )).thenAnswer(invocation -> {
            RowMapper<ReviewPackageResponse.ReviewProblemItem> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(availableRow, 0), mapper.mapRow(missingRow, 1));
        });

        ReviewPackageResponse detail = service.getPackageDetail(USER_ID, "pkg-1");

        assertThat(detail.problems()).hasSize(2);
        ReviewPackageResponse.ReviewProblemItem available = detail.problems().get(0);
        assertThat(available.problemId()).isEqualTo(101L);
        assertThat(available.isUnavailable()).isFalse();
        ReviewPackageResponse.ReviewProblemItem missing = detail.problems().get(1);
        assertThat(missing.problemId()).isNull();
        assertThat(missing.title()).isNull();
        assertThat(missing.isUnavailable()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void listPackagesShouldOrderDueReviewsBeforeRecentlyCreatedPackages() {
        when(jdbcTemplate.query(
                argThat(sql -> sql != null
                        && sql.contains("order by fsrs_due_at asc, created_at desc")),
                any(RowMapper.class),
                eq(USER_ID)
        )).thenReturn(List.of());

        service.listPackages(USER_ID);

        verify(jdbcTemplate).query(
                argThat(sql -> sql != null
                        && sql.contains("where user_id = ?")
                        && sql.contains("order by fsrs_due_at asc, created_at desc")),
                any(RowMapper.class),
                eq(USER_ID)
        );
    }

    @Test
    void reviewPackageShouldAdvanceFsrsScheduleWithExplicitRating() {
        LearningEventPublisher learningEventPublisher = org.mockito.Mockito.mock(LearningEventPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AiProblemTestCaseWriter testCaseWriter = new AiProblemTestCaseWriter(objectMapper, properties);
        SpecializedProblemGenerator specializedProblemGenerator = new SpecializedProblemGenerator(jdbcTemplate, objectMapper, aiModelGateway, testCaseWriter);
        FsrsSchedulerService fsrsScheduler = new FsrsSchedulerService();
        ReviewPackageFsrsAdvancer advancer = new ReviewPackageFsrsAdvancer(jdbcTemplate, fsrsScheduler, learningEventPublisher);
        ErrorReviewPackageService fsrsAwareService = spy(new ErrorReviewPackageService(
                jdbcTemplate,
                objectMapper,
                new com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService(jdbcTemplate),
                specializedProblemGenerator,
                new ReviewProblemSelector(jdbcTemplate),
                new ReviewPackageProblemMetaResolver(),
                advancer,
                new ReviewSubmissionRecorder(jdbcTemplate, advancer),
                learningEventPublisher,
                fsrsScheduler
        ));
        Instant dueAt = Instant.parse("2026-04-24T00:00:00Z");
        Instant reviewedAt = Instant.parse("2026-04-25T00:00:00Z");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fsrs_state", "review");
        row.put("fsrs_due_at", Timestamp.from(dueAt));
        row.put("fsrs_stability", 1.5);
        row.put("fsrs_difficulty", 4.4);
        row.put("fsrs_retrievability", 0.8);
        row.put("fsrs_reps", 1);
        row.put("fsrs_lapses", 0);
        row.put("fsrs_last_review_at", Timestamp.from(Instant.parse("2026-04-23T00:00:00Z")));

        when(jdbcTemplate.queryForMap(
                argThat(sql -> sql != null && sql.contains("from ai_error_review_package")),
                eq("pkg-1")
        )).thenReturn(row);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("select user_id from ai_error_review_package")),
                eq(Long.class),
                eq("pkg-1")
        )).thenReturn(USER_ID);
        doReturn(new ReviewPackageResponse(
                "pkg-1", ERROR_TAXONOMY, "逻辑错误", Map.of(),
                1, 1, true, List.of(), "2026-04-24T00:00:00Z",
                "review", "2026-04-26T00:00:00Z", 2.0, 4.0, 0.7
        )).when(fsrsAwareService).getPackageDetail(USER_ID, "pkg-1");

        fsrsAwareService.reviewPackage(USER_ID, "pkg-1", FsrsSchedulerService.ReviewRating.GOOD, reviewedAt);

        verify(jdbcTemplate).update(
                argThat(sql -> sql != null && sql.contains("set fsrs_state = ?")),
                eq("review"),
                any(Timestamp.class),
                any(Double.class),
                any(Double.class),
                any(Double.class),
                eq(2),
                eq(0),
                eq(Timestamp.from(reviewedAt)),
                eq("pkg-1")
        );
        verify(learningEventPublisher).publishReviewPackageUpdated(
                eq(USER_ID),
                eq("pkg-1"),
                eq("explicit_review"),
                any(Map.class)
        );
    }

    private static Object[] normalizeSqlArgs(Object[] invocationArguments) {
        Object[] sqlArgs = Arrays.copyOfRange(invocationArguments, 2, invocationArguments.length);
        if (sqlArgs.length == 1 && sqlArgs[0] instanceof Object[] nestedArgs) {
            return nestedArgs;
        }
        return sqlArgs;
    }

    private static boolean containsLongArray(Object[] values) {
        for (Object value : values) {
            if (value instanceof Long[]) {
                return true;
            }
            if (value instanceof Object[] nestedValues && containsLongArray(nestedValues)) {
                return true;
            }
        }
        return false;
    }
}
