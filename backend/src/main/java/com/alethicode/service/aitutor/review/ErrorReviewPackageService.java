package com.alethicode.service.aitutor.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.request.CreateReviewPackageRequest;
import com.alethicode.dto.response.ReviewPackageResponse;
import com.alethicode.dto.response.ReviewPackageStatsResponse;
import com.alethicode.service.aitutor.contract.ErrorTaxonomy;
import com.alethicode.service.aitutor.events.LearningEventPublisher;
import com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Lazy
public class ErrorReviewPackageService {

    static final int REVIEW_PROBLEM_COUNT = ReviewProblemSelector.REVIEW_PROBLEM_COUNT;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BeginnerSupplementPlannerService beginnerSupplementPlannerService;
    private final SpecializedProblemGenerator specializedProblemGenerator;
    private final ReviewProblemSelector reviewProblemSelector;
    private final ReviewPackageProblemMetaResolver metaResolver;
    private final ReviewPackageFsrsAdvancer fsrsAdvancer;
    private final ReviewSubmissionRecorder submissionRecorder;
    private final LearningEventPublisher learningEventPublisher;
    private final FsrsSchedulerService fsrsSchedulerService;

    @Autowired
    public ErrorReviewPackageService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                     BeginnerSupplementPlannerService beginnerSupplementPlannerService,
                                     SpecializedProblemGenerator specializedProblemGenerator,
                                     ReviewProblemSelector reviewProblemSelector,
                                     ReviewPackageProblemMetaResolver metaResolver,
                                     ReviewPackageFsrsAdvancer fsrsAdvancer,
                                     ReviewSubmissionRecorder submissionRecorder,
                                     LearningEventPublisher learningEventPublisher,
                                     FsrsSchedulerService fsrsSchedulerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.beginnerSupplementPlannerService = beginnerSupplementPlannerService;
        this.specializedProblemGenerator = specializedProblemGenerator;
        this.reviewProblemSelector = reviewProblemSelector;
        this.metaResolver = metaResolver;
        this.fsrsAdvancer = fsrsAdvancer;
        this.submissionRecorder = submissionRecorder;
        this.learningEventPublisher = learningEventPublisher;
        this.fsrsSchedulerService = fsrsSchedulerService;
    }

    public ErrorReviewPackageService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                     SpecializedProblemGenerator specializedProblemGenerator) {
        this(jdbcTemplate, objectMapper,
                new BeginnerSupplementPlannerService(jdbcTemplate),
                specializedProblemGenerator,
                new ReviewProblemSelector(jdbcTemplate),
                new ReviewPackageProblemMetaResolver(),
                new ReviewPackageFsrsAdvancer(jdbcTemplate, new FsrsSchedulerService(), LearningEventPublisher.NOOP),
                new ReviewSubmissionRecorder(jdbcTemplate, new ReviewPackageFsrsAdvancer(jdbcTemplate, new FsrsSchedulerService(), LearningEventPublisher.NOOP)),
                LearningEventPublisher.NOOP,
                new FsrsSchedulerService());
    }

    public List<ReviewPackageResponse> createPackages(Long userId, List<CreateReviewPackageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("review package items are required");
        }
        List<ReviewPackageResponse> responses = new ArrayList<>();
        for (CreateReviewPackageRequest request : requests) {
            if (request == null) throw new IllegalArgumentException("review package item is required");
            if (request.languagePackId() == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
            }
            responses.add(createPackage(
                    userId,
                    request.errorTaxonomy(),
                    request.languagePackId(),
                    request.problemId(),
                    request.trigger()
            ));
        }
        return responses;
    }

    public ReviewPackageResponse createPackage(Long userId, String errorTaxonomy, Long languagePackId, Long problemId, String trigger) {
        if (!ErrorTaxonomy.isValid(errorTaxonomy)) throw new IllegalArgumentException("Invalid error_taxonomy: " + errorTaxonomy);
        Map<String, Object> evidence = buildEvidenceSummary(userId, errorTaxonomy);
        String normalizedTrigger = (trigger == null || trigger.isBlank()) ? "wrong_answer" : trigger.trim().toLowerCase(Locale.ROOT);
        Map<Long, Map<String, Object>> plannedProblemMeta = new LinkedHashMap<>();
        if (languagePackId != null) {
            Map<String, Object> plan = beginnerSupplementPlannerService.buildPlan(userId, normalizedTrigger, languagePackId, problemId, null, errorTaxonomy, REVIEW_PROBLEM_COUNT);
            plannedProblemMeta.putAll(metaResolver.extractFromSupplementPlan(plan));
            evidence.put("language_pack_id", languagePackId);
            evidence.put("trigger", normalizedTrigger);
            evidence.put("intro_message", plan.get("intro_message"));
            evidence.put("target_kcs", plan.getOrDefault("target_kcs", List.of()));
            evidence.put("language_profile", plan.getOrDefault("language_profile", Map.of()));
            evidence.put("planned_problem_cards", new ArrayList<>(plannedProblemMeta.values()));
        }
        List<Long> problemIds = reviewProblemSelector.select(userId, errorTaxonomy, languagePackId, new ArrayList<>(plannedProblemMeta.keySet()));
        if (problemIds.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "没有找到与该错误类型相关的题目用于生成复习包");
        }
        @SuppressWarnings("unchecked")
        List<String> rootCauses = (List<String>) evidence.getOrDefault("recent_root_causes", List.of());
        String packageId = randomId();
        FsrsSchedulerService.ScheduleState s = fsrsSchedulerService.initialize(Instant.now());
        jdbcTemplate.update("""
                insert into ai_error_review_package(id, user_id, error_taxonomy, evidence_summary,
                    problem_count, completed_count, mastery_reached, all_ac,
                    fsrs_state, fsrs_due_at, fsrs_stability, fsrs_difficulty,
                    fsrs_retrievability, fsrs_reps, fsrs_lapses, fsrs_last_review_at,
                    created_at, updated_at)
                values (?, ?, ?, cast(? as jsonb), ?, 0, false, false, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                packageId, userId, errorTaxonomy, toJson(evidence), problemIds.size(),
                s.state(), Timestamp.from(s.dueAt()), s.stability(), s.difficulty(),
                s.retrievability(), s.reps(), s.lapses(), null);
        List<ReviewPackageResponse.ReviewProblemItem> problems = new ArrayList<>();
        int seq = 0;
        for (Long pid : problemIds) {
            seq++;
            String problemRowId = randomId();
            jdbcTemplate.update("""
                    insert into ai_error_review_problem(id, package_id, problem_id, sequence, submitted, is_correct, is_ai_generated, created_at)
                    values (?, ?, ?, ?, false, null, false, now())
                    """, problemRowId, packageId, pid, seq);
            Map<String, Object> info = loadProblemBasicInfo(pid);
            Map<String, Object> meta = plannedProblemMeta.getOrDefault(pid, metaResolver.defaultMeta(seq, false, info));
            problems.add(new ReviewPackageResponse.ReviewProblemItem(
                    problemRowId, pid, String.valueOf(info.getOrDefault("_id", "")),
                    String.valueOf(info.getOrDefault("title", "")), seq, false, null, false,
                    metaResolver.stringValue(meta.get("card_type")),
                    metaResolver.stringValue(meta.get("education_goal")),
                    metaResolver.stringValue(meta.get("why_this_now")),
                    metaResolver.toStringList(meta.get("target_kcs")),
                    null, false));
        }
        specializedProblemGenerator.scheduleSpecializedProblemGeneration(packageId, userId, errorTaxonomy, rootCauses, problemIds, seq);
        learningEventPublisher.publishReviewPackageUpdated(userId, packageId, "created", fsrsPayload(s));
        return new ReviewPackageResponse(
                packageId, errorTaxonomy, ErrorTaxonomy.label(errorTaxonomy),
                evidence, problemIds.size(), 0, false, problems,
                formatNow(), s.state(), formatInstant(s.dueAt()), s.stability(), s.difficulty(), s.retrievability());
    }

    public List<ReviewPackageResponse> listPackages(Long userId) {
        return jdbcTemplate.query(
                "select id, error_taxonomy, evidence_summary::text as evidence_json, problem_count, completed_count, mastery_reached, created_at, fsrs_state, fsrs_due_at, fsrs_stability, fsrs_difficulty, fsrs_retrievability from ai_error_review_package where user_id = ? order by fsrs_due_at asc, created_at desc limit 50",
                (rs, rowNum) -> mapToResponse(rs, null), userId);
    }

    public ReviewPackageResponse getPackageDetail(Long userId, String packageId) {
        ReviewPackageResponse pkg = jdbcTemplate.query(
                "select id, error_taxonomy, evidence_summary::text as evidence_json, problem_count, completed_count, mastery_reached, created_at, fsrs_state, fsrs_due_at, fsrs_stability, fsrs_difficulty, fsrs_retrievability from ai_error_review_package where id = ? and user_id = ?",
                (rs, rowNum) -> mapToResponse(rs, null), packageId, userId
        ).stream().findFirst().orElse(null);
        if (pkg == null) throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "复习包不存在");
        List<ReviewPackageResponse.ReviewProblemItem> problems = jdbcTemplate.query(
                "select rp.id, rp.problem_id, rp.sequence, rp.submitted, rp.is_correct, rp.is_ai_generated, rp.user_rating, p._id as problem_key, p.title, coalesce(p.statistic_info #>> '{objective_question,question_type}', 'coding') as question_type from ai_error_review_problem rp left join problem p on p.id = rp.problem_id where rp.package_id = ? order by rp.sequence",
                (rs, rowNum) -> mapProblemRow(rs, pkg.evidenceSummary()), packageId
        );
        return new ReviewPackageResponse(
                pkg.id(), pkg.errorTaxonomy(), pkg.errorLabel(),
                pkg.evidenceSummary(), pkg.problemCount(), pkg.completedCount(),
                pkg.masteryReached(), problems, pkg.createdAt(),
                pkg.fsrsState(), pkg.dueAt(), pkg.stability(), pkg.difficulty(), pkg.retrievability());
    }

    private ReviewPackageResponse.ReviewProblemItem mapProblemRow(java.sql.ResultSet rs, Map<String, Object> evidence) throws java.sql.SQLException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("question_type", rs.getString("question_type"));
        Long problemId = rs.getObject("problem_id") == null ? null : rs.getLong("problem_id");
        boolean isUnavailable = problemId == null;
        Map<String, Object> meta = problemId == null
                ? Map.of()
                : metaResolver.resolveFromEvidenceSummary(evidence, problemId);
        if (meta.isEmpty()) meta = metaResolver.defaultMeta(rs.getInt("sequence"), rs.getBoolean("is_ai_generated"), info);
        return new ReviewPackageResponse.ReviewProblemItem(
                rs.getString("id"), problemId,
                rs.getString("problem_key"), rs.getString("title"),
                rs.getInt("sequence"), rs.getBoolean("submitted"),
                rs.getObject("is_correct") == null ? null : rs.getBoolean("is_correct"),
                rs.getBoolean("is_ai_generated"),
                metaResolver.stringValue(meta.get("card_type")),
                metaResolver.stringValue(meta.get("education_goal")),
                metaResolver.stringValue(meta.get("why_this_now")),
                metaResolver.toStringList(meta.get("target_kcs")),
                rs.getString("user_rating"),
                isUnavailable);
    }

    public void recordSubmission(Long userId, Long problemId, boolean isCorrect) {
        submissionRecorder.recordSubmission(userId, problemId, isCorrect);
    }

    public ReviewPackageResponse reviewPackage(Long userId, String packageId,
                                               FsrsSchedulerService.ReviewRating rating, Instant reviewedAt) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (packageId == null || packageId.isBlank()) throw new IllegalArgumentException("packageId is required");
        if (rating == null) throw new IllegalArgumentException("rating is required");
        Long ownerId = jdbcTemplate.queryForObject(
                "select user_id from ai_error_review_package where id = ?", Long.class, packageId
        );
        if (!userId.equals(ownerId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "复习包不属于当前用户");
        }
        fsrsAdvancer.advance(packageId, rating, reviewedAt == null ? Instant.now() : reviewedAt, "explicit_review", userId);
        return getPackageDetail(userId, packageId);
    }

    public void advancePackageScheduleAfterMastery(String packageId, Long userId, Instant reviewedAt) {
        fsrsAdvancer.advance(packageId, FsrsSchedulerService.ReviewRating.GOOD, reviewedAt, "all_problems_mastered", userId);
    }

    public ReviewPackageStatsResponse getStats() {
        List<ReviewPackageStatsResponse.TaxonomyStat> stats = jdbcTemplate.query(
                "select error_taxonomy, count(*) as generated, count(*) filter (where completed_count = problem_count) as completed, count(*) filter (where mastery_reached = true) as mastery_reached, count(*) filter (where completed_count < problem_count) as pending from ai_error_review_package group by error_taxonomy order by generated desc",
                (rs, rowNum) -> new ReviewPackageStatsResponse.TaxonomyStat(
                        rs.getString("error_taxonomy"), ErrorTaxonomy.label(rs.getString("error_taxonomy")),
                        rs.getLong("generated"), rs.getLong("completed"),
                        rs.getLong("mastery_reached"), rs.getLong("pending"))
        );
        long g = stats.stream().mapToLong(ReviewPackageStatsResponse.TaxonomyStat::generated).sum();
        long c = stats.stream().mapToLong(ReviewPackageStatsResponse.TaxonomyStat::completed).sum();
        long m = stats.stream().mapToLong(ReviewPackageStatsResponse.TaxonomyStat::masteryReached).sum();
        return new ReviewPackageStatsResponse(stats, g, c, m);
    }

    private ReviewPackageResponse mapToResponse(java.sql.ResultSet rs, List<ReviewPackageResponse.ReviewProblemItem> problems) throws java.sql.SQLException {
        return new ReviewPackageResponse(
                rs.getString("id"), rs.getString("error_taxonomy"),
                ErrorTaxonomy.label(rs.getString("error_taxonomy")),
                parseJsonMap(rs.getString("evidence_json")),
                rs.getInt("problem_count"), rs.getInt("completed_count"),
                rs.getBoolean("mastery_reached"), problems,
                formatTime(rs.getTimestamp("created_at")),
                rs.getString("fsrs_state"), formatTime(rs.getTimestamp("fsrs_due_at")),
                getNullableDouble(rs, "fsrs_stability"), getNullableDouble(rs, "fsrs_difficulty"), getNullableDouble(rs, "fsrs_retrievability")
        );
    }

    private Map<String, Object> fsrsPayload(FsrsSchedulerService.ScheduleState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fsrs_state", state.state());
        payload.put("due_at", formatInstant(state.dueAt()));
        payload.put("stability", state.stability());
        payload.put("difficulty", state.difficulty());
        payload.put("retrievability", state.retrievability());
        payload.put("reps", state.reps());
        payload.put("lapses", state.lapses());
        if (state.lastReviewAt() != null) payload.put("last_review_at", formatInstant(state.lastReviewAt()));
        return payload;
    }

    private Map<String, Object> buildEvidenceSummary(Long userId, String errorTaxonomy) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        int notebookCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_learner_notebook where user_id = ? and error_taxonomy = ? and is_deleted = false",
                Integer.class, userId, errorTaxonomy
        );
        evidence.put("notebook_count", notebookCount);
        int eventCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_learning_event where user_id = ? and error_taxonomy = ?",
                Integer.class, userId, errorTaxonomy
        );
        evidence.put("event_count", eventCount);
        List<String> recentCauses = jdbcTemplate.query(
                """
                select root_cause
                from ai_learner_notebook
                where user_id = ? and error_taxonomy = ? and is_deleted = false
                  and root_cause is not null
                  and length(btrim(root_cause)) >= 2
                  and root_cause !~ '^[0-9]+$'
                group by root_cause
                order by count(*) desc, max(update_time) desc
                limit 5
                """,
                (rs, rowNum) -> rs.getString("root_cause"),
                userId, errorTaxonomy
        );
        evidence.put("recent_root_causes", recentCauses);
        return evidence;
    }

    private Map<String, Object> loadProblemBasicInfo(Long problemId) {
        return jdbcTemplate.query(
                "select _id, title, coalesce(statistic_info #>> '{objective_question,question_type}', 'coding') as question_type from problem where id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("_id", rs.getString("_id"));
                    row.put("title", rs.getString("title"));
                    row.put("question_type", rs.getString("question_type"));
                    return row;
                },
                problemId
        ).stream().findFirst().orElse(Map.of());
    }

    private Double getNullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object v = rs.getObject(column); return v == null ? null : ((Number) v).doubleValue();
    }
    private String formatInstant(Instant instant) { return instant == null ? null : ISO_FORMATTER.format(instant.atOffset(ZoneOffset.UTC)); }
    private String formatTime(Timestamp t) { return t == null ? null : ISO_FORMATTER.format(t.toInstant().atOffset(ZoneOffset.UTC)); }
    private String formatNow() { return ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)); }
    private String randomId() { return UUID.randomUUID().toString().replace("-", ""); }
    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return "{}"; }
    }
    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try { return objectMapper.readValue(raw, new TypeReference<>() {}); } catch (Exception e) { return Map.of(); }
    }
}
