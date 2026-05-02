package com.alethicode.service.aitutor.parsons;

/**
 * Parsons 拼装题的干扰块。
 *
 * @param id        序列化 ID（D0/D1/...）
 * @param code      代码字符串
 * @param indent    缩进层级
 * @param source    {@link Source#NOTEBOOK}（来自该学生历史错题）或 {@link Source#LLM}（受控生成兜底）
 * @param kcHint    可选的 KC 关联标签
 */
public record ParsonsDistractor(
        String id,
        String code,
        int indent,
        Source source,
        String kcHint
) {
    public enum Source {
        NOTEBOOK, LLM;

        public String key() {
            return name().toLowerCase();
        }
    }
}
