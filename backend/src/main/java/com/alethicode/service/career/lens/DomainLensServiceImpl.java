package com.alethicode.service.career.lens;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.rollout.RolloutDecision;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Coding Lens 实现（plan 4.1 节）。
 *
 * <p>核心流程：
 * 1. 命中缓存 → 直接返回
 * 2. Rollout 决策 → rollback 直接返回 empty（回退原版题面）
 * 3. LLM 生成 narrative → 拼装 problem 原始 IO + major_dictionary 作 evidence
 * 4. Reflection critic（CardType.DOMAIN_VARIANT）→ 不通过不写库
 * 5. 写 problem_domain_variant → 返回变体
 */
@Service
public class DomainLensServiceImpl implements DomainLensService {

    private static final Logger log = LoggerFactory.getLogger(DomainLensServiceImpl.class);
    private static final String AI_PROFILE_PREFIX = "coding-lens";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiModelGateway aiModelGateway;
    private final ReflectionService reflectionService;
    private final RolloutPolicyService rolloutPolicyService;

    public DomainLensServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiModelGateway aiModelGateway,
            ReflectionService reflectionService,
            RolloutPolicyService rolloutPolicyService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiModelGateway = aiModelGateway;
        this.reflectionService = reflectionService;
        this.rolloutPolicyService = rolloutPolicyService;
    }

    @Override
    public Optional<ProblemDomainVariant> findOrGenerate(long problemId, String majorCode) {
        // 考试模式（plan 4.4 节 + todo 15）：教师 lockForExam 之后，任意 major 的
        // 请求都强制返回锁定 variant，确保所有学生看到同一份题面，避免不公平。
        ProblemDomainVariant lockedVariant = findLockedVariant(problemId);
        if (lockedVariant != null) {
            log.debug("coding lens locked variant override for problem={}, requestedMajor={}, lockedMajor={}",
                    problemId, majorCode, lockedVariant.majorCode());
            return Optional.of(lockedVariant);
        }

        ProblemDomainVariant cached = findCached(problemId, majorCode);
        if (cached != null) {
            return Optional.of(cached);
        }

        RolloutDecision decision = rolloutPolicyService.evaluate(
                "coding_lens", "problem:" + problemId, Map.of());
        if ("rollback".equals(decision.rolloutMode())) {
            log.debug("coding lens rollback for problem={}, major={}", problemId, majorCode);
            return Optional.empty();
        }

        Map<String, Object> problemRow = loadProblemEvidence(problemId);
        Map<String, Object> majorRow = loadMajorRow(majorCode);

        String userPrompt = buildUserPrompt(problemRow, majorRow);
        Map<String, Object> initialOutput = aiModelGateway.callForJson(
                DomainLensPrompts.SYSTEM, userPrompt, AI_PROFILE_PREFIX);

        Object abortFlag = initialOutput.get("abort");
        if (Boolean.TRUE.equals(abortFlag) || "true".equals(String.valueOf(abortFlag))) {
            log.info("coding lens LLM self-aborted for problem={}, major={}", problemId, majorCode);
            return Optional.empty();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("problem", problemRow);
        evidence.put("major_dictionary", majorRow);

        ReflectionResult reflection = reflectionService.reflectAndRefine(
                CardType.DOMAIN_VARIANT, evidence, initialOutput, 1);

        if (!reflection.passed()) {
            log.warn("coding lens critic rejected for problem={}, major={}: {}",
                    problemId, majorCode, reflection.criticVerdict());
            return Optional.empty();
        }

        Map<String, Object> output = reflection.output();
        Double driftScore = extractDriftScore(output);

        jdbcTemplate.update("""
                insert into problem_domain_variant(
                    problem_id, major_code, title, description_md,
                    sample_input_text, sample_output_text, domain_metaphor,
                    semantic_drift_score, reflection_passed
                )
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, true)
                on conflict (problem_id, major_code) do update
                set title = excluded.title,
                    description_md = excluded.description_md,
                    sample_input_text = excluded.sample_input_text,
                    sample_output_text = excluded.sample_output_text,
                    domain_metaphor = excluded.domain_metaphor,
                    semantic_drift_score = excluded.semantic_drift_score,
                    reflection_passed = true,
                    generated_at = now()
                """,
                problemId,
                majorCode,
                truncate(String.valueOf(output.getOrDefault("title", "")), 512),
                String.valueOf(output.getOrDefault("description_md", "")),
                String.valueOf(output.getOrDefault("rewritten_sample_input", "")),
                String.valueOf(output.getOrDefault("rewritten_sample_output", "")),
                serializeJson(output.get("domain_metaphor")),
                driftScore
        );

        log.info("coding lens variant persisted: problem={}, major={}, drift={}",
                problemId, majorCode, driftScore);
        return Optional.ofNullable(findCached(problemId, majorCode));
    }

    @Override
    @Transactional
    public void lockForExam(long variantId, long teacherId) {
        int updated = jdbcTemplate.update("""
                update problem_domain_variant
                set locked_for_exam = true, validated_by = ?
                where id = ?
                """, teacherId, variantId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "variant not found: id=" + variantId);
        }
    }

    @Override
    @Transactional
    public void invalidate(long problemId) {
        int deleted = jdbcTemplate.update(
                "delete from problem_domain_variant where problem_id = ? and locked_for_exam = false",
                problemId
        );
        log.info("coding lens invalidated {} unlocked variants for problem={}", deleted, problemId);
    }

    /**
     * 查找该题目是否存在任意 locked variant（教师锁定考试模式）。
     * 命中即覆盖请求 major，让所有学生看同一份。
     */
    private ProblemDomainVariant findLockedVariant(long problemId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id, problem_id, major_code, title, description_md,
                           sample_input_text, sample_output_text,
                           domain_metaphor::text as domain_metaphor_json,
                           semantic_drift_score, reflection_passed, locked_for_exam,
                           generated_at, validated_by
                    from problem_domain_variant
                    where problem_id = ?
                      and locked_for_exam = true
                      and reflection_passed = true
                    order by generated_at desc
                    limit 1
                    """,
                    this::mapVariantRow,
                    problemId);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private ProblemDomainVariant findCached(long problemId, String majorCode) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id, problem_id, major_code, title, description_md,
                           sample_input_text, sample_output_text,
                           domain_metaphor::text as domain_metaphor_json,
                           semantic_drift_score, reflection_passed, locked_for_exam,
                           generated_at, validated_by
                    from problem_domain_variant
                    where problem_id = ? and major_code = ? and reflection_passed = true
                    """,
                    this::mapVariantRow,
                    problemId, majorCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Map<String, Object> loadProblemEvidence(long problemId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select title, description, input_description, output_description,
                           samples::text as samples_json
                    from problem
                    where id = ?
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("title", rs.getString("title"));
                        row.put("description", rs.getString("description"));
                        row.put("input_description", rs.getString("input_description"));
                        row.put("output_description", rs.getString("output_description"));
                        row.put("samples", rs.getString("samples_json"));
                        return row;
                    },
                    problemId
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "problem not found: id=" + problemId);
        }
    }

    private Map<String, Object> loadMajorRow(String majorCode) {
        try {
            return jdbcTemplate.queryForObject("""
                    select code, name_zh, discipline,
                           seed_use_cases::text as seed_use_cases_json
                    from career_major_dictionary
                    where code = ? and enabled = true
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", rs.getString("code"));
                        row.put("name_zh", rs.getString("name_zh"));
                        row.put("discipline", rs.getString("discipline"));
                        row.put("seed_use_cases", rs.getString("seed_use_cases_json"));
                        return row;
                    },
                    majorCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "major not found: " + majorCode);
        }
    }

    private String buildUserPrompt(Map<String, Object> problem, Map<String, Object> major) {
        return """
                【原题】
                标题：%s
                描述：%s
                输入说明：%s
                输出说明：%s
                样例：%s

                【目标专业】
                %s（%s，%s）
                场景示例：%s

                请将上面的原题改写为面向该专业的版本。严格按系统要求的 JSON 格式输出。
                """.formatted(
                problem.getOrDefault("title", ""),
                problem.getOrDefault("description", ""),
                problem.getOrDefault("input_description", ""),
                problem.getOrDefault("output_description", ""),
                problem.getOrDefault("samples", ""),
                major.getOrDefault("name_zh", ""),
                major.getOrDefault("code", ""),
                major.getOrDefault("discipline", ""),
                major.getOrDefault("seed_use_cases", "[]")
        );
    }

    /**
     * 从 LLM verification 块抽取「测试样例语义偏移度」的占位评分（plan 4.1 节）。
     *
     * <p>当前是 placeholder：仅根据 {@code verification.drift_explanation} 是否
     * 非空粗略给 0.05 / 0.0；不是真实的 drift 计算（真正的 drift score 需要
     * 跑 sample IO 等价对照）。该列写入 V85 表 {@code semantic_drift_score}
     * 给运维监控使用，0.05 仅作为「LLM 自报告有可解释偏移迹象」的提示信号。
     *
     * <p>plan todo 14 接入 RolloutPolicyService 后会被真实 drift 算法替换。
     */
    private Double extractDriftScore(Map<String, Object> output) {
        Object verification = output.get("verification");
        if (verification instanceof Map<?, ?> vMap) {
            Object drift = vMap.get("drift_explanation");
            if (drift instanceof String s && !s.isBlank()) {
                return 0.05;
            }
        }
        return 0.0;
    }

    private String serializeJson(Object value) {
        try {
            return value == null ? "{}" : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ProblemDomainVariant mapVariantRow(ResultSet rs, int rowNum) throws SQLException {
        Object validatedByRaw = rs.getObject("validated_by");
        Long validatedBy = validatedByRaw == null ? null : ((Number) validatedByRaw).longValue();
        String metaphorJson = rs.getString("domain_metaphor_json");
        Map<String, Object> metaphor;
        try {
            metaphor = metaphorJson == null
                    ? Map.of()
                    : objectMapper.readValue(metaphorJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            metaphor = Map.of();
        }
        Timestamp generatedAt = rs.getTimestamp("generated_at");
        return new ProblemDomainVariant(
                rs.getLong("id"),
                rs.getLong("problem_id"),
                rs.getString("major_code"),
                rs.getString("title"),
                rs.getString("description_md"),
                rs.getString("sample_input_text"),
                rs.getString("sample_output_text"),
                metaphor,
                rs.getObject("semantic_drift_score") == null
                        ? null : rs.getDouble("semantic_drift_score"),
                rs.getBoolean("reflection_passed"),
                rs.getBoolean("locked_for_exam"),
                generatedAt == null ? null : generatedAt.toInstant(),
                validatedBy
        );
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
