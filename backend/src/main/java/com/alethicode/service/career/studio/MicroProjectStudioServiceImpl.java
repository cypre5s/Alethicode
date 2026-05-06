package com.alethicode.service.career.studio;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.profile.MasteryService;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.review.AiProblemTestCaseWriter;
import com.alethicode.service.career.bridging.CareerBridgingService;
import com.alethicode.service.career.bridging.MilestoneType;
import com.alethicode.service.languagepack.impl.JudgeCheckResult;
import com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain-Aware Project Studio 主实现（plan 5.1 节 + todo 11）。
 *
 * <p>关键流程（plan 5.1 节强约束）：
 * <ol>
 *   <li>LLM 出题（{@link MicroProjectPrompts#SYSTEM} + 学生 mastery 上下文）；</li>
 *   <li>{@link ReflectionService} critic（{@link CardType#MICRO_PROJECT_BRIEF}）：
 *       专业相关性 + KC ⊆ mastered_kcs + Python 标准库 + 测试样例 ≥ 5 含边界与反例；</li>
 *   <li><strong>reference_solution 真判题自验证</strong>：调
 *       {@link LanguagePackProblemJudgeCheckService#executeReferenceSolution}
 *       走 Judge Server 沙箱跑 reference 100% AC 自身 test_cases，否则 abort 不落库；</li>
 *   <li>真判题通过 ⇒ 落 {@code problem} 表（学生提交时走标准 SubmissionService）+
 *       落 {@code career_micro_project} 含 {@code judge_problem_id}。</li>
 * </ol>
 *
 * <p>不绕过 Judge Server：plan 0 节强约束「判题不动 / Judge Server 协议不动 /
 * IO schema 不动」严格遵守；本实现只复用现有 {@link LanguagePackProblemJudgeCheckService}
 * 的 {@code /judge} 调用与 {@link AiProblemTestCaseWriter} 的 test_case 落盘工具，
 * 未引入新的判题路径。
 */
@Service
public class MicroProjectStudioServiceImpl implements MicroProjectStudioService {

    private static final Logger log = LoggerFactory.getLogger(MicroProjectStudioServiceImpl.class);
    private static final double MASTERY_RECOMMEND_THRESHOLD = 0.5;
    private static final int DEFAULT_TIME_LIMIT_MS = 3000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final String REFERENCE_LANGUAGE = "Python3";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiModelGateway aiModelGateway;
    private final ReflectionService reflectionService;
    private final MasteryService masteryService;
    private final CareerBridgingService careerBridgingService;
    private final AiProblemTestCaseWriter testCaseWriter;
    private final LanguagePackProblemJudgeCheckService judgeCheckService;

    public MicroProjectStudioServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiModelGateway aiModelGateway,
            ReflectionService reflectionService,
            MasteryService masteryService,
            CareerBridgingService careerBridgingService,
            AiProblemTestCaseWriter testCaseWriter,
            LanguagePackProblemJudgeCheckService judgeCheckService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiModelGateway = aiModelGateway;
        this.reflectionService = reflectionService;
        this.masteryService = masteryService;
        this.careerBridgingService = careerBridgingService;
        this.testCaseWriter = testCaseWriter;
        this.judgeCheckService = judgeCheckService;
    }

    @Override
    public List<MicroProjectRecommendation> recommendForUser(long userId) {
        Map<String, Double> mastery = masteryService.projectMasteryByLanguagePack(userId, null);
        List<String> masteredKcs = mastery.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= MASTERY_RECOMMEND_THRESHOLD)
                .map(Map.Entry::getKey)
                .toList();
        if (masteredKcs.isEmpty()) {
            return List.of();
        }
        int size = Math.min(masteredKcs.size(), 5);
        return List.of(new MicroProjectRecommendation(
                masteredKcs.subList(0, size),
                "基于你已掌握的 KC 推荐"
        ));
    }

    @Override
    @Transactional
    public Optional<CareerMicroProject> generate(long userId, String majorCode, List<String> kcCodes) {
        Map<String, Object> majorRow = loadMajorRow(majorCode);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("major_dictionary", majorRow);
        evidence.put("mastered_kcs", kcCodes);
        evidence.put("user_id", userId);

        String userPrompt = buildUserPrompt(majorRow, kcCodes);
        Map<String, Object> initialOutput = aiModelGateway.callForJson(
                MicroProjectPrompts.SYSTEM, userPrompt, "micro-project");

        ReflectionResult reflection = reflectionService.reflectAndRefine(
                CardType.MICRO_PROJECT_BRIEF, evidence, initialOutput, 1);

        if (!reflection.passed()) {
            log.warn("micro project critic rejected: user={}, major={}, verdict={}",
                    userId, majorCode, reflection.criticVerdict());
            return Optional.empty();
        }

        Map<String, Object> output = reflection.output();
        Map<String, Object> problemSchema = asMap(output.get("problem"));
        Map<String, Object> referenceSchema = asMap(output.get("reference_solution"));
        List<Map<String, Object>> testCases = extractTestCases(problemSchema);
        String referenceCode = stringOf(referenceSchema.get("code"));

        if (referenceCode.isBlank() || testCases.isEmpty()) {
            log.warn("micro project missing reference_solution.code or test_cases: user={}, major={}",
                    userId, majorCode);
            return Optional.empty();
        }

        if (!verifyReferenceSolution(userId, majorCode, referenceCode, testCases)) {
            return Optional.empty();
        }

        return Optional.of(persistProject(userId, majorCode, kcCodes, problemSchema, referenceCode, testCases));
    }

    @Override
    public List<CareerMicroProject> listForUser(long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return jdbcTemplate.query("""
                select id, user_id, major_code, title, brief_md, judge_problem_id,
                       status, score, created_at, completed_at
                from career_micro_project
                where user_id = ?
                order by created_at desc
                limit ?
                """, (rs, rowNum) -> mapRow(rs), userId, safeLimit);
    }

    @Override
    public Optional<CareerMicroProject> findById(long userId, long projectId) {
        try {
            CareerMicroProject project = jdbcTemplate.queryForObject("""
                    select id, user_id, major_code, title, brief_md, judge_problem_id,
                           status, score, created_at, completed_at
                    from career_micro_project
                    where id = ? and user_id = ?
                    """, (rs, rowNum) -> mapRow(rs), projectId, userId);
            return Optional.ofNullable(project);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void markCompleted(long projectId, double score) {
        int updated = jdbcTemplate.update("""
                update career_micro_project
                set status = 'passed', score = ?, completed_at = now()
                where id = ? and status != 'passed'
                """, (int) score, projectId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "project not found or already completed: id=" + projectId);
        }
        try {
            Long userId = jdbcTemplate.queryForObject(
                    "select user_id from career_micro_project where id = ?",
                    Long.class, projectId);
            if (userId != null) {
                careerBridgingService.recordMilestone(
                        userId, MilestoneType.PROJECT_COMPLETED,
                        "project:" + projectId);
            }
        } catch (EmptyResultDataAccessException ignored) { }
        log.info("micro project completed: id={}, score={}", projectId, score);
    }

    /**
     * 走 Judge Server 跑 reference solution，验证 100% AC 自身 test_cases。
     * 任一 case 不通过即视为出题失败，调用方应丢弃本次生成（plan 5.1 节强约束）。
     */
    private boolean verifyReferenceSolution(long userId, String majorCode,
                                            String referenceCode, List<Map<String, Object>> testCases) {
        List<String> inputs = testCases.stream()
                .map(tc -> stringOf(tc.get("input")))
                .toList();
        try {
            JudgeCheckResult result = judgeCheckService.executeReferenceSolution(
                    referenceCode, REFERENCE_LANGUAGE, inputs,
                    DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_MB);
            if (!result.allPassed()) {
                log.warn("micro project reference failed self-validation: user={}, major={}, failed_cases={}",
                        userId, majorCode, result.failedIndices());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // Judge Server 不可用 / 编译失败 / 沙箱异常 ⇒ 不落库；上抛会污染整个 generate 主链路
            log.warn("micro project reference verification raised: user={}, major={}, reason={}",
                    userId, majorCode, e.toString());
            return false;
        }
    }

    private CareerMicroProject persistProject(long userId, String majorCode, List<String> kcCodes,
                                              Map<String, Object> problemSchema,
                                              String referenceCode,
                                              List<Map<String, Object>> testCases) {
        String title = truncate(stringOf(problemSchema.getOrDefault("title", "微项目")), 255);
        String briefMd = stringOf(problemSchema.getOrDefault("description_md", ""));
        String inputDescription = stringOf(problemSchema.getOrDefault("input_description", ""));
        String outputDescription = stringOf(problemSchema.getOrDefault("output_description", ""));
        String sampleInput = stringOf(problemSchema.getOrDefault("sample_input", ""));
        String sampleOutput = stringOf(problemSchema.getOrDefault("sample_output", ""));
        String kcJson = serializeKcs(kcCodes);
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        long judgeProblemId = persistJudgeProblem(
                userId, title, briefMd, inputDescription, outputDescription,
                sampleInput, sampleOutput, referenceCode, testCases);

        long projectId = persistMicroProjectRow(
                userId, majorCode, title, briefMd, kcJson, judgeProblemId, traceId);

        log.info("micro project generated: id={}, judge_problem={}, user={}, major={}, trace={}",
                projectId, judgeProblemId, userId, majorCode, traceId);
        return new CareerMicroProject(projectId, userId, majorCode, title, briefMd, judgeProblemId,
                "recommended", null, Instant.now(), null);
    }

    private CareerMicroProject mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Object judgeRaw = rs.getObject("judge_problem_id");
        Long judgeProblemId = judgeRaw == null ? null : ((Number) judgeRaw).longValue();
        Object scoreRaw = rs.getObject("score");
        Integer score = scoreRaw == null ? null : ((Number) scoreRaw).intValue();
        return new CareerMicroProject(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("major_code"),
                rs.getString("title"),
                rs.getString("brief_md"),
                judgeProblemId,
                rs.getString("status"),
                score,
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("completed_at"))
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /**
     * 把 Studio 题目落到正常 {@code problem} 表（与 SpecializedProblemGenerator 同源），
     * 学生提交时走标准 SubmissionService → Judge Server，不绕开判题主链路。
     */
    private long persistJudgeProblem(long userId, String title, String description,
                                     String inputDescription, String outputDescription,
                                     String sampleInput, String sampleOutput,
                                     String referenceCode, List<Map<String, Object>> testCases) {
        String displayId = "MPRJ-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
        String testCaseId = "mprj_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        testCaseWriter.writeTestCases(testCaseId, testCases);
        String testCaseScoreJson = testCaseWriter.buildTestCaseScoreJson(testCases.size());
        String samplesJson = serializeSamples(sampleInput, sampleOutput);

        Long newProblemId = jdbcTemplate.queryForObject("""
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
                    cast('["Python3"]' as jsonb), cast('{}' as jsonb), ?, ?, ?,
                    ?, ?,
                    false, false, 'Mid', 'Career Project Studio', cast('{}' as jsonb),
                    true, 'student_private', now(), now()
                ) returning id
                """,
                Long.class,
                displayId, title, description, inputDescription, outputDescription,
                samplesJson, testCaseId, testCaseScoreJson,
                userId, DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_MB,
                REFERENCE_LANGUAGE, referenceCode);
        if (newProblemId == null) {
            throw new IllegalStateException("failed to insert problem for micro project");
        }
        return newProblemId;
    }

    private long persistMicroProjectRow(long userId, String majorCode, String title, String briefMd,
                                        String kcJson, long judgeProblemId, String traceId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into career_micro_project(
                        user_id, major_code, title, brief_md, related_kcs,
                        status, judge_problem_id, rollout_mode, trace_id
                    ) values (?, ?, ?, ?, cast(? as jsonb), 'recommended', ?, 'baseline', ?)
                    """, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, majorCode);
            ps.setString(3, title);
            ps.setString(4, briefMd);
            ps.setString(5, kcJson);
            ps.setLong(6, judgeProblemId);
            ps.setString(7, traceId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to insert career_micro_project");
        }
        return key.longValue();
    }

    private Map<String, Object> loadMajorRow(String majorCode) {
        try {
            return jdbcTemplate.queryForObject("""
                    select code, name_zh, seed_use_cases::text as seed_use_cases_json
                    from career_major_dictionary
                    where code = ? and enabled = true
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", rs.getString("code"));
                        row.put("name_zh", rs.getString("name_zh"));
                        row.put("seed_use_cases", rs.getString("seed_use_cases_json"));
                        return row;
                    }, majorCode);
        } catch (EmptyResultDataAccessException ignored) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "major not found: " + majorCode);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractTestCases(Map<String, Object> problemSchema) {
        Object raw = problemSchema.get("test_cases");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("input", stringOf(((Map<String, Object>) m).get("input")));
                Object expected = ((Map<String, Object>) m).get("expected");
                if (expected == null) {
                    expected = ((Map<String, Object>) m).get("output");
                }
                normalized.put("output", stringOf(expected));
                result.add(normalized);
            }
        }
        return result;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String serializeKcs(List<String> kcCodes) {
        try {
            return objectMapper.writeValueAsString(kcCodes == null ? List.of() : kcCodes);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String serializeSamples(String sampleInput, String sampleOutput) {
        try {
            if (sampleInput.isBlank() && sampleOutput.isBlank()) {
                return "[]";
            }
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("input", sampleInput);
            sample.put("output", sampleOutput);
            return objectMapper.writeValueAsString(List.of(sample));
        } catch (Exception e) {
            return "[]";
        }
    }

    private String buildUserPrompt(Map<String, Object> majorRow, List<String> kcCodes) {
        return """
                【专业】%s（%s）
                场景：%s

                【已掌握 KC】%s

                请生成一个与该专业紧密相关的 Python 微项目。严格按系统要求的 JSON 格式输出。
                """.formatted(
                majorRow.getOrDefault("name_zh", ""),
                majorRow.getOrDefault("code", ""),
                majorRow.getOrDefault("seed_use_cases", "[]"),
                kcCodes == null ? "" : String.join(", ", kcCodes)
        );
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
