package com.alethicode.service.aitutor.context;

import java.util.List;

/**
 * 解析 {@code @notebook:<entryId>} 引用为 {@link NotebookSummary} 列表。
 *
 * <p>鉴权：entryId 必须属于当前 userId，否则 fail-fast 抛 LegacyBusinessException。</p>
 *
 * <p>Design: Phase 2 sprint of chat composer plan。</p>
 */
public interface NotebookContextProvider {

    /**
     * @param userId     当前登录用户 id；NotebookEntry 是按 user 隔离的私有数据
     * @param rawTokens  原始 token 列表（仅 @notebook:* 会被处理）
     * @return 每个有效 entryId 对应一条 NotebookSummary，按出现顺序、去重
     */
    List<NotebookSummary> resolveNotebookReferences(long userId, List<String> rawTokens);
}
