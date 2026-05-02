# 多语言感知 AI 导学助手设计

## 目标

把题目页 AI workflow 从默认 Python 的单语言实现，改造成面向 `Python3 / Java / C++ / C` 的多语言感知体系。

本轮只解决题目页 AI 主链路：

- AI 的当前语言统一跟随学生编辑器语言
- prompt、骨架、Parsons、错误诊断、聊天、AC 复盘都按当前语言生成
- 执行轨迹与代码质量分析抽象为语言适配器
- 非 Python 语言的“运行轨迹”基于真实编译/运行证据生成结构化分析，不伪装成逐步 tracer

## 统一语言决策规则

### 当前语言

`current_language` 的决策顺序固定为：

1. 当前 workflow event 显式传入的 `language`
2. 当前 submission / code snapshot 的 `language`
3. 题目的 `reference_solution_language`
4. 题目支持语言列表的第一个

若以上都为空，直接 fail-fast。

### 语言包信息

题目上下文补齐以下字段：

- `problem_supported_languages`
- `problem_reference_solution_language`
- `language_pack_id`
- `language_pack_primary_language`
- `audience`

其中 `audience` 统一表达为：`非计算机专业的 <语言> 初学者`。

## 能力分层

### 通用层

- `LanguageAwareTutorContext`
- `TutorLanguageSupport`
- `EvidencePackAssembler.buildProblemContext`

职责：

- 统一语言规范化与展示文案
- 为所有 prompt 提供同一份语言上下文

### 运行轨迹层

- `ExecutionTraceService`
- `LanguageRoutedExecutionTraceService`
- `PythonExecutionTraceService`
- `JudgeBackedExecutionTraceService`

规则：

- Python 保留当前真实逐步轨迹
- Java/C++/C 调用 judge 产生真实编译/运行证据，再生成结构化步骤
- 前端统一继续叫“运行轨迹”

### 代码质量层

- `CodeQualityAssessmentService`
- `LanguageRoutedCodeQualityAssessmentService`
- `PythonCodeQualityAssessmentService`
- `GenericCodeQualityAssessmentService`

规则：

- 统一输出维度：`readability / efficiency / style`
- Python 仍使用 Python 导向 prompt
- Java/C++/C 使用各自语言规范导向 prompt

## Fail-fast 约束

- workflow 请求未传语言且无法从上下文解析时直接失败
- 依赖参考解的能力在当前语言缺失参考解/模板时直接失败
- 非 Python 语言无法获取真实运行证据时，可返回失败态，但不允许伪造中间变量轨迹
- 不允许学生当前写 Java 时返回 Python 骨架或 Python 诊断
