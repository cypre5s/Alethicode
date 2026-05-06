package com.alethicode.service.aitutor.context;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * 解析 {@code @page:<lpId>:<n>} / {@code @page:<n>} 引用为 {@link PageSummary} 列表，
 * 让 AI 导学助手 / 课件问答页都能把"具体某一页课件"塞进 LLM prompt。
 *
 * <p>与 {@link CoursewareContextProvider} 相比，本 provider 不做 RAG top-k 检索，
 * 直接读取指定页的全文（page text）。鉴权：用户必须能在 {@code listQaPacks} 看到该
 * lp，越权 fail-fast 抛 LegacyBusinessException。</p>
 *
 * <p>Design: Phase 2 sprint of chat composer plan。</p>
 */
public interface PageContextProvider {

    /**
     * @param username        当前登录用户，用于检查 lp 可见性
     * @param defaultLpId     课件问答页的当前 lp id；当 token 形如 {@code @page:<n>} 省略 lpId
     *                        时，由调用方注入；AI 导学助手里无默认值，应传 {@code null}
     * @param rawTokens       原始 token 列表（可混合各种 @ 引用，本 provider 只关心 @page:*）
     * @return 一个 PageSummary 对应一条 @page 引用（按出现顺序、去重）
     */
    List<PageSummary> resolvePageReferences(
            String username,
            @Nullable Long defaultLpId,
            List<String> rawTokens
    );
}
