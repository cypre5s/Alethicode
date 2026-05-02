package com.alethicode.service.languagepack.quality;

/**
 * Reference solution lint 的可选上下文：
 * REF002（浮点限位）和 REF007（中英文标点一致）需要题面文本作为参考标准。
 * 没有上下文时，这两条规则被跳过——init 流水线总是有题面，不会触发跳过；
 * 单元测试可只构造 reference 代码而不带上下文，用于聚焦单条规则。
 */
public record ReferenceLintContext(
        String description,
        String inputDescription,
        String outputDescription
) {
    public static ReferenceLintContext empty() {
        return new ReferenceLintContext("", "", "");
    }

    public boolean hasOutputDescription() {
        return outputDescription != null && !outputDescription.isBlank();
    }

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }
}
