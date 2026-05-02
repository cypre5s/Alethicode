package com.alethicode.service.aitutor.profile;

/**
 * 学生教学风格偏好枚举。
 *
 * <p>四种风格覆盖常见的 Kolb/学习理论维度的简化映射：
 * <ul>
 *   <li>{@link #STEP_BY_STEP}：默认值；适合大多数初学者，要求 agent 给出分步指导；</li>
 *   <li>{@link #EXPLORATORY}：喜欢先自己尝试，要求 agent 给最少提示；</li>
 *   <li>{@link #VISUAL}：喜欢样例 / 数据流图，要求 agent 先给类比题或可视化说明；</li>
 *   <li>{@link #ANALYTICAL}：喜欢严谨推理，要求 agent 把因果与前置条件展开说清楚。</li>
 * </ul>
 *
 * <p>推断逻辑在 {@link LearnerMemoryService#inferLearningStyle(Long)}；数据源为
 * {@code ai_learner_memory} 表里 {@code memory_type='teaching_strategy_preference'} 的
 * positive/negative 反馈。
 */
public enum LearningStyle {

    STEP_BY_STEP(
            "step_by_step",
            "【学生偏好】喜欢被逐步引导：请把解题步骤按 1/2/3 编号列出，每一步说清楚目的和验证方式，避免一口气给出完整结论。"
    ),
    EXPLORATORY(
            "exploratory",
            "【学生偏好】喜欢自己先尝试再要提示：请只给最小化提示（下一步的方向 / 一个关键问题），不要给整段代码或完整答案。"
    ),
    VISUAL(
            "visual",
            "【学生偏好】喜欢看样例或数据流图：优先给一个结构类似的小例题/样例输入输出/状态表，再让学生对照迁移，不要只讲抽象概念。"
    ),
    ANALYTICAL(
            "analytical",
            "【学生偏好】喜欢严谨推理：请把因果、前置条件、边界条件显式列出，并在每个推断处指出依据，避免跳步。"
    );

    private final String key;
    private final String promptPrefix;

    LearningStyle(String key, String promptPrefix) {
        this.key = key;
        this.promptPrefix = promptPrefix;
    }

    public String key() {
        return key;
    }

    public String toPromptPrefix() {
        return promptPrefix;
    }

    public static LearningStyle fromKey(String key) {
        if (key == null) return STEP_BY_STEP;
        String normalized = key.trim().toLowerCase();
        for (LearningStyle style : values()) {
            if (style.key.equals(normalized)) {
                return style;
            }
        }
        return STEP_BY_STEP;
    }
}
