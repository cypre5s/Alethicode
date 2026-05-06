package com.alethicode.service.aitutor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个对话会话的 token / 上下文用量摘要。
 *
 * <p>AI 导学助手 (ai_tutor_workflow_session) 与课件问答 (language_pack_chat_session)
 * 共享同一份 DTO，让前端 ContextUsageBar 组件在两侧走完全相同的接口约定。</p>
 *
 * <p>tokensLimit == 0 表示数据尚未接入（schema 预留期），前端用
 * v-if="contextUsage && contextUsage.tokens_limit" 兜底不渲染彩条。</p>
 *
 * <p>Phase 1 chat composer plan 1.7 节定义。</p>
 */
public record SessionUsage(
        long tokensUsed,
        long tokensLimit,
        String modelName,
        Instant lastUpdated
) {

    public SessionUsage {
        if (tokensUsed < 0) tokensUsed = 0;
        if (tokensLimit < 0) tokensLimit = 0;
        if (modelName == null) modelName = "";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tokens_used", tokensUsed);
        m.put("tokens_limit", tokensLimit);
        m.put("model_name", modelName);
        m.put("last_updated", lastUpdated == null ? null : lastUpdated.toString());
        return m;
    }
}
