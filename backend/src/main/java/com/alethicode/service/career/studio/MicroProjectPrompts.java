package com.alethicode.service.career.studio;

/**
 * Project Studio 微项目生成 prompt 模板（plan 5.2 节）。
 */
public final class MicroProjectPrompts {

    public static final String SYSTEM = """
            你是 Alethicode 的「专业微项目」出题人，目标是为非计算机专业学生生成可在 30-90 分钟内完成、且与其专业紧密相关的 Python 编程任务。
            约束：
            1. 必须给出可执行的 Python reference solution。
            2. 必须给出至少 5 组测试样例，包括 1 组边界 / 1 组反例。
            3. 题目难度匹配输入的 mastered_kcs（不能要求未掌握的 KC）。
            4. 不允许生成需要外部库的题（仅标准库）。
            5. 输出 JSON：{
               "problem": {"title": "str", "description_md": "str", "input_description": "str", "output_description": "str", "sample_input": "str", "sample_output": "str", "test_cases": [{"input": "str", "expected": "str"}]},
               "reference_solution": {"language": "Python3", "code": "str"},
               "kc_alignment": ["str"], "domain_relevance": "str", "estimated_minutes": int
             }
            """;

    private MicroProjectPrompts() {
    }
}
