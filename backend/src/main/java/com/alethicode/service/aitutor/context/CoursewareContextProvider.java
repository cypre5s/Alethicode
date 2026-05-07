package com.alethicode.service.aitutor.context;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * 将 AI Tutor 对话中的 {@code @courseware:<lpId>} 引用解析为课件上下文摘要。
 *
 * <p>该能力跨越 aitutor 与 languagepack 包边界，集中处理两件事：
 * <ol>
 *   <li>访问控制：只接受当前用户可见的语言包，越权引用直接 fail fast。</li>
 *   <li>RAG 检索：对每个授权语言包检索 top-k 页面片段，结构与课件问答页保持一致。</li>
 * </ol>
 *
 * <p>Design: <code>docs/plans/2026-05-03-courseware-reference-token-design.md</code></p>
 */
public interface CoursewareContextProvider {

    /**
     * 解析原始引用列表中的 {@code @courseware:<lpId>} 子集。
     *
     * <p>非课件引用由 {@link ConversationContextService#resolveReferences} 处理，此方法只关注课件引用。</p>
     *
     * @param username 当前登录用户，用于查询可访问语言包
     * @param rawTokens 从用户消息中提取的原始引用字符串
     * @param currentQuery 当前问题文本，作为 RAG 查询
     * @param recentContext 可选的滚动上下文窗口
     * @return 每个已授权且去重的语言包对应一个 {@link CoursewareSummary}
     * @throws com.alethicode.exception.LegacyBusinessException 引用越权语言包时抛出 403
     */
    List<CoursewareSummary> resolveCoursewareReferences(
            String username,
            List<String> rawTokens,
            String currentQuery,
            @Nullable String recentContext
    );
}
