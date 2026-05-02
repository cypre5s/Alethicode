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
 * Enforces the business-level preconditions that guard every tutor workflow API:
 * problem accessibility, allowed language for the chosen problem, and submission
 * ownership for runs that depend on a submission.
 *
 * <p>All checks use fail-fast exceptions so the controller layer can map them to
 * stable HTTP status codes (403 / 404 / 409 / 422) without branching on business
 * details.
 */
@Service
public class TutorWorkflowAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(TutorWorkflowAuthorizer.class);

    /** Judge result code for Accepted. Mirrors the legacy Python OJ and V5 schema. */
    private static final int AC_RESULT_CODE = 0;

    /** Cache name registered by {@link com.alethicode.config.MultiTierCacheConfig}. */
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
     * Loaded view of the minimum problem fields needed to authorize tutor runs.
     *
     * <p>{@code languagesCorrupt=true} means the DB row's {@code languages} JSON could
     * not be parsed; the authorizer must refuse with a dedicated error message so
     * operators can diagnose the row instead of seeing a generic "no languages".
     */
    public record ProblemAccess(long problemId, long ownerId, boolean visible, boolean isPublic,
                                 Set<String> allowedLanguages, boolean languagesCorrupt) {
        public boolean isOwnedBy(long userId) {
            return ownerId == userId;
        }
    }

    /** Dedicated exception types so controllers can map to precise HTTP statuses. */
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
     * Assert the user can open a tutor session on the given problem with the requested language.
     * Throws {@link ProblemNotFound} / {@link AccessDenied} / {@link LanguageNotAllowed}.
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
            // Problem row carries no explicit language list — treat as misconfigured rather than
            // silently accept any input.
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
     * Intermediate parse result. Uses a boolean flag instead of exceptions so
     * callers can surface "corrupt JSON" with a dedicated error message.
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
     * Assert that the submission exists and belongs to the given user/problem pair.
     * Returns the submission metadata for callers that need the AC check next.
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
     * Cache-fronted single-flight lookup for {@link ProblemAccess}.
     *
     * <p>Penetration defense: a missing problem is cached as {@link Optional#empty()}
     * so a malicious or buggy caller hammering an unknown problem id only triggers
     * one DB query per cache TTL. Avalanche defense lives in
     * {@code MultiTierCacheConfig.JitteredExpiry}, which spreads expirations across
     * a {@code [base, base * 1.3]} window so cold-warmed entries do not all drop at once.
     *
     * <p>The cache must exist (registered by {@link com.alethicode.config.MultiTierCacheConfig});
     * a missing cache is a configuration error, not a fall-through condition.
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
