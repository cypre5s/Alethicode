package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LanguagePackCoverageBaselineSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pythonBasicBaselineShouldStayAtFiftyOneProblems() {
        Map<String, Object> report = LanguagePackCoverageBaselineSupport.buildCoverageReport(
                objectMapper,
                "python-basic",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0
        );

        assertThat(report.get("baseline_problem_count")).isEqualTo(51);
        assertThat(report.get("final_oj_candidate_count")).isEqualTo(0);
        assertThat(((List<?>) report.get("missing"))).hasSize(51);
        assertThat(((List<?>) report.get("extra"))).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void coverageReportShouldSeparateMatchedMissingAndExtraRows() {
        List<Map<String, Object>> generated = List.of(
                Map.of("chapter_title", "第二章：Python 语言基础", "chapter_index", 2, "source_title", "2.1 圆面积计算", "title", "圆面积计算", "unit_type", "exercise"),
                Map.of("chapter_title", "第七章：归纳与抽象", "chapter_index", 7, "source_title", "7.12 绘图系统", "title", "绘图系统", "unit_type", "assignment"),
                Map.of("chapter_title", "第一章：计算工具与计算思维", "chapter_index", 1, "source_title", "自定义额外题目", "title", "额外题", "unit_type", "demo")
        );

        Map<String, Object> report = LanguagePackCoverageBaselineSupport.buildCoverageReport(
                objectMapper,
                "python-basic",
                generated,
                List.of(
                        Map.ofEntries(
                                Map.entry("chapter_title", "第二章：Python 语言基础"),
                                Map.entry("chapter_index", 2),
                                Map.entry("chapter_page_count", 10),
                                Map.entry("source_title", "2.1 圆面积计算"),
                                Map.entry("page_range_start", 1),
                                Map.entry("page_range_end", 1),
                                Map.entry("oj_convertible", true),
                                Map.entry("stdin_stdout_convertible", true),
                                Map.entry("oj_block_reason", ""),
                                Map.entry("task_signal_score", 2),
                                Map.entry("review_required", false),
                                Map.entry("review_reason", "")
                        ),
                        Map.ofEntries(
                                Map.entry("chapter_title", "第七章：归纳与抽象"),
                                Map.entry("chapter_index", 7),
                                Map.entry("chapter_page_count", 12),
                                Map.entry("source_title", "7.12 绘图系统"),
                                Map.entry("page_range_start", 1),
                                Map.entry("page_range_end", 1),
                                Map.entry("oj_convertible", true),
                                Map.entry("stdin_stdout_convertible", true),
                                Map.entry("oj_block_reason", ""),
                                Map.entry("task_signal_score", 2),
                                Map.entry("review_required", false),
                                Map.entry("review_reason", "")
                        ),
                        Map.ofEntries(
                                Map.entry("chapter_title", "第四章：流程自动化"),
                                Map.entry("chapter_index", 4),
                                Map.entry("chapter_page_count", 12),
                                Map.entry("source_title", "循环练习"),
                                Map.entry("page_range_start", 2),
                                Map.entry("page_range_end", 3),
                                Map.entry("oj_convertible", false),
                                Map.entry("stdin_stdout_convertible", true),
                                Map.entry("oj_block_reason", "insufficient_task_goal"),
                                Map.entry("task_signal_score", 1),
                                Map.entry("review_required", true),
                                Map.entry("review_reason", "generic_title_requires_review")
                        )
                ),
                List.of(
                        Map.of("chapter_title", "第二章：Python 语言基础", "chapter_index", 2, "chapter_page_count", 10),
                        Map.of("chapter_title", "第四章：流程自动化", "chapter_index", 4, "chapter_page_count", 12),
                        Map.of("chapter_title", "第七章：归纳与抽象", "chapter_index", 7, "chapter_page_count", 12),
                        Map.of("chapter_title", "第六章：函数", "chapter_index", 6, "chapter_page_count", 9)
                ),
                List.of(
                        Map.of("chapter_index", 2, "chapter_title", "第二章：Python 语言基础", "kc_alias_merge_count", 1, "cross_batch_merged_kc_count", 2, "conflict_count", 0),
                        Map.of("chapter_index", 4, "chapter_title", "第四章：流程自动化", "kc_alias_merge_count", 2, "cross_batch_merged_kc_count", 1, "conflict_count", 1)
                ),
                3
        );

        assertThat(report.get("baseline_problem_count")).isEqualTo(51);
        assertThat(report.get("generated_problem_count")).isEqualTo(3);
        assertThat(report.get("matched_count")).isEqualTo(2);
        assertThat(report.get("final_oj_candidate_count")).isEqualTo(2);
        assertThat((List<Map<String, Object>>) report.get("missing")).hasSize(49);
        assertThat((List<Map<String, Object>>) report.get("extra"))
                .extracting(row -> row.get("source_title"))
                .containsExactly("自定义额外题目");
        assertThat((List<Map<String, Object>>) report.get("blocked_candidates"))
                .extracting(row -> row.get("source_title"))
                .containsExactly("循环练习");
        assertThat((List<Map<String, Object>>) report.get("unresolved_review_required"))
                .extracting(row -> row.get("source_title"))
                .containsExactly("循环练习");
        assertThat((List<Map<String, Object>>) report.get("high_risk_chapters"))
                .extracting(row -> row.get("chapter_title"))
                .contains("第四章：流程自动化")
                .doesNotContain("第六章：函数");
        assertThat((List<Map<String, Object>>) report.get("chapter_stats"))
                .filteredOn(row -> "第四章：流程自动化".equals(row.get("chapter_title")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("convertible_unit_count")).isEqualTo(1);
                    assertThat(row.get("non_convertible_unit_count")).isEqualTo(0);
                    assertThat(row.get("chapter_has_task_signal")).isEqualTo(true);
                    assertThat((Map<String, Integer>) row.get("blocked_by_reason")).containsEntry("insufficient_task_goal", Integer.valueOf(1));
                });
        assertThat(report.get("kc_alias_merge_count")).isEqualTo(3);
        assertThat(report.get("cross_batch_merged_kc_count")).isEqualTo(3);
        assertThat(report.get("resume_reused_batch_count")).isEqualTo(3);
        assertThat(report.get("chapter_memory_conflict_count")).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void coverageReportShouldMatchBaselineByChapterTitleWhenDocumentOrderDiffers() {
        Map<String, Object> report = LanguagePackCoverageBaselineSupport.buildCoverageReport(
                objectMapper,
                "python-basic",
                List.of(
                        Map.of(
                                "chapter_title", "第四章：流程自动化",
                                "chapter_index", 7,
                                "source_title", "举例：自然数之和",
                                "title", "自然数之和",
                                "unit_type", "exercise"
                        )
                ),
                List.of(
                        Map.ofEntries(
                                Map.entry("chapter_title", "第一章：计算工具与计算思维"),
                                Map.entry("chapter_index", 1),
                                Map.entry("chapter_page_count", 45),
                                Map.entry("source_title", "课程导入"),
                                Map.entry("page_range_start", 1),
                                Map.entry("page_range_end", 2),
                                Map.entry("oj_convertible", false),
                                Map.entry("stdin_stdout_convertible", false),
                                Map.entry("oj_block_reason", "concept_only"),
                                Map.entry("task_signal_score", 0),
                                Map.entry("review_required", false),
                                Map.entry("review_reason", "")
                        ),
                        Map.ofEntries(
                                Map.entry("chapter_title", "第四章：流程自动化"),
                                Map.entry("chapter_index", 7),
                                Map.entry("chapter_page_count", 74),
                                Map.entry("source_title", "举例：自然数之和"),
                                Map.entry("page_range_start", 52),
                                Map.entry("page_range_end", 52),
                                Map.entry("oj_convertible", true),
                                Map.entry("stdin_stdout_convertible", true),
                                Map.entry("oj_block_reason", ""),
                                Map.entry("task_signal_score", 2),
                                Map.entry("review_required", false),
                                Map.entry("review_reason", "")
                        )
                ),
                List.of(
                        Map.of("chapter_title", "第一章：计算工具与计算思维", "chapter_index", 1, "chapter_page_count", 45),
                        Map.of("chapter_title", "第四章：流程自动化", "chapter_index", 7, "chapter_page_count", 74)
                ),
                List.of(),
                0
        );

        assertThat((List<Map<String, Object>>) report.get("missing"))
                .extracting(row -> row.get("title"))
                .doesNotContain("4.14 自然数之和");
        assertThat((List<Map<String, Object>>) report.get("high_risk_chapters"))
                .extracting(row -> row.get("chapter_title"))
                .doesNotContain("第一章：计算工具与计算思维");
    }
}
