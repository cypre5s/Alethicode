package com.alethicode.service.career.path;

/**
 * Career Path Map 服务（plan 6.1 节）。
 */
public interface CareerPathService {

    /**
     * 组装某专业下的完整路径视图，含拓扑排序 + 学生当前 mastery + 解锁状态。
     */
    CareerPathView buildView(long userId, String majorCode);

    /**
     * 标记某节点已解锁（写里程碑 path_node_unlocked）。
     */
    void markNodeUnlocked(long userId, String majorCode, String kcCode);
}
