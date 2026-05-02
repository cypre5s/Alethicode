package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.aitutor.review.ReviewProblemRatingService;
import com.alethicode.service.languagepack.impl.JudgeCheckResult;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService.JudgeUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ParsonsCapabilityService 单元测试，覆盖：
 *
 * <ul>
 *   <li>dispatch 主流程：mastery → fading → segmenter → distractor → 卡 schema 关键字段</li>
 *   <li>dispatch 在缺少 reference_solution_code 时 failfast</li>
 *   <li>grade block_pass 标记 walkthrough_required</li>
 *   <li>grade block_fail 累计 cascade_degrade（N=3）/ cascade_failfast（N=4）</li>
 *   <li>walkthrough 高分写 breakthrough notebook + parsons_breakthrough 事件</li>
 *   <li>walkthrough 低分允许 rewrite，不写 breakthrough</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ParsonsCapabilityServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ParsonsDistractorGenerator distractorGenerator;
    @Mock
    private MasteryNfkProjectionService masteryNfkProjectionService;
    @Mock
    private ParsonsWalkthroughEvaluator walkthroughEvaluator;
    @Mock
    private LanguagePackProblemJudgeCheckService judgeService;
    @Mock
    private ReviewProblemRatingService reviewProblemRatingService;

    private ObjectMapper objectMapper;
    private ParsonsProperties properties;
    private AdaptiveFadingPolicy fadingPolicy;
    private ParsonsTokenSegmenter segmenter;
    private ParsonsCapabilityService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new ParsonsProperties();
        fadingPolicy = new AdaptiveFadingPolicy(properties);
        segmenter = new ParsonsTokenSegmenter();
        service = new ParsonsCapabilityService(
                jdbcTemplate, objectMapper, properties, segmenter, distractorGenerator,
                fadingPolicy, masteryNfkProjectionService, walkthroughEvaluator, judgeService,
                reviewProblemRatingService);
    }

    @Test
    void dispatchProducesSchemaPayloadAndPersistsSession() {
        stubProblemMeta(101L, "求和题", "a = int(input())\nb = int(input())\nprint(a + b)\n", "Python3");
        stubKcs(101L, List.of(new long[]{55L, 1}, new long[]{66L, 2}), List.of("for循环", "输入输出"));
        when(masteryNfkProjectionService.getMasteryByKc(7L, List.of(55L, 66L)))
                .thenReturn(Map.of(
                        55L, MasteryWithSource.bkt(0.10, MasteryWithSource.FallbackReason.COVERAGE),
                        66L, MasteryWithSource.bkt(0.10, MasteryWithSource.FallbackReason.COVERAGE)));
        // mastery avg 0.10 → fading_level=0 → distractorCount=0；不会调 distractorGenerator

        ParsonsCapabilityService.DispatchResult result = service.dispatch(
                new ParsonsCapabilityService.DispatchRequest(7L, 101L, "twf-1", "card-x", null, "pkg-9", null));

        assertThat(result.parsonsSessionId()).startsWith("ps-");
        Map<String, Object> payload = result.cardPayload();
        assertThat(payload).containsKeys(
                "parsons_session_id", "fading_level", "blocks", "distractors",
                "mastery_snapshot", "instructions", "language", "fsrs_origin");
        assertThat(payload.get("fading_level")).isEqualTo(0);
        assertThat(payload.get("language")).isEqualTo("Python3");
        assertThat(payload.get("fsrs_origin")).isEqualTo("pkg-9");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) payload.get("blocks");
        assertThat(blocks).hasSize(3);
        assertThat(blocks).extracting(b -> b.get("id")).containsExactly("B0", "B1", "B2");
        @SuppressWarnings("unchecked")
        Map<String, Object> snap = (Map<String, Object>) payload.get("mastery_snapshot");
        assertThat(snap).containsKey("decision_at");
        assertThat(snap).containsKey("routing");
        @SuppressWarnings("unchecked")
        Map<String, Object> routing = (Map<String, Object>) snap.get("routing");
        assertThat(routing.keySet()).containsExactlyInAnyOrder("55", "66");
        // distractor 不被调用
        verify(distractorGenerator, never()).generate(any());
        // 写库 + 事件
        verify(jdbcTemplate).update(argThat(sql -> sql != null && sql.contains("insert into ai_parsons_session")),
                any(Object[].class));
        verify(jdbcTemplate).update(argThat(sql -> sql != null
                        && sql.contains("insert into ai_learning_event")
                        && sql.contains("error_taxonomy")
                        && sql.contains("root_cause")
                        && sql.contains("detector_name")),
                any(Object[].class));
    }

    @Test
    void dispatchFailsFastWhenReferenceCodeMissing() {
        stubProblemMeta(101L, "求和题", "  ", "Python3");

        assertThatThrownBy(() -> service.dispatch(
                new ParsonsCapabilityService.DispatchRequest(7L, 101L, null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reference_solution_code");
    }

    @Test
    void gradeReturnsJudgeAcAndAsksForWalkthroughWhenJudgeAccepts() {
        stubSessionRow("ps-1", 7L, 101L, "[" +
                "{\"id\":\"B0\",\"code\":\"a=1\",\"indent\":0}," +
                "{\"id\":\"B1\",\"code\":\"b=2\",\"indent\":0}," +
                "{\"id\":\"B2\",\"code\":\"print(a+b)\",\"indent\":0}]",
                0, null, 0, 2);
        stubProblemMeta(101L, "求和题", "a=1\nb=2\nprint(a+b)\n", "Python3");
        when(judgeService.executeAgainstStoredTestCases(anyString(), eq("Python3"), eq("tc-101"), eq(1000), eq(256)))
                .thenReturn(new JudgeCheckResult(true, List.of(), ""));

        ParsonsCapabilityService.GradeResult result = service.grade(
                new ParsonsCapabilityService.GradeRequest("ps-1", List.of("B0", "B1", "B2")));

        assertThat(result.passed()).isTrue();
        assertThat(result.judgeStatus()).isEqualTo("judge_ac");
        assertThat(result.walkthroughRequired()).isTrue();
        assertThat(result.cascadeDegrade()).isFalse();
        assertThat(result.cascadeFailfast()).isFalse();
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.currentFadingLevel()).isEqualTo(2);
        assertThat(result.nextFadingLevel())
                .as("通过时不需要降级")
                .isNull();
        verify(jdbcTemplate).update(argThat(sql -> sql != null && sql.contains("update ai_parsons_session")),
                any(Object[].class));
    }

    @Test
    void gradeReturnsJudgeWaWhenAssembledCodeFailsTestCases() {
        stubSessionRow("ps-1b", 7L, 101L, "[" +
                "{\"id\":\"B0\",\"code\":\"a=1\",\"indent\":0}," +
                "{\"id\":\"B1\",\"code\":\"b=2\",\"indent\":0}," +
                "{\"id\":\"B2\",\"code\":\"print(a+b)\",\"indent\":0}]",
                0, null, 0);
        stubProblemMeta(101L, "求和题", "a=1\nb=2\nprint(a+b)\n", "Python3");
        when(judgeService.executeAgainstStoredTestCases(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(false,
                        List.of(new JudgeCheckResult.CaseResult(0, false, "wrong", "", -1)),
                        ""));

        ParsonsCapabilityService.GradeResult result = service.grade(
                new ParsonsCapabilityService.GradeRequest("ps-1b", List.of("B0", "B1", "B2")));

        assertThat(result.passed()).isFalse();
        assertThat(result.walkthroughRequired()).isFalse();
        assertThat(result.judgeStatus()).isEqualTo("judge_wa");
        assertThat(result.hint()).contains("第 1 个测试点未通过");
    }

    @Test
    void gradeReturnsJudgeUnavailableWithoutCounting() {
        stubSessionRow("ps-1c", 7L, 101L, "[" +
                "{\"id\":\"B0\",\"code\":\"a=1\",\"indent\":0}," +
                "{\"id\":\"B1\",\"code\":\"b=2\",\"indent\":0}," +
                "{\"id\":\"B2\",\"code\":\"print(a+b)\",\"indent\":0}]",
                2, null, 0);
        stubProblemMeta(101L, "求和题", "a=1\nb=2\nprint(a+b)\n", "Python3");
        when(judgeService.executeAgainstStoredTestCases(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new JudgeUnavailableException("no judge"));

        ParsonsCapabilityService.GradeResult result = service.grade(
                new ParsonsCapabilityService.GradeRequest("ps-1c", List.of("B0", "B1", "B2")));

        assertThat(result.judgeStatus()).isEqualTo("judge_unavailable");
        assertThat(result.passed()).isFalse();
        assertThat(result.walkthroughRequired()).isFalse();
        // 判题不可用不计入 cascade（不阻塞学生），attempts 仍 +1 仅作 trace
        assertThat(result.cascadeDegrade()).isFalse();
        assertThat(result.cascadeFailfast()).isFalse();
        assertThat(result.attempts()).isEqualTo(3);
    }

    @Test
    void gradeBlockFailMarksCascadeDegradeAtThirdAttempt() {
        stubSessionRow("ps-2", 7L, 101L, "[" +
                "{\"id\":\"B0\",\"code\":\"a=1\",\"indent\":0}," +
                "{\"id\":\"B1\",\"code\":\"b=2\",\"indent\":0}," +
                "{\"id\":\"B2\",\"code\":\"print(a+b)\",\"indent\":0}]",
                2, null, 0, 2);

        ParsonsCapabilityService.GradeResult result = service.grade(
                new ParsonsCapabilityService.GradeRequest("ps-2", List.of("B1", "B0", "B2")));

        assertThat(result.passed()).isFalse();
        assertThat(result.judgeStatus()).isEqualTo("block_fail");
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.cascadeDegrade()).isTrue();
        assertThat(result.cascadeFailfast()).isFalse();
        assertThat(result.currentFadingLevel()).isEqualTo(2);
        assertThat(result.nextFadingLevel())
                .as("cascade degrade 时 next_fading_level 必须 = current - 1，绕过 mastery 重算")
                .isEqualTo(1);
        assertThat(result.hint()).isNotBlank();
    }

    @Test
    void gradeBlockFailEscalatesToFailfastAtFourthAttempt() {
        stubSessionRow("ps-3", 7L, 101L, "[" +
                "{\"id\":\"B0\",\"code\":\"a=1\",\"indent\":0}," +
                "{\"id\":\"B1\",\"code\":\"b=2\",\"indent\":0}]",
                3, null, 0);

        ParsonsCapabilityService.GradeResult result = service.grade(
                new ParsonsCapabilityService.GradeRequest("ps-3", List.of("B1", "B0")));

        assertThat(result.cascadeFailfast()).isTrue();
        assertThat(result.cascadeDegrade()).isFalse();
        assertThat(result.attempts()).isEqualTo(4);
        // failfast 时额外写 parsons_failed_cascade 事件
        verify(jdbcTemplate, atLeastOnce()).update(argThat(sql ->
                sql != null && sql.contains("insert into ai_learning_event")), any(Object[].class));
    }

    @Test
    void walkthroughHighScoreWritesBreakthroughNotebookAndEvent() {
        stubSessionRow("ps-4", 7L, 101L, "[]", 1, "judge_ac", 0);
        stubProblemMeta(101L, "求和题", "a=1\nb=2\nprint(a+b)\n", "Python3");
        when(walkthroughEvaluator.evaluate(any(), any(), any()))
                .thenReturn(new ParsonsWalkthroughEvaluator.Result(0.85, "解释清晰", true));

        ParsonsCapabilityService.WalkthroughResult result = service.submitWalkthrough(
                new ParsonsCapabilityService.WalkthroughRequest("ps-4", "我用 a 和 b 接收输入，再相加输出"));

        assertThat(result.passed()).isTrue();
        assertThat(result.canRewrite()).isFalse();
        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.breakthroughNotebookId()).startsWith("nb-");
        verify(jdbcTemplate).update(argThat(sql ->
                sql != null && sql.contains("insert into ai_learner_notebook")), any(Object[].class));
        // 至少写 walkthrough_submitted + breakthrough 两条事件
        verify(jdbcTemplate, times(2)).update(argThat(sql ->
                sql != null && sql.contains("insert into ai_learning_event")), any(Object[].class));
    }

    @Test
    void walkthroughLowScoreAllowsRewriteWithoutBreakthrough() {
        stubSessionRow("ps-5", 7L, 101L, "[]", 1, "judge_ac", 0);
        stubProblemMeta(101L, "求和题", "a=1\nb=2\nprint(a+b)\n", "Python3");
        when(walkthroughEvaluator.evaluate(any(), any(), any()))
                .thenReturn(new ParsonsWalkthroughEvaluator.Result(0.40, "请补充说明", false));

        ParsonsCapabilityService.WalkthroughResult result = service.submitWalkthrough(
                new ParsonsCapabilityService.WalkthroughRequest("ps-5", "拼起来就行"));

        assertThat(result.passed()).isFalse();
        assertThat(result.canRewrite()).isTrue();
        assertThat(result.breakthroughNotebookId()).isNull();
        verify(jdbcTemplate, never()).update(argThat(sql ->
                sql != null && sql.contains("insert into ai_learner_notebook")), any(Object[].class));
    }

    @Test
    void walkthroughRefusesWhenSessionNotPassedJudgePhase() {
        stubSessionRow("ps-6", 7L, 101L, "[]", 1, "judge_wa", 0);

        assertThatThrownBy(() -> service.submitWalkthrough(
                new ParsonsCapabilityService.WalkthroughRequest("ps-6", "无所谓")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未通过");
    }

    @Test
    void walkthroughPassWithFsrsOriginAdvancesReviewPackageGood() {
        stubSessionRow("ps-7", 7L, 101L, "[]", 1, "judge_ac", 0, 1, "pkg-9");
        stubProblemMeta(101L, "求和题", "a=1\nb=2\nprint(a+b)\n", "Python3");
        when(walkthroughEvaluator.evaluate(any(), any(), any()))
                .thenReturn(new ParsonsWalkthroughEvaluator.Result(0.9, "好", true));

        service.submitWalkthrough(new ParsonsCapabilityService.WalkthroughRequest("ps-7", "我把输入相加再输出"));

        verify(reviewProblemRatingService).recordParsonsOutcome(7L, "pkg-9", 101L, "good");
    }

    @Test
    void walkthroughPassWithoutFsrsOriginDoesNotTouchReviewPackage() {
        stubSessionRow("ps-8", 7L, 101L, "[]", 1, "judge_ac", 0, 1, null);
        stubProblemMeta(101L, "求和题", "a=1\nb=2\nprint(a+b)\n", "Python3");
        when(walkthroughEvaluator.evaluate(any(), any(), any()))
                .thenReturn(new ParsonsWalkthroughEvaluator.Result(0.9, "好", true));

        service.submitWalkthrough(new ParsonsCapabilityService.WalkthroughRequest("ps-8", "我把输入相加再输出"));

        verify(reviewProblemRatingService, never()).recordParsonsOutcome(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    void cascadeFailfastWithFsrsOriginAdvancesReviewPackageAgain() {
        stubSessionRow("ps-9", 7L, 101L, "[" +
                "{\"id\":\"B0\",\"code\":\"a=1\",\"indent\":0}," +
                "{\"id\":\"B1\",\"code\":\"b=2\",\"indent\":0}]",
                3, null, 0, 1, "pkg-10");

        ParsonsCapabilityService.GradeResult result = service.grade(
                new ParsonsCapabilityService.GradeRequest("ps-9", List.of("B1", "B0")));

        assertThat(result.cascadeFailfast()).isTrue();
        verify(reviewProblemRatingService).recordParsonsOutcome(7L, "pkg-10", 101L, "again");
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private void stubProblemMeta(long problemId, String title, String code, String language) {
        lenient().when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(problemId)))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("from problem")) {
                        RowMapper<?> mapper = inv.getArgument(1);
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getLong("id")).thenReturn(problemId);
                        when(rs.getString("title")).thenReturn(title);
                        when(rs.getString("reference_solution_code")).thenReturn(code);
                        when(rs.getString("language")).thenReturn(language);
                        when(rs.getString("test_case_id")).thenReturn("tc-" + problemId);
                        when(rs.getInt("time_limit")).thenReturn(1000);
                        when(rs.getInt("memory_limit")).thenReturn(256);
                        return mapper.mapRow(rs, 1);
                    }
                    throw new EmptyResultDataAccessException("unexpected sql", 1);
                });
    }

    @SuppressWarnings("unchecked")
    private void stubKcs(long problemId, List<long[]> kcIdsAndWeights, List<String> kcNames) {
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(problemId)))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (!sql.contains("ai_problem_kc_mapping")) return List.of();
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.util.List<Object> result = new java.util.ArrayList<>();
                    for (int i = 0; i < kcIdsAndWeights.size(); i++) {
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getLong("id")).thenReturn(kcIdsAndWeights.get(i)[0]);
                        when(rs.getString("name")).thenReturn(kcNames.get(i));
                        result.add(mapper.mapRow(rs, i));
                    }
                    return result;
                });
    }

    private void stubSessionRow(String sessionId, long userId, long problemId,
                                String blocksJson, int submissionCount, String judgeStatus,
                                int walkthroughAttempts) {
        stubSessionRow(sessionId, userId, problemId, blocksJson, submissionCount, judgeStatus,
                walkthroughAttempts, 1, null);
    }

    private void stubSessionRow(String sessionId, long userId, long problemId,
                                String blocksJson, int submissionCount, String judgeStatus,
                                int walkthroughAttempts, int fadingLevel) {
        stubSessionRow(sessionId, userId, problemId, blocksJson, submissionCount, judgeStatus,
                walkthroughAttempts, fadingLevel, null);
    }

    @SuppressWarnings("unchecked")
    private void stubSessionRow(String sessionId, long userId, long problemId,
                                String blocksJson, int submissionCount, String judgeStatus,
                                int walkthroughAttempts, int fadingLevel, String fsrsOrigin) {
        lenient().when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(sessionId)))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (!sql.contains("from ai_parsons_session")) {
                        throw new EmptyResultDataAccessException("unexpected sql", 1);
                    }
                    RowMapper<?> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn(sessionId);
                    when(rs.getLong("user_id")).thenReturn(userId);
                    when(rs.getLong("problem_id")).thenReturn(problemId);
                    when(rs.getString("language")).thenReturn("Python3");
                    when(rs.getInt("fading_level")).thenReturn(fadingLevel);
                    when(rs.getString("mastery_snapshot")).thenReturn("{\"routing\":{}}");
                    when(rs.getString("blocks")).thenReturn(blocksJson);
                    when(rs.getString("distractors")).thenReturn("[]");
                    when(rs.getInt("submission_count")).thenReturn(submissionCount);
                    when(rs.getString("judge_status")).thenReturn(judgeStatus);
                    when(rs.getInt("walkthrough_attempts")).thenReturn(walkthroughAttempts);
                    when(rs.getString("previous_session_id")).thenReturn(null);
                    when(rs.getString("fsrs_origin")).thenReturn(fsrsOrigin);
                    when(rs.getString("instructions")).thenReturn("");
                    return mapper.mapRow(rs, 1);
                });
    }
}
