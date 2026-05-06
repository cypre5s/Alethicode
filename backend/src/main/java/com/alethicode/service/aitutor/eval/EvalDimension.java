package com.alethicode.service.aitutor.eval;

/**
 * Evaluation dimensions for LLM-as-Judge rubric,
 * aligned with MRBench 8-dimension pedagogy assessment.
 */
public enum EvalDimension {
    FACTUAL_CORRECTNESS("事实正确性", "内容是否与 evidence 中的客观事实一致"),
    PEDAGOGICAL_FIT("教学适切性", "难度和表述是否匹配初学者水平"),
    SCAFFOLD_LEVEL_MATCH("脚手架层级匹配", "scaffold_level 是否与 mastery 对应"),
    ANSWER_LEAKAGE("答案泄露", "是否直接暴露完整解题代码"),
    GUIDANCE_QUALITY("引导质量", "是否给出逐步引导而非直接答案"),
    KC_ALIGNMENT("KC 对齐", "related_kcs 是否与题目知识点一致"),
    COMPREHENSIBILITY("可理解性", "语言是否清晰、初学者能读懂"),
    ENCOURAGEMENT("鼓励性", "是否包含合适的情感支持"),
    CAREER_GROUNDING("Career Bridging 证据扎根", "Why 报告事实声明是否具备 citations 与可追溯证据"),
    DOMAIN_DRIFT("Coding Lens 语义漂移", "专业化题面是否保持 IO schema 与样例语义不变"),
    PROJECT_SOLVABILITY("Project Studio 可解性", "微项目是否具备通过 Judge Server 自验证的标准题目"),
    PATH_UNLOCK_CONSISTENCY("Career Path 解锁一致性", "路径节点父子关系与 mastery 解锁阈值是否一致");

    private final String label;
    private final String description;

    EvalDimension(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }
}
