package com.alethicode.service.aitutor.parsons;

/**
 * Parsons 拼装题的一个代码块。
 *
 * @param id            序列化 ID（B0/B1/...），跨 dispatch 与 grade 稳定
 * @param code          完整代码字符串（含 indent 之外的内容）
 * @param indent        缩进层级（0 起算，每级 4 空格）
 * @param fadingState   visible / faded / hidden
 * @param fadeHint      faded/hidden 时给学生的提示（visible 时为 null）
 */
public record ParsonsBlock(
        String id,
        String code,
        int indent,
        FadingState fadingState,
        String fadeHint
) {
    public enum FadingState {
        VISIBLE, FADED, HIDDEN;

        public String key() {
            return name().toLowerCase();
        }
    }
}
