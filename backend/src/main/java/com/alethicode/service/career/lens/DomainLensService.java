package com.alethicode.service.career.lens;

import java.util.List;
import java.util.Optional;

/**
 * Coding Lens：题面专业化重写缓存服务（plan 4.1 节）。
 *
 * <p>核心约束（plan 2.3 + 4.1）：
 * <ul>
 *   <li>判题不动，本服务不持有 test_case_id，不修改 problem 表的 IO schema</li>
 *   <li>IO schema 不变 + 测试样例语义不偏移 由 Reflection critic 强制守门</li>
 *   <li>critic 不通过 → 不写库，前端回退原版</li>
 * </ul>
 */
public interface DomainLensService {

    /**
     * 命中缓存返回已校验变体；缓存未命中走 LLM 生成 + Reflection critic。
     * critic 不通过或 rollout 决策为回退时返回 empty。
     */
    /**
     * 按 (problem, major, user) 三元组返回专业化变体。
     *
     * @param problemId 题目 id
     * @param majorCode 学生专业代码
     * @param userId    学生 id（用于 plan 9 节 A/B 分组：experiment_id={@code coding_lens_v1}，
     *                  treatment_rate=0.3，control 组返回空让前端回退原版题面）
     */
    Optional<ProblemDomainVariant> findOrGenerate(long problemId, String majorCode, long userId);

    /** 教师锁定某变体（考试模式下不允许 LLM 重新生成）。 */
    void lockForExam(long variantId, long teacherId);

    /** 教师后台查看最近生成的变体，majorCode 为空时返回所有专业。 */
    List<ProblemDomainVariant> listVariants(String majorCode, int limit);

    /** 使某题的所有变体失效（题目内容被修改后调用）。 */
    void invalidate(long problemId);
}
