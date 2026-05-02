# AI 导学助手多语言感知改造方案

## Summary
将题目页 AI workflow 从“默认 Python、局部支持多语言”重构为“统一语言决策源 + 统一语言上下文 + 分能力适配器”的结构，首期覆盖 `Python3 / Java / C++ / C`。  
AI 的所有行为统一跟随学生当前编辑器语言，而不是固定跟随语言包主语言。前端继续保留“运行轨迹”入口与卡片心智；后端按语言返回该语言下最真实、最稳定的运行证据分析，不伪装成 Python 式逐步 tracer。

## Key Changes

### 1. 统一语言决策源
- 新增后端统一上下文对象，建议命名为 `LanguageAwareTutorContext`，至少包含：
  - `current_language`
  - `problem_supported_languages`
  - `problem_reference_solution_language`
  - `language_pack_id`
  - `language_pack_primary_language`
  - `audience`
- 所有 AI workflow 事件统一显式携带 `language`；后端禁止再从空值默认回退到 `Python3`。
- 前端题目页默认语言选择逻辑改为：
  - 优先当前编辑器语言
  - 否则用题目默认语言
  - 否则取题目支持语言列表第一个
  - 不再写死偏好 `Python3`
- `EvidencePackAssembler` 与 `problemContext` 必须补齐语言与语言包信息，不再只拼题面文本。

### 2. 统一 prompt / context 多语言化
- 将所有 `Python 初学者`、`Python骨架代码`、`Python 参考解` 之类 prompt 改为基于统一语言上下文动态生成。
- prompt 中同时明确两层信息：
  - `当前编程语言 = current_language`
  - `目标受众 = 非计算机专业初学者`
- 受影响能力统一改造为吃 `LanguageAwareTutorContext`，而不是各自拼字符串：
  - 题目导读
  - 思路分析
  - 骨架代码
  - Parsons
  - 错误诊断
  - AC 复盘
  - Chat
  - worked/faded/minimal scaffolding
- 语言包初始化链路继续以 `language_pack.primary_language` 生成题目与参考解，但题目页 AI 运行时始终以学生当前编辑器语言为准。

### 3. 深能力拆分为语言适配器
- 抽象出语言能力接口，至少拆成两类：
  - `ExecutionTraceService`
  - `CodeQualityAssessmentService`
- 增加语言路由器，例如 `LanguageCapabilityRouter`，按 `current_language` 分发到具体实现。
- Python：
  - 保留现有 `SimplePythonTracer` 真实逐步轨迹能力
  - 原 `PythonExecutionTraceService` 重命名/迁移到语言实现层
- Java / C++ / C：
  - 不做伪逐步 tracer
  - 通过真实编译/运行结果、编译错误、运行错误、输入输出差异、代码结构分析产出结构化“运行轨迹”载荷
  - 统一返回现有前端可消费的 `status / failure_reason / steps / divergence_step` 基本骨架；其中 `steps` 可表示关键执行证据步骤，而不是逐行变量快照
- `CodeQualityAssessmentService` 取消仅支持 `Python3` 的硬编码，改为按语言实现：
  - Python 继续现有评估维度
  - Java/C++/C 使用统一维度名 `readability / efficiency / style`，但提示词按语言规范生成
- `Skeleton / Parsons / Scaffolding` 必须按当前语言输出：
  - 骨架代码生成对应语言模板
  - Parsons 基于当前语言参考解或模板生成
  - 若题目不存在当前语言参考解/模板，直接 fail-fast，不做跨语言偷换

### 4. 复用现有 judge 作为真能力证据源
- 复用现有 `/api/debug` 与 judge 配置作为四语言真实编译/运行证据源，不新建第二套执行通道。
- 新增 AI 内部分析流程时，统一读取：
  - 当前代码
  - 当前语言
  - 调试输入样例
  - judge 返回的 `output / error / time_cost / memory_cost`
- `buildExecutionTraceExplainerPayload` 改为走语言路由，不再直接调用 Python 实现。
- 非 Python 语言的“运行轨迹”能力定义为：
  - 基于真实编译/运行结果
  - 再结合代码结构与失败证据生成结构化教学解释
  - 不允许伪造未真实观测到的变量值或中间步骤

### 5. 契约与前端保持稳定
- 前端继续保留“运行轨迹”入口、按钮和卡片类型，不拆分 Python / 非 Python 两套 UX。
- 后端 `execution_trace_explainer` 契约保持兼容：
  - `status`
  - `input_sample`
  - `steps`
  - `divergence_step`
  - `failure_reason`
- 允许 `steps` 在不同语言下语义不同，但必须可稳定渲染：
  - Python：逐步执行轨迹
  - Java/C++/C：关键执行证据步骤
- 现有与 workflow/UnifiedAgentPanel 相关的卡片与事件契约不改名，只扩充语言感知字段。

## Implementation Changes

### Backend
- 在 AI workflow 主链路引入统一语言上下文构建步骤，来源顺序固定为：
  1. workflow event `language`
  2. 当前 submission/code snapshot 语言
  3. problem 默认参考语言
  4. 若仍为空则直接报错
- `EvidencePackAssembler.loadProblemRecord` 补查：
  - `languages`
  - `reference_solution_language`
  - `language_pack_id`
  - 语言包的 `primary_language`
- 所有 `build*Payload` / `generate*ByLlm` / scaffolding 生成器都改为接收语言上下文。
- 语言相关硬编码清理范围包括：
  - `AITutorWorkflowAdminServiceImpl`
  - `PythonExecutionTraceService` 及其调用点
  - `CodeQualityAssessmentService`
  - `WorkedExampleGenerator`
  - `FadedExampleGenerator`
  - `FadedExampleAnswerEvaluator`
  - `MinimalHintGenerator`
  - 其他题目页 AI 主链路中的 Python 文案与默认值

### Frontend
- `Problem.vue` 的默认语言选择逻辑去 Python 偏置。
- `workflowStateMachine.js` 所有 workflow event payload 在需要时都显式带 `language`。
- 骨架代码、运行轨迹、AC 复盘、chat、错误诊断相关请求统一透传当前编辑器语言。
- 现有“运行轨迹”入口与卡片保持不变，只保证不同语言下也能请求并展示。

### Fail-fast Rules
- 当前语言不在题目允许语言中：直接拒绝 AI 请求。
- 当前语言缺少模板/参考解而该能力又依赖参考解：直接失败并给出明确原因。
- 非 Python 语言如果真实编译/运行失败且无法提取有效执行证据：允许返回失败态，但不能伪造轨迹。
- 不做跨语言兼容生成，不允许“学生在写 Java，AI 偷偷按 Python 回答”。

## Test Plan
- 后端单测/集成测试：
  - `LanguageAwareTutorContext` 构建优先级正确
  - `workflow event language` 缺失时按规则报错，不再默认 `Python3`
  - 四语言下题目导读 / 思路分析 / chat prompt 均带正确语言
  - 四语言下骨架代码生成结果语言正确
  - 四语言下 Parsons/scaffolding 使用当前语言参考解
  - `ExecutionTraceService` 路由正确
  - Python 返回真实逐步轨迹；Java/C++/C 返回真实运行证据分析，不为空壳
  - `CodeQualityAssessmentService` 四语言均可评估
- 前端契约测试：
  - workflow 事件统一透传 `language`
  - 默认语言选择不再偏向 `Python3`
  - “运行轨迹”入口在四语言题目下都可触发
  - 现有 `execution_trace_explainer`、`post_ac`、`chat`、`skeleton` 契约测试按新语言字段更新
- 端到端场景：
  - Python 题：保持现有体验不回退
  - Java 题：导学、骨架、Parsons、错误诊断、运行轨迹、复盘全部按 Java
  - C++ 题：同上
  - C 题：同上
  - 学生切换编辑器语言后，再次请求 AI，返回内容立即跟随切换
  - 题目支持多语言但某语言无参考解时，对依赖参考解的能力直接 fail-fast

## Assumptions
- 首期目标语言固定为 `Python3 / Java / C++ / C`。
- AI 运行时语言以学生当前编辑器语言为唯一主决策源。
- 前端继续统一使用“运行轨迹”命名，不区分语言。
- “真能力”定义为 `真实编译/运行 + 结构化分析`，不要求四语言都做调试器级逐步执行。
- 语言包初始化链路仍以 `primary_language` 生成题目与参考解；题目页 AI 与语言包初始化属于两个不同的语言决策层级。
