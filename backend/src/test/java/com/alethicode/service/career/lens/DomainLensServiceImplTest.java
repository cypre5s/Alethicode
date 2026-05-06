package com.alethicode.service.career.lens;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.rollout.RolloutDecision;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DomainLensServiceImpl} 单测——覆盖 plan 4.1 节核心约束：
 * 缓存命中直接返回 / rollback 不生成 / LLM abort 不写库 /
 * critic 拒绝不写库 / critic 通过写库 / lockForExam / invalidate。
 */
@ExtendWith(MockitoExtension.class)
class DomainLensServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AiModelGateway aiModelGateway;
    @Mock
    private ReflectionService reflectionService;
    @Mock
    private RolloutPolicyService rolloutPolicyService;

    private ObjectMapper objectMapper;
    private DomainLensServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new DomainLensServiceImpl(
                jdbcTemplate, objectMapper, aiModelGateway, reflectionService, rolloutPolicyService);
    }

    @Test
    void cacheHitReturnsExistingVariantWithoutCallingLlm() {
        long problemId = 7L;
        String major = "biology";
        stubFindCachedReturning(problemId, major);

        Optional<ProblemDomainVariant> variant = service.findOrGenerate(problemId, major);

        assertThat(variant).isPresent();
        assertThat(variant.get().problemId()).isEqualTo(problemId);
        verify(rolloutPolicyService, never()).evaluate(anyString(), anyString(), any());
        verify(aiModelGateway, never()).callForJson(anyString(), anyString(), anyString());
    }

    @Test
    void rollbackDecisionSkipsLlmAndReturnsEmpty() {
        long problemId = 8L;
        String major = "chemistry";
        stubFindCachedEmpty(problemId, major);
        when(rolloutPolicyService.evaluate(eq("coding_lens"), eq("problem:" + problemId), any()))
                .thenReturn(new RolloutDecision("rollback", "force", Map.of()));

        Optional<ProblemDomainVariant> variant = service.findOrGenerate(problemId, major);

        assertThat(variant).isEmpty();
        verify(aiModelGateway, never()).callForJson(anyString(), anyString(), anyString());
    }

    @Test
    void llmSelfAbortReturnsEmptyWithoutPersisting() {
        long problemId = 9L;
        String major = "medicine";
        stubFindCachedEmpty(problemId, major);
        stubRolloutBaseline(problemId);
        stubLoadProblem(problemId);
        stubLoadMajor(major);

        when(aiModelGateway.callForJson(anyString(), anyString(), eq("coding-lens")))
                .thenReturn(Map.of("abort", true));

        Optional<ProblemDomainVariant> variant = service.findOrGenerate(problemId, major);

        assertThat(variant).isEmpty();
        verify(reflectionService, never()).reflectAndRefine(any(), any(), any(), anyInt());
        verify(jdbcTemplate, never()).update(argThat(sqlContains("insert into problem_domain_variant")), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void criticRejectionDoesNotPersistVariant() {
        long problemId = 10L;
        String major = "finance";
        stubFindCachedEmpty(problemId, major);
        stubRolloutBaseline(problemId);
        stubLoadProblem(problemId);
        stubLoadMajor(major);

        Map<String, Object> initial = Map.of("title", "x", "description_md", "y");
        when(aiModelGateway.callForJson(anyString(), anyString(), eq("coding-lens")))
                .thenReturn(initial);
        when(reflectionService.reflectAndRefine(eq(CardType.DOMAIN_VARIANT), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(initial, false, 1, "drift detected"));

        Optional<ProblemDomainVariant> variant = service.findOrGenerate(problemId, major);

        assertThat(variant).isEmpty();
        // 关键合约：critic 不通过 → 不允许写 problem_domain_variant
        verify(jdbcTemplate, never()).update(argThat(sqlContains("insert into problem_domain_variant")),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void criticPassPersistsVariantAndReturnsCachedRow() {
        long problemId = 11L;
        String major = "psychology";
        stubFindCachedEmptyThenReturning(problemId, major);
        stubRolloutBaseline(problemId);
        stubLoadProblem(problemId);
        stubLoadMajor(major);

        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("title", "心理学版面");
        initial.put("description_md", "## 实验背景\n...");
        initial.put("rewritten_sample_input", "1 2");
        initial.put("rewritten_sample_output", "3");
        initial.put("domain_metaphor", Map.of("a", "受试编号", "b", "得分"));
        initial.put("verification", Map.of(
                "input_schema_unchanged", true,
                "semantics_unchanged", true));

        when(aiModelGateway.callForJson(anyString(), anyString(), eq("coding-lens")))
                .thenReturn(initial);
        when(reflectionService.reflectAndRefine(eq(CardType.DOMAIN_VARIANT), any(), any(), eq(1)))
                .thenReturn(new ReflectionResult(initial, true, 1, "ok"));

        Optional<ProblemDomainVariant> variant = service.findOrGenerate(problemId, major);

        assertThat(variant).isPresent();
        verify(jdbcTemplate, times(1)).update(
                argThat(sqlContains("insert into problem_domain_variant")),
                eq(problemId), eq(major), anyString(), anyString(),
                anyString(), anyString(), anyString(), any());
    }

    @Test
    void lockForExamRejectsNonExistentVariantWith404() {
        when(jdbcTemplate.update(argThat(sqlContains("update problem_domain_variant")),
                eq(99L), eq(404L)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.lockForExam(404L, 99L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void invalidateDeletesAllUnlockedVariants() {
        when(jdbcTemplate.update(argThat(sqlContains("delete from problem_domain_variant")), eq(123L)))
                .thenReturn(2);

        service.invalidate(123L);

        verify(jdbcTemplate).update(argThat(sqlContains("delete from problem_domain_variant")), eq(123L));
    }

    // ---------- helpers ----------

    private void stubFindCachedReturning(long problemId, String major) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from problem_domain_variant")
                                .and(sqlContains("where problem_id"))),
                        any(RowMapper.class), eq(problemId), eq(major)))
                .thenAnswer(invocation -> sampleVariant(problemId, major));
    }

    private void stubFindCachedEmpty(long problemId, String major) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from problem_domain_variant")
                                .and(sqlContains("where problem_id"))),
                        any(RowMapper.class), eq(problemId), eq(major)))
                .thenThrow(new EmptyResultDataAccessException(1));
    }

    private void stubFindCachedEmptyThenReturning(long problemId, String major) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from problem_domain_variant")
                                .and(sqlContains("where problem_id"))),
                        any(RowMapper.class), eq(problemId), eq(major)))
                .thenThrow(new EmptyResultDataAccessException(1))
                .thenAnswer(invocation -> sampleVariant(problemId, major));
    }

    private void stubRolloutBaseline(long problemId) {
        lenient().when(rolloutPolicyService.evaluate(
                        eq("coding_lens"), eq("problem:" + problemId), any()))
                .thenReturn(new RolloutDecision("baseline", "default", Map.of()));
    }

    private void stubLoadProblem(long problemId) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from problem")
                                .and(sqlContains("where id = ?"))),
                        any(RowMapper.class), eq(problemId)))
                .thenAnswer(invocation -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", "原题标题");
                    row.put("description", "原题描述");
                    row.put("input_description", "两个整数");
                    row.put("output_description", "和");
                    row.put("samples", "[]");
                    return row;
                });
    }

    private void stubLoadMajor(String major) {
        lenient().when(jdbcTemplate.queryForObject(
                        argThat(sqlContains("from career_major_dictionary")
                                .and(sqlContains("seed_use_cases"))),
                        any(RowMapper.class), eq(major)))
                .thenAnswer(invocation -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", major);
                    row.put("name_zh", major);
                    row.put("discipline", "natural-science");
                    row.put("seed_use_cases", "[]");
                    return row;
                });
    }

    private ProblemDomainVariant sampleVariant(long problemId, String major) {
        return new ProblemDomainVariant(
                1L, problemId, major, "题面", "描述",
                "1 2", "3",
                Map.of("a", "x"),
                0.0, true, false,
                java.time.Instant.now(), null);
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
