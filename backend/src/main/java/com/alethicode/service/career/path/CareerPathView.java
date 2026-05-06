package com.alethicode.service.career.path;

import java.util.List;

/**
 * Career Path Map 完整视图。
 *
 * @param majorCode 专业代码
 * @param majorNameZh 专业中文名
 * @param nodes 拓扑排序后的节点列表（根 → 叶）
 */
public record CareerPathView(
        String majorCode,
        String majorNameZh,
        List<CareerPathNodeView> nodes
) {
}
