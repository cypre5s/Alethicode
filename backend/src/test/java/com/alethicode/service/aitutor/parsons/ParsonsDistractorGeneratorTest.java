package com.alethicode.service.aitutor.parsons;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ParsonsDistractorGenerator} 单元测试 — 覆盖设计稿
 * ALETH-PLAN-2026-0427-FP01 附录 B.1 的 7 个 distractor 生成场景。
 *
 * <p><b>历史背景</b>：4/28 落地 Parsons 时漏写了本测试类，导致
 * `pickFromNotebook` 的 SQL 里 `kc_ids ?| ARRAY[...]` 与 JDBC `?` 占位符的
 * 冲突 bug 一路藏到 5/3 真实 dispatch 才暴露（`No value specified for
 * parameter 3`）。本类先用 mock JdbcTemplate 覆盖 7 个业务行为分支并加
 * SQL 形态合约断言（必须使用 `jsonb_exists_any` 函数形式而非
 * `?|` 操作符）；SQL 在真实 PostgreSQL 的可解析性由
 * {@link ParsonsDistractorGeneratorSqlSmokeTest} 走 5436 dev DB 的 read-only
 * EXPLAIN 路径担保，不在本类职责范围内。</p>
 *
 * <p><b>关于"脱敏"</b>：设计稿 §10.3 所述的 RBAC 与脱敏在上游
 * `LanguagePackQaService` 强制（仅当前用户自己的 notebook 才会被抽取），
 * 当前 Generator 层并未实现额外脱敏逻辑；待未来扩展跨学生 misconception
 * 聚合时再补对应的脱敏单测，本类不抢这一职责。</p>
 */
@ExtendWith(MockitoExtension.class)
class ParsonsDistractorGeneratorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AiModelGateway aiModelGateway;

    private ObjectMapper objectMapper;
    private ParsonsProperties properties;
    private ParsonsDistractorGenerator generator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new ParsonsProperties();
        generator = new ParsonsDistractorGenerator(jdbcTemplate, aiModelGateway, objectMapper, properties);
    }

    @Test
    void notebookFullyCoversTargetCountAndDoesNotInvokeLlm() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                notebookRow(1L, "for j in range(0):", "for 循环边界写错"),
                notebookRow(2L, "for j in range(1):", "off-by-one"),
                notebookRow(3L, "for j in range(2):", "off-by-two")
        ));

        List<ParsonsDistractor> result = generator.generate(ctx(7L, List.of(101L), 2, refBlocks()));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(d -> d.source() == ParsonsDistractor.Source.NOTEBOOK);
        verify(aiModelGateway, never()).callForJson(anyString(), anyString());
    }

    @Test
    void triggersLlmFallbackWhenNotebookSupplyInsufficient() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                notebookRow(1L, "x = input()", "类型转换错")
        ));
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of(
                "distractors", List.of(
                        Map.of("code", "y = float(input())", "indent", 0, "kc_hint", "类型转换"),
                        Map.of("code", "z = eval(input())", "indent", 0, "kc_hint", "eval 误用")
                )
        ));

        List<ParsonsDistractor> result = generator.generate(ctx(7L, List.of(202L), 3, refBlocks()));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ParsonsDistractor::source)
                .contains(ParsonsDistractor.Source.NOTEBOOK, ParsonsDistractor.Source.LLM);
        verify(aiModelGateway, times(1)).callForJson(anyString(), anyString());
    }

    @Test
    void retriesLlmUpToMaxRetriesAndReturnsWhatWasGathered() {
        // 默认 maxLlmRetries=2 → 1 次首调 + 2 次重试 = 3 次总调用
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenThrow(new RuntimeException("simulated LLM outage"));

        List<ParsonsDistractor> result = generator.generate(ctx(7L, List.of(303L), 2, refBlocks()));

        assertThat(result).isEmpty();
        verify(aiModelGateway, times(3)).callForJson(anyString(), anyString());
    }

    @Test
    void filtersDistractorsTooSimilarToReferenceBlocks() {
        // row 1 = reference 完全一致 → 必被 LCS 过滤；
        // row 2 = 结构性完全不同（控制流而非表达式）→ 必被保留
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                notebookRow(1L, "print(a + b)", "完全一致 — 应被 LCS 过滤丢弃"),
                notebookRow(2L, "for i in range(10):", "结构性差异巨大 — 应被保留")
        ));
        when(aiModelGateway.callForJson(anyString(), anyString())).thenReturn(Map.of());

        List<ParsonsBlock> references = List.of(
                new ParsonsBlock("B0", "print(a + b)", 0, ParsonsBlock.FadingState.VISIBLE, null)
        );
        List<ParsonsDistractor> result = generator.generate(ctx(7L, List.of(404L), 2, references));

        assertThat(result).extracting(ParsonsDistractor::code)
                .doesNotContain("print(a + b)")
                .contains("for i in range(10):");
    }

    @Test
    void multipleKcIdsAreEncodedIntoSqlInListLiteralCorrectly() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                notebookRow(1L, "for i in range(n):", "KC100 错"),
                notebookRow(2L, "if x = 1:", "KC200 错")
        ));

        List<ParsonsDistractor> result = generator.generate(ctx(7L, List.of(100L, 200L), 5, refBlocks()));

        assertThat(result).hasSize(2);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), argsCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("ARRAY['100','200']::text[]");
        assertThat(argsCaptor.getValue()).hasSize(2);
        assertThat(argsCaptor.getValue()[0]).isEqualTo(7L);
        assertThat(argsCaptor.getValue()[1]).isInstanceOf(Long[].class);
        assertThat((Long[]) argsCaptor.getValue()[1]).containsExactly(100L, 200L);
    }

    @Test
    void enforcesTargetCountUpperBoundEvenWithAbundantNotebookSupply() {
        List<Map<String, Object>> abundantRows = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            abundantRows.add(notebookRow(i, "x" + i + " = " + i, "丰富错题 " + i));
        }
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(abundantRows);

        List<ParsonsDistractor> result = generator.generate(ctx(7L, List.of(505L), 3, refBlocks()));

        assertThat(result).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void emptyKcIdsBypassesNotebookSqlButLlmFallbackPathIsIndependent() {
        // 当题目无任何 KC 关联时，pickFromNotebook 早退不发 SQL（避免拼一条
        // ARRAY[] 这种边界 SQL 给 PG），但 generate() 主流程仍会按 targetCount
        // 走 LLM 兜底；这里只断言"SQL 不被调"这一边界行为，LLM 调与不调是
        // distractor 数量决策的责任，不归 kcIds 是否空管。
        when(aiModelGateway.callForJson(anyString(), anyString()))
                .thenReturn(Map.of("distractors", List.of(
                        Map.of("code", "while True: pass", "indent", 0, "kc_hint", "无 KC fallback"))));

        List<ParsonsDistractor> result = generator.generate(ctx(7L, List.of(), 1, refBlocks()));

        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
        assertThat(result).extracting(ParsonsDistractor::source)
                .allMatch(s -> s == ParsonsDistractor.Source.LLM);
    }

    /**
     * <b>SQL 形态合约</b>：守住 5/3 修复后的 SQL 不被回退到 `?|` 操作符。
     * 不验证 SQL 真实可执行（那是 {@link ParsonsDistractorGeneratorSqlSmokeTest}
     * 的职责），只验证字符串模式：
     * <ul>
     *   <li>使用 PostgreSQL 内置函数 `jsonb_exists_any(kc_ids, ARRAY[...]::text[])`</li>
     *   <li>不再含 `kc_ids ?|` 这种与 JDBC 占位符冲突的形式</li>
     *   <li>占位符 `?` 数量与 buildArgs 长度对齐（避免回退到 3 个 ? 但只 bind 2 个的 bug）</li>
     * </ul>
     */
    @Test
    void sqlMustUseJsonbExistsAnyFunctionFormToAvoidJdbcPlaceholderConflict() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        generator.generate(ctx(7L, List.of(919L), 1, refBlocks()));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), argsCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql)
                .as("SQL 必须用 jsonb_exists_any 函数形式，不能用 ?| 操作符")
                .contains("jsonb_exists_any(kc_ids,")
                .doesNotContain("kc_ids ?|");

        long placeholderCount = sql.chars().filter(c -> c == '?').count();
        assertThat(placeholderCount)
                .as("SQL 中字面 ? 的数量必须等于 buildArgs 长度，否则 pgjdbc 报 No value specified for parameter")
                .isEqualTo(argsCaptor.getValue().length);
    }

    // ---- helpers ----

    private static Map<String, Object> notebookRow(long id, String snippetCode, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("root_cause", description + "：\n```python\n" + snippetCode + "\n```");
        row.put("misconception_distribution", "{}");
        row.put("evidence_ptr", "{}");
        return row;
    }

    private static List<ParsonsBlock> refBlocks() {
        return List.of(
                new ParsonsBlock("B0", "a = int(input())", 0, ParsonsBlock.FadingState.VISIBLE, null),
                new ParsonsBlock("B1", "b = int(input())", 0, ParsonsBlock.FadingState.VISIBLE, null),
                new ParsonsBlock("B2", "print(a + b)", 0, ParsonsBlock.FadingState.VISIBLE, null)
        );
    }

    private static ParsonsDistractorGenerator.GenerationContext ctx(
            long uid, List<Long> kcIds, int targetCount, List<ParsonsBlock> referenceBlocks) {
        return new ParsonsDistractorGenerator.GenerationContext(
                uid, 999L, "测试题", "Python3", "Python 3",
                kcIds, kcIds.stream().map(id -> "KC#" + id).toList(),
                referenceBlocks, targetCount);
    }
}
