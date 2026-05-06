package com.alethicode.service.career.studio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain-Aware Project Studio 服务（plan 5.1 节）。
 *
 * <p>四个能力：
 * <ul>
 *   <li>{@link #recommendForUser}：基于学生 mastery 推荐微项目候选 KC 簇。</li>
 *   <li>{@link #generate}：LLM 出题 + critic + <strong>reference solution 真判题
 *       自验证</strong>，通过后落 {@code problem} + {@code career_micro_project}。</li>
 *   <li>{@link #listForUser}：拉学生最近 N 个微项目，按 created_at 倒序。</li>
 *   <li>{@link #findById}：拉单个微项目的完整投影（含 judge_problem_id）。</li>
 *   <li>{@link #markCompleted}：标记项目完成（同时写 PROJECT_COMPLETED 里程碑）。</li>
 * </ul>
 */
public interface MicroProjectStudioService {

    /** 基于 LearnerState mastery 推荐 1-3 个候选 KC 簇。 */
    List<MicroProjectRecommendation> recommendForUser(long userId);

    /** 生成微项目（LLM 出题 + reference solution 真判题自验证 + critic）。 */
    Optional<CareerMicroProject> generate(long userId, String majorCode, List<String> kcCodes);

    /** 拉学生最近 N 个微项目，按 created_at 倒序。 */
    List<CareerMicroProject> listForUser(long userId, int limit);

    /** 拉单个微项目（必须为该用户所有，否则抛 404）。 */
    Optional<CareerMicroProject> findById(long userId, long projectId);

    /** 标记项目完成（写 project_completed 里程碑 + 重激活 Why 报告）。 */
    void markCompleted(long projectId, double score);

    /**
     * 由 SubmissionService AC 路径回调：根据 (user_id, judge_problem_id) 反查
     * career_micro_project，存在则调 {@link #markCompleted}，写
     * project_completed 里程碑并触发 Why 报告重激活。
     *
     * @return true 表示真触发了 markCompleted，false 表示该 problem 不属于任何
     *         career_micro_project（即学生提交的是普通题目，非微项目）
     */
    boolean markCompletedByJudgeProblem(long userId, long judgeProblemId, double score);

    record MicroProjectRecommendation(List<String> kcCodes, String rationale) {
    }

    record CareerMicroProject(
            long id, long userId, String majorCode, String title,
            String briefMd, Long judgeProblemId, String status,
            Integer score, Instant createdAt, Instant completedAt
    ) {
    }
}
