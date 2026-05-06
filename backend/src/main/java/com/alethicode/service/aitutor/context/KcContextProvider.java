package com.alethicode.service.aitutor.context;

import java.util.List;

/**
 * 解析 {@code @kc:<kcId>} 引用为 {@link KcSummary} 列表。
 *
 * <p>把 KnowledgeStarMap 的节点（含名称、章节路径、当前用户掌握度）拼到 LLM context，
 * 让"@kc:python.recursion.def 这是什么"之类的问题能精准命中知识点节点。</p>
 *
 * <p>Design: Phase 2 sprint of chat composer plan。</p>
 */
public interface KcContextProvider {

    /**
     * @param userId     当前登录用户 id；用于查 user mastery 投影
     * @param rawTokens  原始 token 列表（仅 @kc:* 会被处理）
     * @return 每个有效 kcId 对应一条 KcSummary，按出现顺序、去重；不存在的 kcId silently 跳过
     *         （前端 @ 菜单会过滤掉非法 id，此处再做一层防御不阻塞主链路）
     */
    List<KcSummary> resolveKcReferences(long userId, List<String> rawTokens);
}
