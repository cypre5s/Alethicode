package com.alethicode.service.career.path;

import java.util.List;

/**
 * Career Path Map 单个 KC 节点的视图。
 *
 * @param kcCode       KC 代码（复用现有 KC 体系）
 * @param parentKcCode 父节点 KC 代码，null 表示根节点
 * @param whyMd        「该 KC 在此专业的 Why」说明文
 * @param typicalUseCases 典型场景列表
 * @param mastery      学生当前 mastery（0.0-1.0），null 表示无数据
 * @param status       节点状态：unlocked / in_progress / locked
 */
public record CareerPathNodeView(
        String kcCode,
        String parentKcCode,
        String whyMd,
        List<String> typicalUseCases,
        Double mastery,
        String status,
        int sortOrder
) {
}
