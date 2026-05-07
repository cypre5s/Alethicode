package com.alethicode.service.aitutor.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 校验导学工作流 API 的业务前置条件。
 *
 * <p>题目可访问性、语言白名单和提交所有权都使用 fail-fast 异常，控制器只负责映射稳定 HTTP 状态。</p>
 */
@Service
public class TutorWorkflowAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(TutorWorkflowAuthorizer.class);

    /** AC 在旧 Python OJ 与 V5 schema 中的判题结果码。 */
    private static final int AC_RESULT_CODE = 0;

    /** {@link com.alethicode.config.MultiTierCacheConfig} 注册的题目访问缓存名。 */
    static final String PROBLEM_ACCESS_CACHE = "problemAccess";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    public TutorWorkflowAuthorizer(NamedParameterJdbcTemplate jdbc, CacheManager cacheManager) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
        this.cacheManager = cacheManager;
    }

    /**
     * 导学运行鉴权所需的最小题目字段视图。
     *
     * <p>{@code languagesCorrupt=true} 表示数据库 {@code languages} JSON 无法解析，必须返回专门错误，
     * 便于排查脏数据而不是误报“无语言配置”。</p>
     */
    public record ProblemAccess(long problemId, long ownerId, boolean visible, boolean isPublic,
                                 Set<String> allowedLanguages, boolean languagesCorrupt) {
        public boolean isOwnedBy(long userId) {
            return ownerId == userId;
        }
    }

    /** 控制器用这些异常类型映射精确 HTTP 状态。 */
    public static class ProblemNotFound extends RuntimeException {
        public ProblemNotFound(long problemId) {
            super("Problem not found: " + problemId);
        }
    }

    public static class AccessDenied extends RuntimeException {
        public AccessDenied(String message) {
            super(message);
        }
    }

    public static class LanguageNotAllowed extends RuntimeException {
        public LanguageNotAllowed(String message) {
            super(message);
        }
    }

    public static class SubmissionNotFound extends RuntimeException {
        public SubmissionNotFound(String submissionId) {
            super("Submission not found: " + submissionId);
        }
    }

    public static class SubmissionMismatch extends RuntimeException {
        public SubmissionMismatch(String message) {
            super(message);
        }
    }

    public static class SubmissionNotAccepted extends RuntimeException {
        public SubmissionNotAccepted(String message) {
            super(message);
        }
    }

    /**
     * 断言用户可以用指定语言为目标题目打开导学会话。
     */
    public ProblemAccess assertProblemAccessible(long problemId, long userId, String language) {
        ProblemAccess access = lookupProblemAccess(problemId)
                .orElseThrow(() -> new ProblemNotFound(problemId));
        boolean canAccess = access.visible() || access.isPublic() || access.isOwnedBy(userId);
        if (!canAccess) {
            throw new AccessDenied("Problem " + problemId + " is not accessible to user " + userId);
        }
        assertLanguageAllowed(access, language);
        return access;
    }

    public void assertLanguageAllowed(ProblemAccess access, String language) {
        if (language == null || language.isBlank()) {
            throw new LanguageNotAllowed("language is required");
        }
        if (access.languagesCorrupt()) {
            throw new LanguageNotAllowed(
                    "Problem " + access.problemId() + " has corrupt language metadata; cannot start a tutor run");
        }
        if (access.allowedLanguages().isEmpty()) {
            // 题目缺少显式语言列表时视为配置错误，不能默认接受任意语言。
            throw new LanguageNotAllowed(
                    "Problem " + access.problemId() + " has no configured languages; cannot start a tutor run");
        }
        if (!access.allowedLanguages().contains(language)) {
            throw new LanguageNotAllowed(
                    "Language '" + language + "' not allowed for problem " + access.problemId());
        }
    }

    private ProblemAccess loadProblemAccess(long problemId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, created_by_id, visible, is_public, languages::text AS languages_json " +
                        "FROM problem WHERE id = :pid",
                new MapSqlParameterSource("pid", problemId)
        );
        if (rows.isEmpty()) throw new ProblemNotFound(problemId);

        Map<String, Object> row = rows.get(0);
        long ownerId = row.get("created_by_id") == null ? -1L : ((Number) row.get("created_by_id")).longValue();
        boolean visible = Boolean.TRUE.equals(row.get("visible"));
        boolean isPublic = Boolean.TRUE.equals(row.get("is_public"));
        ParsedLanguages parsed = parseLanguages(row.get("languages_json"));
        return new ProblemAccess(problemId, ownerId, visible, isPublic, parsed.languages(), parsed.corrupt());
    }

    /**
     * 中间解析结果，用布尔标记保留“JSON 损坏”的专门错误语义。
     */
    private record ParsedLanguages(Set<String> languages, boolean corrupt) {
        private static ParsedLanguages none() {
            return new ParsedLanguages(Set.of(), false);
        }
        private static ParsedLanguages corruptMarker() {
            return new ParsedLanguages(Set.of(), true);
        }
    }

    private ParsedLanguages parseLanguages(Object raw) {
        if (raw == null) return ParsedLanguages.none();
        String text = raw.toString();
        if (text.isEmpty()) return ParsedLanguages.none();
        try {
            List<String> list = objectMapper.readValue(text, new TypeReference<List<String>>() {});
            return new ParsedLanguages(new HashSet<>(list), false);
        } catch (Exception e) {
            log.warn("Corrupt problem.languages JSON (len={}): {}", text.length(), e.getMessage());
            return ParsedLanguages.corruptMarker();
        }
    }

    /**
     * 断言提交存在且属于指定用户和题目，并返回后续 AC 校验所需元数据。
     */
    public SubmissionRef assertSubmissionBelongsTo(String submissionId, long userId, long problemId) {
        if (submissionId == null || submissionId.isBlank()) {
            throw new IllegalArgumentException("submission_id is required");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, user_id, problem_id, result FROM submission WHERE id = :sid",
                new MapSqlParameterSource("sid", submissionId)
        );
        if (rows.isEmpty()) throw new SubmissionNotFound(submissionId);

        Map<String, Object> row = rows.get(0);
        long subUserId = row.get("user_id") == null ? -1L : ((Number) row.get("user_id")).longValue();
        long subProblemId = row.get("problem_id") == null ? -1L : ((Number) row.get("problem_id")).longValue();
        Integer result = row.get("result") == null ? null : ((Number) row.get("result")).intValue();

        if (subUserId != userId) {
            throw new SubmissionMismatch("Submission " + submissionId + " does not belong to user " + userId);
        }
        if (subProblemId != problemId) {
            throw new SubmissionMismatch("Submission " + submissionId + " does not belong to problem " + problemId);
        }
        return new SubmissionRef(submissionId, subUserId, subProblemId, result);
    }

    public void assertSubmissionAccepted(SubmissionRef submission) {
        if (submission.result() == null || submission.result() != AC_RESULT_CODE) {
            throw new SubmissionNotAccepted(
                    "Submission " + submission.submissionId() + " is not AC (result=" + submission.result() + ")");
        }
    }

    public Optional<ProblemAccess> tryLoadProblem(long problemId) {
        return lookupProblemAccess(problemId);
    }

    /**
     * 带缓存的 {@link ProblemAccess} 单飞查询。
     *
     * <p>不存在的题目会缓存为 {@link Optional#empty()}，减少异常 problemId 的穿透查询。
     * 过期抖动由 {@code MultiTierCacheConfig.JitteredExpiry} 统一处理，避免批量缓存同时失效。</p>
     *
     * <p>缓存缺失是配置错误，不能静默绕过。</p>
     */
    Optional<ProblemAccess> lookupProblemAccess(long problemId) {
        Cache cache = cacheManager.getCache(PROBLEM_ACCESS_CACHE);
        if (cache == null) {
            throw new IllegalStateException(
                    "Cache '" + PROBLEM_ACCESS_CACHE + "' not registered; check MultiTierCacheConfig");
        }
        @SuppressWarnings("unchecked")
        Optional<ProblemAccess> cached = (Optional<ProblemAccess>) cache.get(problemId, () -> {
            try {
                return Optional.of(loadProblemAccess(problemId));
            } catch (ProblemNotFound e) {
                return Optional.<ProblemAccess>empty();
            }
        });
        return cached == null ? Optional.empty() : cached;
    }

    public record SubmissionRef(String submissionId, long userId, long problemId, Integer result) {
    }
}
