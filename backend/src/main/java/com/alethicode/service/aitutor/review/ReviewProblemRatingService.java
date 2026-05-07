package com.alethicode.service.aitutor.review;

import com.alethicode.dto.response.ReviewPackageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.exception.BusinessExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 错题强化包「单题级评分」服务（Phase 3 新建）。
 *
 * 业务行为：
 *  - {@code good} → 写 user_rating；当包内所有题已 good，调用 ErrorReviewPackageService 推进包级 FSRS。
 *  - {@code again} → 写 user_rating + 同步生成 1 道 AI 特化相似题、append 到包末尾，不立即推进 FSRS。
 *
 * 校验顺序（fail-fast）：用户存在 → 拥有包 → 题属于包 → submitted=true 才允许评分。
 */
@Service
@Lazy
public class ReviewProblemRatingService {

    private static final Logger log = LoggerFactory.getLogger(ReviewProblemRatingService.class);

    public static final String RATING_GOOD = "good";
    public static final String RATING_AGAIN = "again";
    private static final Set<String> ALLOWED_RATINGS = Set.of(RATING_GOOD, RATING_AGAIN);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SpecializedProblemGenerator specializedProblemGenerator;
    private final ErrorReviewPackageService errorReviewPackageService;

    public ReviewProblemRatingService(JdbcTemplate jdbcTemplate,
                                      ObjectMapper objectMapper,
                                      SpecializedProblemGenerator specializedProblemGenerator,
                                      ErrorReviewPackageService errorReviewPackageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.specializedProblemGenerator = specializedProblemGenerator;
        this.errorReviewPackageService = errorReviewPackageService;
    }

    public ReviewPackageResponse rateProblem(Long userId, String packageId, String problemRowId, String rawRating) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (packageId == null || packageId.isBlank()) throw new IllegalArgumentException("packageId is required");
        if (problemRowId == null || problemRowId.isBlank()) throw new IllegalArgumentException("problemId is required");

        String rating = normalizeRating(rawRating);
        ReviewProblemRow row = loadProblemRow(packageId, problemRowId, userId);

        if (RATING_GOOD.equals(rating)) {
            applyGoodRating(packageId, problemRowId, userId);
        } else {
            applyAgainRating(packageId, problemRowId, userId, row);
        }
        return errorReviewPackageService.getPackageDetail(userId, packageId);
    }

    /**
     * Parsons + FSRS 闭环驱动的自动评分（设计稿 §11.1）。
     *
     * <p>当 Parsons walkthrough 通过 / 连续失败到 cascade_failfast 时，
     * ParsonsCapabilityService 会调用这里把对应错题包条目自动 rate 为
     * good/again，从而推进 FSRS。</p>
     *
     * <p>与 {@link #rateProblem} 的差别：</p>
     * <ul>
     *   <li>用 (packageId, problemId) 定位行而非 row id（Parsons 入口拿不到 row id）</li>
     *   <li>跳过 {@code submitted=true} 校验：Parsons 真实判题已经是练习证据，
     *       同时把行标记为 submitted + is_correct，便于错题包页面展示</li>
     *   <li>如果该题不在指定包内，记 warn 并返回，不抛错——Parsons 主流程不应被
     *       FSRS 副作用阻塞</li>
     *   <li>当 user_rating 已经存在且与本次一致，直接 no-op 避免重复 rate</li>
     * </ul>
     */
    public void recordParsonsOutcome(Long userId, String packageId, Long problemId, String rawRating) {
        if (userId == null || packageId == null || packageId.isBlank() || problemId == null) {
            log.warn("recordParsonsOutcome skipped: invalid args (userId={}, packageId={}, problemId={})",
                    userId, packageId, problemId);
            return;
        }
        String rating = normalizeRating(rawRating);

        Long ownerId = jdbcTemplate.query(
                "select user_id from ai_error_review_package where id = ?",
                (rs, rowNum) -> rs.getLong("user_id"),
                packageId
        ).stream().findFirst().orElse(null);
        if (ownerId == null) {
            log.warn("recordParsonsOutcome skipped: package not found (packageId={})", packageId);
            return;
        }
        if (!userId.equals(ownerId)) {
            log.warn("recordParsonsOutcome skipped: package not owned by user (packageId={}, userId={}, owner={})",
                    packageId, userId, ownerId);
            return;
        }

        ReviewProblemRow row = jdbcTemplate.query(
                """
                select id, problem_id, sequence, submitted, is_correct
                from ai_error_review_problem
                where package_id = ? and problem_id = ?
                order by sequence asc
                limit 1
                """,
                (rs, rowNum) -> new ReviewProblemRow(
                        rs.getString("id"),
                        rs.getLong("problem_id"),
                        rs.getInt("sequence"),
                        rs.getBoolean("submitted"),
                        rs.getObject("is_correct") == null ? null : rs.getBoolean("is_correct")
                ),
                packageId, problemId
        ).stream().findFirst().orElse(null);
        if (row == null) {
            log.warn("recordParsonsOutcome skipped: problem not in package (packageId={}, problemId={})",
                    packageId, problemId);
            return;
        }

        // Parsons 执行判题也算练习证据，因此标记为已提交。
        jdbcTemplate.update(
                "update ai_error_review_problem set submitted = true, is_correct = ? where id = ?",
                RATING_GOOD.equals(rating), row.id()
        );

        String currentRating = jdbcTemplate.query(
                "select user_rating from ai_error_review_problem where id = ?",
                (rs, rowNum) -> rs.getString("user_rating"),
                row.id()
        ).stream().findFirst().orElse(null);
        if (currentRating != null && currentRating.equalsIgnoreCase(rating)) {
            log.debug("recordParsonsOutcome no-op: already rated {} (packageId={}, rowId={})", rating, packageId, row.id());
            return;
        }

        try {
            if (RATING_GOOD.equals(rating)) {
                applyGoodRating(packageId, row.id(), userId);
            } else {
                applyAgainRating(packageId, row.id(), userId, row);
            }
        } catch (RuntimeException e) {
            // Parsons FSRS bridge：副作用失败只记日志，不阻塞 Parsons 主流程
            log.warn("recordParsonsOutcome failed for packageId={} rowId={} rating={}: {}",
                    packageId, row.id(), rating, e.getMessage());
        }
    }

    private void applyGoodRating(String packageId, String problemRowId, Long userId) {
        jdbcTemplate.update(
                "update ai_error_review_problem set user_rating = ?, rated_at = now() where id = ? and package_id = ?",
                RATING_GOOD, problemRowId, packageId
        );
        if (allProblemsRatedGood(packageId)) {
            errorReviewPackageService.advancePackageScheduleAfterMastery(packageId, userId, Instant.now());
        }
    }

    private void applyAgainRating(String packageId, String problemRowId, Long userId, ReviewProblemRow row) {
        jdbcTemplate.update(
                "update ai_error_review_problem set user_rating = ?, rated_at = now() where id = ? and package_id = ?",
                RATING_AGAIN, problemRowId, packageId
        );
        PackageContext ctx = loadPackageContext(packageId);
        try {
            Long newProblemId = specializedProblemGenerator.generateOne(
                    userId,
                    ctx.errorTaxonomy(),
                    ctx.rootCauses(),
                    List.of(row.problemId())
            );
            int nextSequence = currentMaxSequence(packageId) + 1;
            specializedProblemGenerator.appendOneToPackage(packageId, newProblemId, nextSequence);
            jdbcTemplate.update(
                    "update ai_error_review_package set problem_count = problem_count + 1, updated_at = now() where id = ?",
                    packageId
            );
        } catch (Exception e) {
            log.warn("再练一题生成失败 (packageId={}, problemRowId={}): {}", packageId, problemRowId, e.getMessage());
            throw BusinessExceptions.fromLegacy("error", "AI 暂时无法生成相似题，请稍后再试");
        }
    }

    private String normalizeRating(String rawRating) {
        if (rawRating == null) throw BusinessExceptions.fromLegacy("error", "rating 不能为空");
        String normalized = rawRating.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_RATINGS.contains(normalized)) {
            throw BusinessExceptions.fromLegacy("error", "rating 必须是 again 或 good");
        }
        return normalized;
    }

    private ReviewProblemRow loadProblemRow(String packageId, String problemRowId, Long userId) {
        Long ownerId = jdbcTemplate.query(
                "select user_id from ai_error_review_package where id = ?",
                (rs, rowNum) -> rs.getLong("user_id"),
                packageId
        ).stream().findFirst().orElse(null);
        if (ownerId == null) {
            throw BusinessExceptions.fromLegacy("not-found", "复习包不存在");
        }
        if (!userId.equals(ownerId)) {
            throw BusinessExceptions.fromLegacy("permission-denied", "复习包不属于当前用户");
        }
        ReviewProblemRow row = jdbcTemplate.query(
                "select id, problem_id, sequence, submitted, is_correct from ai_error_review_problem where id = ? and package_id = ?",
                (rs, rowNum) -> new ReviewProblemRow(
                        rs.getString("id"),
                        rs.getLong("problem_id"),
                        rs.getInt("sequence"),
                        rs.getBoolean("submitted"),
                        rs.getObject("is_correct") == null ? null : rs.getBoolean("is_correct")
                ),
                problemRowId, packageId
        ).stream().findFirst().orElse(null);
        if (row == null) {
            throw BusinessExceptions.fromLegacy("not-found", "题目不属于该复习包");
        }
        if (!row.submitted()) {
            throw BusinessExceptions.fromLegacy("error", "请先完成本题再评分");
        }
        return row;
    }

    private boolean allProblemsRatedGood(String packageId) {
        Integer remaining = jdbcTemplate.queryForObject(
                """
                select count(*) from ai_error_review_problem
                where package_id = ? and (user_rating is null or user_rating <> 'good')
                """,
                Integer.class,
                packageId
        );
        return remaining != null && remaining == 0;
    }

    private int currentMaxSequence(String packageId) {
        Integer maxSeq = jdbcTemplate.queryForObject(
                "select coalesce(max(sequence), 0) from ai_error_review_problem where package_id = ?",
                Integer.class,
                packageId
        );
        return maxSeq == null ? 0 : maxSeq;
    }

    private PackageContext loadPackageContext(String packageId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select error_taxonomy, evidence_summary::text as evidence_json from ai_error_review_package where id = ?",
                packageId
        );
        String taxonomy = String.valueOf(row.get("error_taxonomy"));
        List<String> rootCauses = extractRootCauses(stringValue(row.get("evidence_json")));
        return new PackageContext(taxonomy, rootCauses);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRootCauses(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) return List.of();
        try {
            Map<String, Object> evidence = objectMapper.readValue(evidenceJson, new TypeReference<>() {});
            Object raw = evidence.get("recent_root_causes");
            if (!(raw instanceof List<?> list)) return List.of();
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) result.add(String.valueOf(item));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record ReviewProblemRow(String id, Long problemId, int sequence, boolean submitted, Boolean isCorrect) {}

    record PackageContext(String errorTaxonomy, List<String> rootCauses) {}
}
