package com.alethicode.service.aitutor.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.ai.AiModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 特化题生成专项服务（Phase 3 拆分自 ErrorReviewPackageService）。
 *
 * 职责：
 *  - {@link #generateSpecializedProblems(Long, String, List, List, int)} 批量生成 N 道（包创建路径）
 *  - {@link #generateOne(Long, String, List, List)} 单题生成（学生「再练一题」即时触发）
 *  - {@link #scheduleSpecializedProblemGeneration} 异步包级触发，与 createPackage 解耦
 *  - {@link #appendOneToPackage} 把单题挂到包，供 ReviewProblemRatingService 使用
 */
@Service
@Lazy
public class SpecializedProblemGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpecializedProblemGenerator.class);

    static final int DEFAULT_BATCH_COUNT = 3;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiModelGateway aiModelGateway;
    private final AiProblemTestCaseWriter testCaseWriter;
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SpecializedProblemGenerator(JdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper,
                                       AiModelGateway aiModelGateway,
                                       AiProblemTestCaseWriter testCaseWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiModelGateway = aiModelGateway;
        this.testCaseWriter = testCaseWriter;
    }

    /** 批量出题。失败的题目静默跳过；调用方根据返回长度决定后续 sequence。 */
    public List<Long> generateSpecializedProblems(Long userId, String errorTaxonomy,
                                                  List<String> rootCauses, List<Long> sourceProblemIds,
                                                  int batchCount) {
        List<String> sourceSummaries = loadProblemSummaries(sourceProblemIds);
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < batchCount; i++) {
            try {
                Long aiProblemId = generateOneProblem(userId, errorTaxonomy, rootCauses, sourceSummaries, i + 1);
                result.add(aiProblemId);
            } catch (Exception e) {
                log.warn("AI特化题生成失败 (index={}, taxonomy={}): {}", i, errorTaxonomy, e.getMessage());
            }
        }
        return result;
    }

    /** 单题入口：学生「再练一题」时同步出 1 道，失败抛 IllegalStateException。 */
    public Long generateOne(Long userId, String errorTaxonomy,
                            List<String> rootCauses, List<Long> sourceProblemIds) {
        List<String> sourceSummaries = loadProblemSummaries(sourceProblemIds);
        return generateOneProblem(userId, errorTaxonomy, rootCauses, sourceSummaries, 1);
    }

    /** 异步触发：包创建时立即返回，AI 题在后台落库 + append + 增加 problem_count。 */
    public void scheduleSpecializedProblemGeneration(String packageId,
                                                     Long userId,
                                                     String errorTaxonomy,
                                                     List<String> rootCauses,
                                                     List<Long> sourceProblemIds,
                                                     int startSequence) {
        asyncExecutor.submit(() -> {
            try {
                List<Long> aiProblemIds = generateSpecializedProblems(userId, errorTaxonomy, rootCauses, sourceProblemIds, DEFAULT_BATCH_COUNT);
                if (aiProblemIds.isEmpty()) return;
                int sequence = startSequence;
                for (Long aiProblemId : aiProblemIds) {
                    sequence++;
                    appendOneToPackage(packageId, aiProblemId, sequence);
                }
                jdbcTemplate.update(
                        "update ai_error_review_package set problem_count = problem_count + ?, updated_at = now() where id = ?",
                        aiProblemIds.size(), packageId
                );
            } catch (Exception exception) {
                log.warn("异步生成强化训练 AI 特化题失败 (packageId={}, taxonomy={}): {}", packageId, errorTaxonomy, exception.getMessage());
            }
        });
    }

    /** 把已生成的 AI 题挂到 package（sequence 已由调用方计算好）。 */
    public void appendOneToPackage(String packageId, Long aiProblemId, int sequence) {
        jdbcTemplate.update(
                "update problem set ai_source_review_package_id = ? where id = ?",
                packageId, aiProblemId
        );
        jdbcTemplate.update(
                """
                insert into ai_error_review_problem(id, package_id, problem_id, sequence, submitted, is_correct, is_ai_generated, created_at)
                values (?, ?, ?, ?, false, null, true, now())
                """,
                randomId(), packageId, aiProblemId, sequence
        );
    }

    private Long generateOneProblem(Long userId, String errorTaxonomy,
                                    List<String> rootCauses, List<String> sourceSummaries, int index) {
        String systemPrompt = "你是一名面向非计算机专业编程初学者的教学出题老师。\n"
                + "根据学生的错误模式生成针对性练习编程题。\n"
                + "返回严格 JSON，字段：title, description, input_description, output_description, "
                + "samples (数组，每项含 input 和 output), test_cases (数组，每项含 input 和 output), "
                + "reference_solution_code (Python3), difficulty (Low/Mid/High)。\n"
                + "题目必须是标准 stdin/stdout 编程题，难度适合初学者。";

        String userPrompt = "error_taxonomy: " + errorTaxonomy + "\n"
                + "root_causes:\n" + String.join("\n", rootCauses) + "\n"
                + "source_problems:\n" + String.join("\n---\n", sourceSummaries) + "\n"
                + "请生成第 " + index + " 道针对该错误模式的编程题，考查同类知识点但题面不同。";

        Map<String, Object> llmResult = aiModelGateway.callForJson(systemPrompt, userPrompt);

        String title = stringVal(llmResult.get("title"));
        String description = stringVal(llmResult.get("description"));
        String inputDescription = stringVal(llmResult.get("input_description"));
        String outputDescription = stringVal(llmResult.get("output_description"));
        String referenceSolutionCode = stringVal(llmResult.get("reference_solution_code"));
        String difficulty = stringVal(llmResult.get("difficulty"));
        if (difficulty.isEmpty()) difficulty = "Low";

        Object samplesRaw = llmResult.get("samples");
        Object testCasesRaw = llmResult.get("test_cases");
        String samplesJson = toJson(samplesRaw != null ? samplesRaw : List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> testCases = testCasesRaw instanceof List<?>
                ? (List<Map<String, Object>>) testCasesRaw
                : List.of();
        if (testCases.isEmpty()) {
            throw new IllegalStateException("LLM 未返回有效 test_cases");
        }

        String displayId = "AIRP-" + randomId().substring(0, 8).toUpperCase(Locale.ROOT);
        String testCaseId = "airp_" + randomId().substring(0, 12).toLowerCase(Locale.ROOT);
        testCaseWriter.writeTestCases(testCaseId, testCases);
        String testCaseScoreJson = testCaseWriter.buildTestCaseScoreJson(testCases.size());

        Long newProblemId = jdbcTemplate.queryForObject(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    reference_solution_language, reference_solution_code,
                    visible, is_public, difficulty, source, statistic_info,
                    is_ai_generated, visibility_status, create_time, last_update_time
                ) values (
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, cast(? as jsonb), '',
                    cast('["Python3"]' as jsonb), cast('{}' as jsonb), ?, 3000, 256,
                    'Python3', ?,
                    false, false, ?, 'AI-Review Specialized', cast('{}' as jsonb),
                    true, 'student_private', now(), now()
                ) returning id
                """,
                Long.class,
                displayId, title, description, inputDescription, outputDescription,
                samplesJson, testCaseId, testCaseScoreJson,
                userId, referenceSolutionCode, difficulty
        );
        if (newProblemId == null) {
            throw new IllegalStateException("AI特化题 insert 失败");
        }
        return newProblemId;
    }

    private List<String> loadProblemSummaries(List<Long> problemIds) {
        List<String> summaries = new ArrayList<>();
        if (problemIds == null) return summaries;
        for (Long pid : problemIds) {
            Map<String, Object> info = jdbcTemplate.query(
                    "select title, description from problem where id = ?",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("title", rs.getString("title"));
                        row.put("description", rs.getString("description"));
                        return row;
                    },
                    pid
            ).stream().findFirst().orElse(Map.of());
            String title = stringVal(info.get("title"));
            String desc = stringVal(info.get("description"));
            if (desc.length() > 300) desc = desc.substring(0, 300) + "...";
            summaries.add("题目: " + title + "\n" + desc);
        }
        return summaries;
    }

    private String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
