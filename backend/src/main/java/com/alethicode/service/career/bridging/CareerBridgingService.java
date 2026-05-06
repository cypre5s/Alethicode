package com.alethicode.service.career.bridging;

import com.alethicode.dto.response.CareerMajorOption;
import com.alethicode.dto.response.CareerProfileView;

import java.util.List;
import java.util.Optional;

/**
 * Career Bridging Why 层主入口（plan 3.2 节）。
 *
 * <p>三个能力：
 * <ol>
 *   <li>{@link #ensureProfile} 写入学生专业 + 学习目标，并对「首次提交某专业」
 *   插入 enrollment 里程碑；</li>
 *   <li>{@link #generateForMilestone} 对未消费的里程碑生成一份 Why 报告，
 *   走 {@link com.alethicode.service.aitutor.rollout.RolloutPolicyService} 决定
 *   是否进 treatment 组、走 {@link com.alethicode.service.ai.AiModelGateway}
 *   生成、走 {@link com.alethicode.service.aitutor.reflection.ReflectionService}
 *   过 critic；</li>
 *   <li>{@link #recentReports} 给学生主页展示最近 N 份报告。</li>
 * </ol>
 *
 * <p>本接口是模块 1 的稳定 API；后续 todo 10 / 13 会增加 KC / 章节 / 项目
 * 完成等里程碑触发器，但调用者只看到 {@link #recordMilestone}，不直接 INSERT。
 */
public interface CareerBridgingService {

    /**
     * 写入或更新学生专业档案；首次提交某专业时插入 enrollment 里程碑。
     *
     * @param userId       学生 id
     * @param majorCode    专业代码（必须命中 {@code career_major_dictionary.code}）
     * @param careerIntent 学习目标自由文本，可空
     * @return 本次调用是否新建了一条 enrollment 里程碑（true=新建，
     *         false=之前已经填过同一专业，未触发）
     */
    EnrollmentResult ensureProfile(long userId, String majorCode, String careerIntent);

    /**
     * 显式登记一条里程碑（被未来 todo 10/13 的 KC 毕业 / 章节进入 / 项目完成
     * 路径调用）。如已存在 (user, type, ref) 三元组则返回原 id 不重复插入。
     */
    long recordMilestone(long userId, MilestoneType milestoneType, String milestoneRef);

    /**
     * 为里程碑生成 Why 报告：control 组返回 empty；treatment 组写库后返回报告。
     */
    Optional<CareerBridgingReport> generateForMilestone(long userId, long milestoneId);

    /** 拉学生最近 N 份报告，按 created_at 倒序。 */
    List<CareerBridgingReport> recentReports(long userId, int limit);

    /**
     * 读学生当前的 Career Bridging 档案视图（专业 + 学习目标 + 完成时间），
     * 给主页 CareerProgressCard / CareerProfilePage 表单回显用。未填专业时
     * majorCode = null。
     */
    Optional<CareerProfileView> findProfile(long userId);

    /** 列出可选的专业下拉项（仅启用项），按 discipline + name_zh 排序。 */
    List<CareerMajorOption> listMajors();

    record EnrollmentResult(boolean newlyEnrolled, long milestoneId, String majorCode) {
    }
}
