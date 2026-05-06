package com.alethicode.service.career.bridging;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.CareerMajorOption;
import com.alethicode.dto.response.CareerProfileView;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.CardType;
import com.alethicode.service.aitutor.profile.LearnerProfileProjector;
import com.alethicode.service.aitutor.profile.LearnerState;
import com.alethicode.service.aitutor.reflection.ReflectionResult;
import com.alethicode.service.aitutor.reflection.ReflectionService;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Career Bridging 主实现（plan 3.2 节）。
 *
 * <p>三个能力的实现要点：
 * <ul>
 *   <li>{@code ensureProfile}：先 fail fast 校验 major_code 命中字典；UPDATE
 *   user_profile（要求行已存在，由账号注册阶段创建）；记录 enrollment 里程碑
 *   （用 (user_id, "enrollment", major_code) 三元组保唯一）。</li>
 *   <li>{@code generateForMilestone}：A/B 分组 + LLM 生成 + ReflectionService
 *   过 critic + 写 career_bridging_report + 标记里程碑已消费。control 组直接
 *   标记消费并返回 empty，不写报告（plan 3.2 节强约束）。</li>
 *   <li>{@code recentReports}：按学生 id 倒序拉，给主页 CareerProgressCard。</li>
 * </ul>
 */
@Service
public class CareerBridgingServiceImpl implements CareerBridgingService {

    private static final Logger log = LoggerFactory.getLogger(CareerBridgingServiceImpl.class);
    private static final String EXPERIMENT_ID = "career_bridging_v1";
    private static final String AI_PROFILE_PREFIX = "career-bridging";
    private static final int TITLE_MAX_LEN = 255;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final AiModelGateway aiModelGateway;
    private final ReflectionService reflectionService;
    private final RolloutPolicyService rolloutPolicyService;
    private final LearnerProfileProjector learnerProfileProjector;

    public CareerBridgingServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AlethicodeProperties properties,
            AiModelGateway aiModelGateway,
            ReflectionService reflectionService,
            RolloutPolicyService rolloutPolicyService,
            LearnerProfileProjector learnerProfileProjector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.aiModelGateway = aiModelGateway;
        this.reflectionService = reflectionService;
        this.rolloutPolicyService = rolloutPolicyService;
        this.learnerProfileProjector = learnerProfileProjector;
    }

    @Override
    @Transactional
    public EnrollmentResult ensureProfile(long userId, String majorCode, String careerIntent) {
        ensureEnabled();
        String normalizedMajor = normalizeMajorCode(majorCode);
        ensureMajorInDictionary(normalizedMajor);

        int updated = jdbcTemplate.update("""
                update user_profile
                set major_code = ?,
                    career_intent = ?,
                    career_profile_completed_at = coalesce(career_profile_completed_at, now())
                where user_id = ?
                """,
                normalizedMajor,
                blankToNull(careerIntent),
                userId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "user_profile not found for user_id=" + userId);
        }

        Long existingMilestoneId = findMilestoneId(userId, MilestoneType.ENROLLMENT.code(), normalizedMajor);
        if (existingMilestoneId != null) {
            log.info("career bridging ensureProfile: user={}, major={}, reusing milestone={}",
                    userId, normalizedMajor, existingMilestoneId);
            return new EnrollmentResult(false, existingMilestoneId, normalizedMajor);
        }

        long milestoneId = insertMilestone(userId, MilestoneType.ENROLLMENT.code(), normalizedMajor);
        log.info("career bridging ensureProfile: user={}, major={}, newMilestone={}",
                userId, normalizedMajor, milestoneId);
        return new EnrollmentResult(true, milestoneId, normalizedMajor);
    }

    @Override
    @Transactional
    public long recordMilestone(long userId, MilestoneType milestoneType, String milestoneRef) {
        ensureEnabled();
        if (milestoneType == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "milestone_type is required");
        }
        String normalizedRef = blankToNull(milestoneRef);
        Long existing = findMilestoneId(userId, milestoneType.code(), normalizedRef);
        if (existing != null) {
            return existing;
        }
        return insertMilestone(userId, milestoneType.code(), normalizedRef);
    }

    @Override
    @Transactional
    public Optional<CareerBridgingReport> generateForMilestone(long userId, long milestoneId) {
        ensureEnabled();
        MilestoneRow milestone = loadMilestone(userId, milestoneId);
        if (milestone == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "milestone not found or not owned by user: id=" + milestoneId);
        }
        if (milestone.consumedAt() != null) {
            log.debug("career bridging milestone already consumed: id={}", milestoneId);
            return latestReportForMilestone(milestoneId);
        }

        String majorCode = loadUserMajor(userId);
        if (majorCode == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "user_profile.major_code is empty; call ensureProfile first");
        }

        double treatmentRate = properties.getCareer().getBridging().getTreatmentRate();
        RolloutPolicyService.AbTestAssignment assignment = rolloutPolicyService.assignAbTest(
                EXPERIMENT_ID, userId, treatmentRate);
        if (!"treatment".equals(assignment.group())) {
            markConsumed(milestoneId);
            log.info("career bridging skipped (control group): user={}, milestone={}", userId, milestoneId);
            return Optional.empty();
        }

        Map<String, Object> majorRow = loadMajorDictionaryRow(majorCode);
        LearnerState learnerState = learnerProfileProjector.project(userId, null, Map.of(), null);
        List<String> recentPackTitles = loadRecentPackTitles(userId);

        CareerBridgingPrompts.MilestoneContext context = new CareerBridgingPrompts.MilestoneContext(
                milestone.milestoneType(),
                milestone.milestoneRef(),
                milestone.triggeredAt() == null ? "" : milestone.triggeredAt().toString(),
                majorCode,
                String.valueOf(majorRow.getOrDefault("name_zh", majorCode)),
                majorRow,
                learnerState.toMap(),
                recentPackTitles
        );

        String userPrompt = CareerBridgingPrompts.userPrompt(context, objectMapper);
        Map<String, Object> initialOutput = aiModelGateway.callForJson(
                CareerBridgingPrompts.SYSTEM, userPrompt, AI_PROFILE_PREFIX);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("major_dictionary", majorRow);
        evidence.put("learner_state", learnerState.toMap());
        evidence.put("recent_pack_titles", recentPackTitles);
        evidence.put("milestone", Map.of(
                "type", milestone.milestoneType(),
                "ref", milestone.milestoneRef() == null ? "" : milestone.milestoneRef()
        ));

        ReflectionResult reflection = reflectionService.reflectAndRefine(
                CardType.CAREER_BRIDGING, evidence, initialOutput, 1);

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        long reportId = persistReport(
                userId,
                milestoneId,
                majorCode,
                reflection.output(),
                reflection.passed(),
                assignment.group(),
                traceId
        );
        markConsumed(milestoneId);
        log.info("career bridging report generated: user={}, milestone={}, report={}, passed={}, trace={}",
                userId, milestoneId, reportId, reflection.passed(), traceId);
        return Optional.of(loadReportById(reportId));
    }

    @Override
    public List<CareerBridgingReport> recentReports(long userId, int limit) {
        ensureEnabled();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return jdbcTemplate.query("""
                select id, user_id, milestone_id, major_code, report_kind, title, content_md,
                       citations::text as citations_json, rollout_mode, reflection_passed,
                       trace_id, created_at
                from career_bridging_report
                where user_id = ?
                order by created_at desc
                limit ?
                """, this::mapReportRow, userId, safeLimit);
    }

    @Override
    public Optional<CareerProfileView> findProfile(long userId) {
        try {
            CareerProfileView view = jdbcTemplate.queryForObject("""
                    select up.major_code, dict.name_zh as major_name_zh,
                           up.career_intent, up.career_profile_completed_at
                    from user_profile up
                    left join career_major_dictionary dict
                           on dict.code = up.major_code and dict.enabled = TRUE
                    where up.user_id = ?
                    """,
                    (rs, rowNum) -> new CareerProfileView(
                            rs.getString("major_code"),
                            rs.getString("major_name_zh"),
                            rs.getString("career_intent"),
                            toInstant(rs.getTimestamp("career_profile_completed_at"))
                    ),
                    userId
            );
            return Optional.ofNullable(view);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<CareerMajorOption> listMajors() {
        return jdbcTemplate.query("""
                select code, name_zh, name_en, discipline
                from career_major_dictionary
                where enabled = TRUE
                order by discipline asc, name_zh asc
                """,
                (rs, rowNum) -> new CareerMajorOption(
                        rs.getString("code"),
                        rs.getString("name_zh"),
                        rs.getString("name_en"),
                        rs.getString("discipline")
                )
        );
    }

    private Long findMilestoneId(long userId, String type, String ref) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id from career_bridging_milestone
                    where user_id = ?
                      and milestone_type = ?
                      and milestone_ref is not distinct from ?
                    """,
                    Long.class,
                    userId, type, ref
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private long insertMilestone(long userId, String type, String ref) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into career_bridging_milestone(user_id, milestone_type, milestone_ref)
                    values (?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, type);
            if (ref == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, ref);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to insert career_bridging_milestone");
        }
        return key.longValue();
    }

    private long persistReport(
            long userId,
            long milestoneId,
            String majorCode,
            Map<String, Object> output,
            boolean reflectionPassed,
            String rolloutMode,
            String traceId
    ) {
        String llmTitle = String.valueOf(output.getOrDefault("title", "")).trim();
        String title = truncate(llmTitle.isBlank() ? "Career Bridging" : llmTitle, TITLE_MAX_LEN);
        String contentMd = renderContentMarkdown(output);
        String citationsJson = serializeCitations(output.get("citations"));
        String reportKind = "milestone";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into career_bridging_report(
                        user_id, milestone_id, major_code, report_kind, title,
                        content_md, citations, rollout_mode, reflection_passed, trace_id
                    )
                    values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setLong(2, milestoneId);
            ps.setString(3, majorCode);
            ps.setString(4, reportKind);
            ps.setString(5, title);
            ps.setString(6, contentMd);
            ps.setString(7, citationsJson);
            ps.setString(8, rolloutMode);
            ps.setBoolean(9, reflectionPassed);
            ps.setString(10, traceId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to insert career_bridging_report");
        }
        return key.longValue();
    }

    private String renderContentMarkdown(Map<String, Object> output) {
        StringBuilder sb = new StringBuilder();
        Object intro = output.get("intro_md");
        if (intro instanceof String introStr && !introStr.isBlank()) {
            sb.append(introStr.trim()).append("\n\n");
        }
        Object useCases = output.get("use_cases");
        if (useCases instanceof List<?> list && !list.isEmpty()) {
            sb.append("## 典型应用场景\n\n");
            int idx = 1;
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object nameRaw = map.get("name");
                    Object whyRaw = map.get("why_for_major");
                    String name = nameRaw == null ? "" : String.valueOf(nameRaw).trim();
                    String why = whyRaw == null ? "" : String.valueOf(whyRaw).trim();
                    Object kcs = map.get("skill_gap_kcs");
                    sb.append(idx).append(". **").append(name).append("** — ").append(why);
                    if (kcs instanceof List<?> kcList && !kcList.isEmpty()) {
                        List<String> kcStrings = kcList.stream().map(String::valueOf).toList();
                        sb.append("（关键 KC：").append(String.join(" / ", kcStrings)).append("）");
                    }
                    sb.append("\n");
                    idx++;
                }
            }
            sb.append("\n");
        }
        Object next = output.get("next_step_md");
        if (next instanceof String nextStr && !nextStr.isBlank()) {
            sb.append("## 下一步建议\n\n").append(nextStr.trim()).append("\n");
        }
        return sb.toString().trim();
    }

    private String serializeCitations(Object raw) {
        try {
            if (raw == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            log.warn("failed to serialize citations, fallback to empty array: {}", e.toString());
            return "[]";
        }
    }

    private void markConsumed(long milestoneId) {
        jdbcTemplate.update("""
                update career_bridging_milestone
                set consumed_at = now()
                where id = ? and consumed_at is null
                """, milestoneId);
    }

    private MilestoneRow loadMilestone(long userId, long milestoneId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id, user_id, milestone_type, milestone_ref, triggered_at, consumed_at
                    from career_bridging_milestone
                    where id = ? and user_id = ?
                    """,
                    (rs, rowNum) -> new MilestoneRow(
                            rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getString("milestone_type"),
                            rs.getString("milestone_ref"),
                            toInstant(rs.getTimestamp("triggered_at")),
                            toInstant(rs.getTimestamp("consumed_at"))
                    ),
                    milestoneId, userId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private String loadUserMajor(long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select major_code from user_profile where user_id = ?",
                    String.class,
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Map<String, Object> loadMajorDictionaryRow(String majorCode) {
        try {
            return jdbcTemplate.queryForObject("""
                    select code, name_zh, name_en, discipline,
                           seed_keywords::text as seed_keywords_json,
                           seed_use_cases::text as seed_use_cases_json,
                           seed_kcs::text as seed_kcs_json
                    from career_major_dictionary
                    where code = ? and enabled = TRUE
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", rs.getString("code"));
                        row.put("name_zh", rs.getString("name_zh"));
                        row.put("name_en", rs.getString("name_en"));
                        row.put("discipline", rs.getString("discipline"));
                        row.put("seed_keywords", parseJsonArray(rs.getString("seed_keywords_json")));
                        row.put("seed_use_cases", parseJsonArray(rs.getString("seed_use_cases_json")));
                        row.put("seed_kcs", parseJsonArray(rs.getString("seed_kcs_json")));
                        return row;
                    },
                    majorCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "major_dictionary entry not found: " + majorCode);
        }
    }

    private List<String> loadRecentPackTitles(long userId) {
        return jdbcTemplate.queryForList("""
                select distinct lp.title
                from learner_kc_mastery m
                join language_pack_kc kc on kc.id = m.kc_id
                join language_pack lp on lp.id = m.language_pack_id
                where m.user_id = ?
                order by lp.title
                limit 5
                """, String.class, userId);
    }

    private CareerBridgingReport loadReportById(long reportId) {
        return jdbcTemplate.queryForObject("""
                select id, user_id, milestone_id, major_code, report_kind, title, content_md,
                       citations::text as citations_json, rollout_mode, reflection_passed,
                       trace_id, created_at
                from career_bridging_report
                where id = ?
                """, this::mapReportRow, reportId);
    }

    private Optional<CareerBridgingReport> latestReportForMilestone(long milestoneId) {
        try {
            CareerBridgingReport report = jdbcTemplate.queryForObject("""
                    select id, user_id, milestone_id, major_code, report_kind, title, content_md,
                           citations::text as citations_json, rollout_mode, reflection_passed,
                           trace_id, created_at
                    from career_bridging_report
                    where milestone_id = ?
                    order by created_at desc
                    limit 1
                    """, this::mapReportRow, milestoneId);
            return Optional.ofNullable(report);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private CareerBridgingReport mapReportRow(ResultSet rs, int rowNum) throws SQLException {
        Object milestoneRaw = rs.getObject("milestone_id");
        Long milestoneId = milestoneRaw == null ? null : ((Number) milestoneRaw).longValue();
        return new CareerBridgingReport(
                rs.getLong("id"),
                rs.getLong("user_id"),
                milestoneId,
                rs.getString("major_code"),
                rs.getString("report_kind"),
                rs.getString("title"),
                rs.getString("content_md"),
                parseJsonArrayOfMaps(rs.getString("citations_json")),
                rs.getString("rollout_mode"),
                rs.getBoolean("reflection_passed"),
                rs.getString("trace_id"),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            return parsed instanceof List<?> list ? new ArrayList<>((List<Object>) list) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseJsonArrayOfMaps(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void ensureEnabled() {
        if (!properties.getCareer().getBridging().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "career bridging is disabled");
        }
    }

    private void ensureMajorInDictionary(String majorCode) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from career_major_dictionary where code = ? and enabled = TRUE)",
                Boolean.class,
                majorCode
        );
        if (exists == null || !exists) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "unknown or disabled major_code: " + majorCode);
        }
    }

    private static String normalizeMajorCode(String raw) {
        String trimmed = requireNonBlank(raw, "major_code").toLowerCase(Locale.ROOT).trim();
        if (trimmed.length() > 64) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "major_code length must be <= 64");
        }
        return trimmed;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    fieldName + " is required");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private record MilestoneRow(
            long id,
            long userId,
            String milestoneType,
            String milestoneRef,
            Instant triggeredAt,
            Instant consumedAt
    ) {
    }
}
