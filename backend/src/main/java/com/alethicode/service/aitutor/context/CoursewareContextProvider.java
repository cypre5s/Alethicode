package com.alethicode.service.aitutor.context;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Resolves {@code @courseware:<lpId>} references inside an AI tutor chat message into
 * {@link CoursewareSummary} bundles ready to be injected into the LLM prompt.
 *
 * <p>Owns two responsibilities the {@link ConversationContextService} could not absorb
 * cleanly because they cross the aitutor / languagepack package boundary:
 * <ol>
 *   <li><strong>Access control</strong> — only language packs the user can see in
 *       {@code LanguagePackQaService.listQaPacks(username)} are honoured; out-of-scope
 *       lp ids fail-fast with HTTP 403, never silently dropped.</li>
 *   <li><strong>RAG retrieval</strong> — for each authorised lp id, a top-k page-chunk
 *       retrieval is performed via the existing {@code PageRetrievalService.retrieve}
 *       (the same one that powers <code>/language-pack-qa</code>) so the chunk shape
 *       mirrors what the courseware QA page returns.</li>
 * </ol>
 *
 * <p>Design: <code>docs/plans/2026-05-03-courseware-reference-token-design.md</code></p>
 */
public interface CoursewareContextProvider {

    /**
     * Resolve the {@code @courseware:<lpId>} subset of the raw reference list.
     *
     * <p>Tokens that are not courseware references are silently ignored (this method only
     * cares about courseware refs); the non-courseware refs are still handled by
     * {@link ConversationContextService#resolveReferences}.</p>
     *
     * @param username        currently logged-in user; used to look up the per-user allowed
     *                        language pack list ({@code LanguagePackQaService.listQaPacks})
     * @param rawTokens       raw reference strings extracted from the user message; can mix
     *                        {@code @card:*}, {@code @last_*}, {@code @courseware:*} freely
     * @param currentQuery    the user's current question text, used as the RAG query
     * @param recentContext   optional rolling context window passed to the retriever for
     *                        better recall; pass {@code null} when not available
     * @return one {@link CoursewareSummary} per authorised, distinct lp id (preserved insertion
     *         order); empty list when no {@code @courseware:*} tokens are present
     * @throws com.alethicode.exception.LegacyBusinessException 403 when the message references a
     *         language pack the user is not allowed to access
     */
    List<CoursewareSummary> resolveCoursewareReferences(
            String username,
            List<String> rawTokens,
            String currentQuery,
            @Nullable String recentContext
    );
}
