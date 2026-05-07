package com.alethicode.service.aitutor.transfer;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.impl.JudgeCheckResult;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 LLM 生成迁移变式题，并用 Judge 执行参考答案进行校验。
 *
 * 只有通过真实判题校验的题目才会持久化。
 */
@Service
public class TransferVerifierService {

    private static final Logger log = LoggerFactory.getLogger(TransferVerifierService.class);
    private static final int MAX_RETRIES = 2;

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;
    private final LanguagePackProblemJudgeCheckService judgeCheckService;

    public TransferVerifierService(JdbcTemplate jdbcTemplate,
                                    AiModelGateway aiModelGateway,
                                    LanguagePackProblemJudgeCheckService judgeCheckService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
        this.judgeCheckService = judgeCheckService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateAndVerify(Long sourceProblemId, Long languagePackId) {
        Map<String, Object> sourceProblem = jdbcTemplate.queryForMap(
                "SELECT id, title, description, input_description, output_description FROM problem WHERE id = ?",
                sourceProblemId);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            Map<String, Object> generated = generateVariantViaLlm(sourceProblem, attempt);
            if (generated == null) continue;

            String referenceCode = (String) generated.get("reference_solution");
            List<Map<String, Object>> testInputs = (List<Map<String, Object>>) generated.get("test_cases");
            if (referenceCode == null || referenceCode.isBlank() || testInputs == null || testInputs.isEmpty()) {
                log.warn("LLM returned incomplete variant on attempt {}", attempt);
                continue;
            }

            List<String> inputs = testInputs.stream()
                    .map(tc -> (String) tc.get("input"))
                    .toList();

            try {
                JudgeCheckResult judgeResult = judgeCheckService.executeReferenceSolution(
                        referenceCode, "Python3", inputs, 3000, 256);

                if (judgeResult.allPassed()) {
                    Long newProblemId = persistVariantProblem(generated, sourceProblemId, languagePackId, judgeResult, testInputs);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "verified");
                    result.put("source_problem_id", sourceProblemId);
                    result.put("new_problem_id", newProblemId);
                    result.put("title", generated.get("title"));
                    result.put("attempts", attempt + 1);
                    return result;
                } else {
                    log.info("Variant judge verification failed on attempt {}: {} of {} cases failed",
                            attempt, judgeResult.caseResults().stream().filter(c -> !c.passed()).count(),
                            judgeResult.caseResults().size());
                }
            } catch (Exception e) {
                log.warn("Judge execution failed on attempt {}: {}", attempt, e.getMessage());
            }
        }

        return Map.of("status", "failed", "source_problem_id", sourceProblemId,
                "message", "All attempts failed to produce a verified variant");
    }

    private Map<String, Object> generateVariantViaLlm(Map<String, Object> source, int attempt) {
        String systemPrompt = """
                你是编程教育出题专家。基于原题生成一道变式题，要求：
                1. 核心考点相同，但场景/数据不同
                2. 必须附带 Python3 标准答案代码
                3. 必须附带至少 3 组测试用例（含边界情况）
                
                输出 JSON：
                {
                  "title": "变式题标题",
                  "description": "题面描述",
                  "input_description": "输入格式说明",
                  "output_description": "输出格式说明",
                  "reference_solution": "Python3 标准答案代码（可直接运行）",
                  "test_cases": [
                    {"input": "输入数据", "expected_output": "期望输出"}
                  ],
                  "target_kcs": ["涉及的知识点"]
                }
                """;

        String userPrompt = "原题标题：%s\n原题描述：%s\n这是第 %d 次尝试。".formatted(
                source.get("title"), abbreviate((String) source.get("description"), 1500), attempt + 1);

        try {
            return aiModelGateway.callForJson(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("LLM variant generation failed: {}", e.getMessage());
            return null;
        }
    }

    private Long persistVariantProblem(Map<String, Object> generated, Long sourceProblemId,
                                       Long languagePackId, JudgeCheckResult judgeResult,
                                       List<Map<String, Object>> testCases) {
        Long newId = jdbcTemplate.queryForObject("""
            INSERT INTO problem (title, description, input_description, output_description,
                                 difficulty, visible, auto_generated, created_by_id)
            VALUES (?, ?, ?, ?, 'Low', true, true,
                    (SELECT created_by_id FROM problem WHERE id = ?))
            RETURNING id
            """, Long.class,
                generated.get("title"),
                generated.get("description"),
                generated.get("input_description"),
                generated.get("output_description"),
                sourceProblemId);

        if (languagePackId != null && newId != null) {
            jdbcTemplate.update("""
                INSERT INTO language_pack_problem_mapping (language_pack_id, problem_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, languagePackId, newId);
        }

        log.info("Persisted verified variant problem: id={}, title={}, source={}",
                newId, generated.get("title"), sourceProblemId);
        return newId;
    }

    private String abbreviate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
