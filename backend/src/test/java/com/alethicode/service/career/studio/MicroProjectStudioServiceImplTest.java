package com.alethicode.service.career.studio;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.review.AiProblemTestCaseWriter;
import com.alethicode.service.career.bridging.CareerBridgingService;
import com.alethicode.service.career.bridging.MilestoneType;
import com.alethicode.service.career.studio.MicroProjectStudioService.CareerMicroProject;
import com.alethicode.service.career.studio.MicroProjectStudioService.MicroProjectRecommendation;
import com.alethicode.service.languagepack.impl.JudgeCheckResult;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MicroProjectStudioServiceImpl} 单测 —— 覆盖 plan 5.1 节核心契约：
 * <ul>
 *   <li>recommendForUser：mastery 全 &lt; 0.5 ⇒ 空；mastery 部分 &gt;= 0.5 ⇒ 给 1 个推荐；</li>
 *   <li>generate critic 拒绝 ⇒ 不真判题、不落库、empty；</li>
 *   <li>generate 缺 reference_solution.code 或 test_cases ⇒ 不真判题、不落库、empty；</li>
 *   <li>generate 真判题失败（reference 自身 not all-pass）⇒ 不落库、empty；</li>
 *   <li>generate 真判题异常（Judge Server 不可用 / 编译失败）⇒ 不落库、empty；</li>
 *   <li>generate 真判题通过 ⇒ 落 problem 表 + career_micro_project + 返回 CareerMicroProject 含 judge_problem_id；</li>
 *   <li>markCompleted 已不存在或已完成 ⇒ 抛 404；</li>
 *   <li>markCompleted 正常 ⇒ 写 PROJECT_COMPLETED 里程碑。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MicroProjectStudioServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AiModelGateway aiModelGateway;
    @Mock
    private ReflectionService reflectionService;
    @Mock
    private MasteryService masteryService;
    @Mock
    private CareerBridgingService careerBridgingService;
    @Mock
    private AiProblemTestCaseWriter testCaseWriter;
    @Mock
    private LanguagePackProblemJudgeCheckService judgeCheckService;

    private ObjectMapper objectMapper;
    private MicroProjectStudioServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new MicroProjectStudioServiceImpl(
                jdbcTemplate, objectMapper, aiModelGateway, reflectionService,
                masteryService, careerBridgingService, testCaseWriter, judgeCheckService);
    }

    // ---------- recommendForUser ----------

    @Test
    void recommendReturnsEmptyWhenAllMasteryBelowThreshold() {
        Map<String, Double> low = new LinkedHashMap<>();
        low.put("variables", 0.4);
        low.put("functions", 0.2);
        when(masteryService.projectMasteryByLanguagePack(eq(7L), eq(null))).thenReturn(low);

        List<MicroProjectRecommendation> result = service.recommendForUser(7L);

        assertThat(result).isEmpty();
    }

    @Test
    void recommendReturnsRecommendationWhenSomeMasteryAboveThreshold() {
        Map<String, Double> mastery = new LinkedHashMap<>();
        mastery.put("variables", 0.85);
        mastery.put("functions", 0.6);
        mastery.put("loops", 0.3);
        when(masteryService.projectMasteryByLanguagePack(eq(7L), eq(null))).thenReturn(mastery);

        List<MicroProjectRecommendation> result = service.recommendForUser(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).kcCodes()).contains("variables", "functions");
        assertThat(result.get(0).kcCodes()).doesNotContain("loops");
    }

    // ---------- generate ----------

    @Test
    void generateReturnsEmptyWhenCriticRejects() {
        long userId = 7L;
        String major = "biology";
        stubLoadMajorRow(major);
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("micro-project")))
                .thenReturn(Map.of("problem", Map.of("title", "x")));
        when(reflectionService.reflectAndRefine(eq(CardType.MICRO_PROJECT_BRIEF), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(Map.of(), false, 1, "off-topic"));

        Optional<CareerMicroProject> result = service.generate(userId, major, List.of("variables"));

        assertThat(result).isEmpty();
        verify(judgeCheckService, never()).executeReferenceSolution(anyString(), anyString(), any(), anyInt(), anyInt());
        verify(jdbcTemplate, never()).queryForObject(argThat(sqlContains("insert into problem")),
                eq(Long.class), any(Object[].class));
    }

    @Test
    void generateReturnsEmptyWhenReferenceSolutionMissing() {
        long userId = 7L;
        String major = "biology";
        stubLoadMajorRow(major);
        Map<String, Object> output = Map.of(
                "problem", Map.of("title", "x", "test_cases", List.of(Map.of("input", "1", "expected", "1")))
                // 注：故意不给 reference_solution
        );
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("micro-project"))).thenReturn(output);
        when(reflectionService.reflectAndRefine(eq(CardType.MICRO_PROJECT_BRIEF), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(output, true, 1, "ok"));

        Optional<CareerMicroProject> result = service.generate(userId, major, List.of("variables"));

        assertThat(result).isEmpty();
        verify(judgeCheckService, never()).executeReferenceSolution(anyString(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void generateReturnsEmptyWhenTestCasesEmpty() {
        long userId = 7L;
        String major = "biology";
        stubLoadMajorRow(major);
        Map<String, Object> output = Map.of(
                "problem", Map.of("title", "x", "test_cases", List.of()),
                "reference_solution", Map.of("code", "print('hi')")
        );
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("micro-project"))).thenReturn(output);
        when(reflectionService.reflectAndRefine(eq(CardType.MICRO_PROJECT_BRIEF), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(output, true, 1, "ok"));

        Optional<CareerMicroProject> result = service.generate(userId, major, List.of("variables"));

        assertThat(result).isEmpty();
        verify(judgeCheckService, never()).executeReferenceSolution(anyString(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void generateReturnsEmptyWhenJudgeReportsFailures() {
        long userId = 7L;
        String major = "biology";
        Map<String, Object> output = sampleLlmOutput();
        stubLoadMajorRow(major);
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("micro-project"))).thenReturn(output);
        when(reflectionService.reflectAndRefine(eq(CardType.MICRO_PROJECT_BRIEF), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(output, true, 1, "ok"));
        JudgeCheckResult.CaseResult fail = new JudgeCheckResult.CaseResult(0, false, "", "WA", -1);
        when(judgeCheckService.executeReferenceSolution(anyString(), eq("Python3"), any(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(false, List.of(fail), ""));

        Optional<CareerMicroProject> result = service.generate(userId, major, List.of("variables"));

        assertThat(result).isEmpty();
        verify(jdbcTemplate, never()).queryForObject(argThat(sqlContains("insert into problem")),
                eq(Long.class), any(Object[].class));
        verify(testCaseWriter, never()).writeTestCases(anyString(), any());
    }

    @Test
    void generateReturnsEmptyWhenJudgeRaisesException() {
        long userId = 7L;
        String major = "biology";
        Map<String, Object> output = sampleLlmOutput();
        stubLoadMajorRow(major);
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("micro-project"))).thenReturn(output);
        when(reflectionService.reflectAndRefine(eq(CardType.MICRO_PROJECT_BRIEF), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(output, true, 1, "ok"));
        when(judgeCheckService.executeReferenceSolution(anyString(), eq("Python3"), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("judge unavailable"));

        Optional<CareerMicroProject> result = service.generate(userId, major, List.of("variables"));

        assertThat(result).isEmpty();
        verify(jdbcTemplate, never()).queryForObject(argThat(sqlContains("insert into problem")),
                eq(Long.class), any(Object[].class));
    }

    @Test
    void generatePersistsBothProblemAndMicroProjectWhenJudgePasses() {
        long userId = 7L;
        String major = "biology";
        Map<String, Object> output = sampleLlmOutput();
        stubLoadMajorRow(major);
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("micro-project"))).thenReturn(output);
        when(reflectionService.reflectAndRefine(eq(CardType.MICRO_PROJECT_BRIEF), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(output, true, 1, "ok"));
        when(judgeCheckService.executeReferenceSolution(anyString(), eq("Python3"), any(), anyInt(), anyInt()))
                .thenReturn(new JudgeCheckResult(true, List.of(
                        new JudgeCheckResult.CaseResult(0, true, "", "", 0),
                        new JudgeCheckResult.CaseResult(1, true, "", "", 0)), ""));
        when(jdbcTemplate.queryForObject(argThat(sqlContains("insert into problem")),
                eq(Long.class), any(Object[].class))).thenReturn(8888L);
        when(testCaseWriter.buildTestCaseScoreJson(anyInt())).thenReturn("{\"1\":50,\"2\":50}");
        // career_micro_project insert via PreparedStatementCreator + KeyHolder
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder holder = invocation.getArgument(1);
                    holder.getKeyList().add(Map.of("id", 333L));
                    return 1;
                });

        Optional<CareerMicroProject> result = service.generate(userId, major, List.of("variables", "functions"));

        assertThat(result).isPresent();
        CareerMicroProject project = result.get();
        assertThat(project.id()).isEqualTo(333L);
        assertThat(project.userId()).isEqualTo(userId);
        assertThat(project.majorCode()).isEqualTo(major);
        assertThat(project.judgeProblemId()).isEqualTo(8888L);
        assertThat(project.status()).isEqualTo("recommended");

        verify(testCaseWriter, times(1)).writeTestCases(anyString(), any());
        verify(jdbcTemplate, times(1)).queryForObject(argThat(sqlContains("insert into problem")),
                eq(Long.class), any(Object[].class));
        verify(jdbcTemplate, times(1)).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    // ---------- markCompleted ----------

    @Test
    void markCompletedThrows404WhenNotFoundOrAlreadyCompleted() {
        when(jdbcTemplate.update(argThat(sqlContains("update career_micro_project")), eq(85), eq(101L)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.markCompleted(101L, 85.0))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
    }

    @Test
    void markCompletedRecordsProjectCompletedMilestoneAndReactivatesReport() {
        when(jdbcTemplate.update(argThat(sqlContains("update career_micro_project")), eq(85), eq(101L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("select user_id from career_micro_project")),
                eq(Long.class), eq(101L)))
                .thenReturn(7L);
        when(careerBridgingService.recordMilestone(eq(7L), eq(MilestoneType.PROJECT_COMPLETED), eq("project:101")))
                .thenReturn(555L);

        service.markCompleted(101L, 85.0);

        verify(careerBridgingService, times(1)).recordMilestone(
                eq(7L), eq(MilestoneType.PROJECT_COMPLETED), eq("project:101"));
        // todo 13: project_completed 后立即重激活 Why 报告
        verify(careerBridgingService, times(1)).generateForMilestone(eq(7L), eq(555L));
    }

    @Test
    void markCompletedSwallowsBridgingReactivationFailure() {
        when(jdbcTemplate.update(argThat(sqlContains("update career_micro_project")), eq(85), eq(101L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("select user_id from career_micro_project")),
                eq(Long.class), eq(101L)))
                .thenReturn(7L);
        when(careerBridgingService.recordMilestone(anyLong(), any(), anyString())).thenReturn(555L);
        when(careerBridgingService.generateForMilestone(eq(7L), eq(555L)))
                .thenThrow(new IllegalStateException("LLM down"));

        // 不抛异常：项目仍标 passed，里程碑已写入，报告失败仅 log.warn
        service.markCompleted(101L, 85.0);

        verify(careerBridgingService, times(1)).generateForMilestone(eq(7L), eq(555L));
    }

    @Test
    void markCompletedSwallowsEmptyResultWhenLookingUpUserId() {
        when(jdbcTemplate.update(argThat(sqlContains("update career_micro_project")), eq(85), eq(101L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("select user_id from career_micro_project")),
                eq(Long.class), eq(101L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        service.markCompleted(101L, 85.0);

        verify(careerBridgingService, never()).recordMilestone(anyLong(), any(), anyString());
        verify(careerBridgingService, never()).generateForMilestone(anyLong(), anyLong());
    }

    // ---------- markCompletedByJudgeProblem ----------

    @Test
    void markCompletedByJudgeProblemReturnsFalseWhenNoMatchingProject() {
        when(jdbcTemplate.queryForList(
                argThat(sqlContains("from career_micro_project")
                        .and(sqlContains("judge_problem_id"))),
                eq(Long.class), eq(7L), eq(8888L)))
                .thenReturn(List.of());

        boolean triggered = service.markCompletedByJudgeProblem(7L, 8888L, 100.0);

        assertThat(triggered).isFalse();
        verify(jdbcTemplate, never()).update(argThat(sqlContains("update career_micro_project")),
                any(), any());
    }

    @Test
    void markCompletedByJudgeProblemMarksAndTriggersReportWhenMatched() {
        when(jdbcTemplate.queryForList(
                argThat(sqlContains("from career_micro_project")
                        .and(sqlContains("judge_problem_id"))),
                eq(Long.class), eq(7L), eq(8888L)))
                .thenReturn(List.of(101L));
        // markCompleted 内部链路
        when(jdbcTemplate.update(argThat(sqlContains("update career_micro_project")), eq(100), eq(101L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat(sqlContains("select user_id from career_micro_project")),
                eq(Long.class), eq(101L)))
                .thenReturn(7L);
        when(careerBridgingService.recordMilestone(eq(7L), eq(MilestoneType.PROJECT_COMPLETED), eq("project:101")))
                .thenReturn(555L);

        boolean triggered = service.markCompletedByJudgeProblem(7L, 8888L, 100.0);

        assertThat(triggered).isTrue();
        verify(careerBridgingService, times(1)).generateForMilestone(eq(7L), eq(555L));
    }

    @Test
    void markCompletedByJudgeProblemSwallows404FromConcurrentRace() {
        when(jdbcTemplate.queryForList(
                argThat(sqlContains("from career_micro_project")
                        .and(sqlContains("judge_problem_id"))),
                eq(Long.class), eq(7L), eq(8888L)))
                .thenReturn(List.of(101L));
        when(jdbcTemplate.update(argThat(sqlContains("update career_micro_project")), eq(100), eq(101L)))
                .thenReturn(0); // 已被另一线程标完

        boolean triggered = service.markCompletedByJudgeProblem(7L, 8888L, 100.0);

        assertThat(triggered).isFalse();
    }

    // ---------- helpers ----------

    private void stubLoadMajorRow(String major) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from career_major_dictionary")),
                        any(org.springframework.jdbc.core.RowMapper.class), eq(major)))
                .thenAnswer(invocation -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", major);
                    row.put("name_zh", major);
                    row.put("seed_use_cases", "[]");
                    return row;
                });
    }

    private static Map<String, Object> sampleLlmOutput() {
        Map<String, Object> output = new LinkedHashMap<>();
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("title", "DNA GC 计算");
        problem.put("description_md", "## 背景\n生物专业实验数据处理…");
        problem.put("input_description", "DNA 序列字符串");
        problem.put("output_description", "GC 含量保留 4 位小数");
        problem.put("sample_input", "ATGC");
        problem.put("sample_output", "0.5000");
        problem.put("test_cases", List.of(
                Map.of("input", "ATGC", "expected", "0.5000"),
                Map.of("input", "AAAA", "expected", "0.0000")
        ));
        output.put("problem", problem);
        output.put("reference_solution", Map.of(
                "language", "Python3",
                "code", "s=input().strip()\nprint(f\"{(s.count('G')+s.count('C'))/len(s):.4f}\")"
        ));
        return output;
    }

    private static SqlMatcher sqlContains(String fragment) {
        return new SqlMatcher(fragment);
    }

    private static class SqlMatcher implements org.mockito.ArgumentMatcher<String> {
        private final String fragment;

        SqlMatcher(String fragment) {
            this.fragment = fragment;
        }

        @Override
        public boolean matches(String argument) {
            return argument != null && argument.toLowerCase().contains(fragment.toLowerCase());
        }

        SqlMatcher and(SqlMatcher other) {
            String thisFrag = this.fragment;
            return new SqlMatcher(thisFrag) {
                @Override
                public boolean matches(String argument) {
                    return argument != null
                            && argument.toLowerCase().contains(thisFrag.toLowerCase())
                            && argument.toLowerCase().contains(other.fragment.toLowerCase());
                }
            };
        }

        @Override
        public String toString() {
            return "sqlContains(" + fragment + ")";
        }
    }
}
