# 工作流 Illegal Workflow Transition 对照表

## 文档目的

本表用于统一说明 OJ AI Tutor 工作流中，哪些 `current_phase -> event` 会触发后端错误：

```text
Illegal workflow transition: <CURRENT_PHASE> -> <EVENT>
```

判定依据来自后端真实生效的迁移策略，而不是前端按钮文案或临时约定。

## 判定来源

- 阶段枚举：`backend/src/main/java/com/alethicode/service/aitutor/contract/Phase.java`
- 事件枚举：`backend/src/main/java/com/alethicode/service/aitutor/contract/WorkflowEvent.java`
- 待确认动作：`backend/src/main/java/com/alethicode/service/aitutor/contract/PendingHumanAction.java`
- 迁移策略：`backend/src/main/java/com/alethicode/service/aitutor/policy/TransitionPolicy.java`

## 非法迁移总表

说明：

- `CALIBRATING` 不在任何一个运行阶段的允许集合内，所以对所有阶段都非法。
- 下表中的“非法事件列表”是基础非法集合。
- “条件补充”描述的是即使某事件在基础集合中合法，也会因为 `pending_human_action` 被额外判为非法的情况。

| 当前阶段 `current_phase` | 允许事件 | 非法事件列表 | 非法数量 | 条件补充 |
| --- | --- | --- | ---: | --- |
| `READING` | `READING`, `IDEATING`, `SCAFFOLDING`, `ERROR_FEEDBACK`, `AC_REVIEW`, `CHAT`, `AGENT_FEEDBACK` | `CALIBRATING`, `CODING`, `TRANSFER` | 3 | 无 |
| `IDEATING` | `IDEATING`, `SCAFFOLDING`, `ERROR_FEEDBACK`, `AC_REVIEW`, `CHAT`, `AGENT_FEEDBACK` | `CALIBRATING`, `READING`, `CODING`, `TRANSFER` | 4 | 无 |
| `SCAFFOLDING` | `SCAFFOLDING`, `CODING`, `TRANSFER`, `ERROR_FEEDBACK`, `AC_REVIEW`, `CHAT`, `AGENT_FEEDBACK` | `CALIBRATING`, `READING`, `IDEATING` | 3 | 当 `pending_human_action = confirm_scaffold` 且 `event = CODING` 但 `event_data.parsons_skipped != true` 时，`SCAFFOLDING -> CODING` 也会被判为非法 |
| `CODING` | `CODING`, `ERROR_FEEDBACK`, `AC_REVIEW`, `CHAT`, `AGENT_FEEDBACK` | `CALIBRATING`, `READING`, `IDEATING`, `SCAFFOLDING`, `TRANSFER` | 5 | 无 |
| `ERROR_FEEDBACK` | `ERROR_FEEDBACK`, `READING`, `IDEATING`, `SCAFFOLDING`, `CODING`, `AC_REVIEW`, `CHAT`, `AGENT_FEEDBACK` | `CALIBRATING`, `TRANSFER` | 2 | 无 |
| `AC_REVIEW` | `AC_REVIEW`, `TRANSFER`, `CHAT`, `AGENT_FEEDBACK` | `CALIBRATING`, `READING`, `IDEATING`, `SCAFFOLDING`, `CODING`, `ERROR_FEEDBACK` | 6 | 无 |
| `TRANSFER` | `TRANSFER`, `CODING`, `ERROR_FEEDBACK`, `AC_REVIEW`, `CHAT`, `AGENT_FEEDBACK` | `CALIBRATING`, `READING`, `IDEATING`, `SCAFFOLDING` | 4 | 当 `pending_human_action = confirm_transfer` 且 `event = CODING` 时，`TRANSFER -> CODING` 也会被判为非法 |

## 额外说明

### 1. 为什么会出现“看起来合法，但仍报 Illegal workflow transition”

常见原因有两个：

- 前端把当前阶段判断错了，实际还在 `READING`，却直接发了 `CODING`
- 当前阶段虽然允许 `CODING`，但同时存在待确认动作：
  - `confirm_scaffold`
  - `confirm_transfer`

这两种都属于后端显式拒绝，不是偶发问题。

### 2. 枚举外输入也会报同类错误

如果 `current_phase` 或 `event` 本身不在后端枚举中，后端同样会抛出：

```text
Illegal workflow transition: <RAW_PHASE> -> <RAW_EVENT>
```

这类错误不是“阶段关系非法”，而是“输入值根本不合法”。

### 3. 当前前端已对齐后端策略

前端工作流状态机已新增与本表一致的 fail-fast 校验：

- 非法迁移在请求发出前直接拦截
- `READING -> CODING` 不会再被前端误发
- `SCAFFOLDING -> CODING` 只有显式 `parsons_skipped=true` 才放行

## 前端 UI 入口对照表

### 适用范围

本节描述的是“当前 `phase` 下，前端哪些工作流入口应该可见且可点击”。

联调时请注意两层判断：

- 第一层：对应组件或卡片本身是否已经出现在页面上
- 第二层：当前 `phase` 是否允许该入口绑定的工作流事件

下表主要描述第二层。也就是说：

- 某入口在表中写“允许显示”，表示“如果该组件当前存在，则前端应显示并允许点击”
- 某入口在表中写“禁止显示”，表示“即使旧卡片还留在时间线里，前端也应隐藏或禁用该入口”

### 入口与事件映射

| 前端入口 | 位置 | 对应事件 | 前端判定 |
| --- | --- | --- | --- |
| `题目导读` | 聊天栏欢迎区 / 聊天栏下方快捷项 | `READING` | `quickActions` 过滤 |
| `思路分析` | 聊天栏欢迎区 / 聊天栏下方快捷项 | `IDEATING` | `quickActions` 过滤 |
| `继续思路分析` | 聊天栏快捷项 | `IDEATING` | `quickActions` 过滤 |
| `开始编码` | 聊天栏快捷项 | `SCAFFOLDING` | `quickActions` 过滤 |
| `提交拼图答案` | 聊天栏快捷项 | `SCAFFOLDING` | `quickActions` 过滤 |
| `跳过，直接编码` | 聊天栏快捷项 / `ParsonsPanel` | `CODING` | `quickActions` 过滤 / `canSkipParsons` |
| `代码伴读` | 聊天栏快捷项 | `CODING` | `quickActions` 过滤 |
| `错误诊断` | 聊天栏快捷项 / 编辑器工具栏 `智能诊断` | `ERROR_FEEDBACK` | `quickActions` 过滤 / `canRequestDiagnosis` |
| `重新审题` | 聊天栏快捷项 | `READING` | `quickActions` 过滤 |
| `重新梳理思路` | 聊天栏快捷项 | `IDEATING` | `quickActions` 过滤 |
| `继续编码` | 聊天栏快捷项 | `CODING` | `quickActions` 过滤 |
| `AC 复盘` | 聊天栏快捷项 | `AC_REVIEW` | `quickActions` 过滤 |
| `迁移练习` / `重新生成迁移题` | 聊天栏快捷项 | `TRANSFER` | `quickActions` 过滤 |
| `AI 对话助手` | 编辑器工具栏 | `CODING` | `canOpenAiChat` |
| 聊天输入框发送 | 聊天栏输入区 | `CHAT` | `canUseChatInput` |
| `审题引导卡片 -> 思路分析` | `ProblemGuideCard` | `IDEATING` | `canStartIdeate` |
| `思路分析卡片 -> 生成骨架代码` | `IdeateAnalysisCard` | `SCAFFOLDING` | `canRequestSkeleton` |
| `代码伴读卡片 -> 看程序怎么跑` | `CodeCompanionCard` | `CODING` 或 `ERROR_FEEDBACK` | `canRequestExecutionTrace` |
| `错误诊断卡片 -> 看程序怎么一步步跑` | `ErrorDiagnosisCard` | `CODING` 或 `ERROR_FEEDBACK` | `canRequestExecutionTrace` |
| `通过总结卡片 -> 优秀解法/进阶引导` | `PostACCard` | `AC_REVIEW` | `canRequestAdvancedAcReview` |

### 各 Phase 应显示的前端入口

说明：

- `聊天输入框` 在当前实现里对所有合法 phase 都允许，因为后端所有运行阶段都允许 `CHAT`
- `代码伴读卡片 -> 看程序怎么跑` 与 `错误诊断卡片 -> 看程序怎么一步步跑` 共用运行轨迹能力
- `通过总结卡片 -> 学习总结` 不受 `guidance_level=3` 约束，始终可以看；这里表格只列“额外动作入口”

| 当前阶段 `phase` | 聊天栏快捷项 / 欢迎区 | 编辑器工具栏 | 卡片内动作入口 |
| --- | --- | --- | --- |
| `READING` | `题目导读`、`思路分析` | `智能诊断` 可显示；`AI 对话助手` 禁止显示 | `审题引导卡片 -> 思路分析` 允许；`思路分析卡片 -> 生成骨架代码` 允许；运行轨迹按钮禁止显示；`PostACCard` 高级 tab 禁止显示 |
| `IDEATING` | `继续思路分析`、`开始编码` | `智能诊断` 可显示；`AI 对话助手` 禁止显示 | `审题引导卡片 -> 思路分析` 允许；`思路分析卡片 -> 生成骨架代码` 允许；运行轨迹按钮禁止显示；`PostACCard` 高级 tab 禁止显示 |
| `SCAFFOLDING` | `提交拼图答案`、`跳过，直接编码` | `智能诊断` 可显示；`AI 对话助手` 允许显示，但若 `pending_human_action = confirm_scaffold` 则禁止显示 | `ParsonsPanel -> 跳过，直接编码` 允许；若 `pending_human_action = confirm_scaffold` 则禁止显示；`思路分析卡片 -> 生成骨架代码` 允许；运行轨迹按钮禁止显示；`PostACCard` 高级 tab 禁止显示 |
| `CODING` | `代码伴读` | `智能诊断` 可显示；`AI 对话助手` 允许显示 | 运行轨迹按钮允许显示；`ProblemGuideCard` 思路分析禁止显示；`IdeateAnalysisCard` 生成骨架代码禁止显示；`PostACCard` 高级 tab 禁止显示 |
| `ERROR_FEEDBACK` | `错误诊断`、`重新审题`、`重新梳理思路`、`继续编码` | `智能诊断` 可显示；`AI 对话助手` 允许显示 | `ProblemGuideCard -> 思路分析` 允许；`IdeateAnalysisCard -> 生成骨架代码` 允许；运行轨迹按钮允许显示；`PostACCard` 高级 tab 禁止显示 |
| `AC_REVIEW` | `AC 复盘`、`迁移练习` | `智能诊断` 禁止显示；`AI 对话助手` 禁止显示 | `PostACCard -> 优秀解法`、`进阶引导` 允许；运行轨迹按钮禁止显示；`ProblemGuideCard` 与 `IdeateAnalysisCard` 的动作按钮禁止显示 |
| `TRANSFER` | `重新生成迁移题`、`返回编码` | `智能诊断` 可显示；`AI 对话助手` 允许显示，但若 `pending_human_action = confirm_transfer` 则禁止显示 | 运行轨迹按钮禁止显示；`PostACCard` 高级 tab 禁止显示；若 `pending_human_action = confirm_transfer`，则所有返回 `CODING` 的入口都应隐藏 |

### 条件型特殊规则

#### 1. `confirm_scaffold`

当：

- `current_phase = SCAFFOLDING`
- `pending_human_action = confirm_scaffold`

则所有会触发 `CODING` 且没有 `parsons_skipped=true` 的入口都必须禁止显示或禁止点击。

当前前端落实结果：

- 聊天栏快捷项里的 `跳过，直接编码` 会被过滤掉
- 编辑器工具栏 `AI 对话助手` 会被隐藏
- `ParsonsPanel` 内部 `跳过，直接编码` 会被隐藏

#### 2. `confirm_transfer`

当：

- `current_phase = TRANSFER`
- `pending_human_action = confirm_transfer`

则所有会触发 `CODING` 的入口都必须禁止显示或禁止点击。

当前前端落实结果：

- 聊天栏快捷项里的 `返回编码` 会被过滤掉
- 编辑器工具栏 `AI 对话助手` 会被隐藏

### 联调建议

联调前优先检查这 4 个点：

1. 后端返回的 `phase` 是否真实更新
2. 后端返回的 `pending_human_action` 是否与当前节点一致
3. 前端 `quickActions` 是否只包含当前 phase 合法动作
4. 旧卡片残留时，卡片内部按钮是否已经按当前 phase 自动隐藏

## 前端入口源码定位表

说明：

- “入口文件”指用户直接点击的 Vue 组件
- “题目页处理”指事件最终汇总到 [Problem.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue) 的哪个方法
- “能力判断”指决定入口是否显示/可点的前端判定函数
- “后端事件”指最终发给工作流状态机的 `event`

| 前端入口 | 入口文件 | 题目页处理 | 能力判断 / 过滤位置 | 后端 event |
| --- | --- | --- | --- | --- |
| `题目导读` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L142) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [filterWorkflowActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L481) | `READING` |
| `思路分析` / `继续思路分析` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L142) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | `ideate` 入口先经 [handleQuickAction](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L411) 切到输入模式，再由 [handleAgentSend](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L940) 发送 | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [canStartIdeate](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1291) | `IDEATING` |
| `开始编码` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L145) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [isWorkflowActionAllowed](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L475) | `SCAFFOLDING` |
| `提交拼图答案` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L148) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [isWorkflowActionAllowed](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L475) | `SCAFFOLDING` |
| `跳过，直接编码` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L149) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleParsonsSkip](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L899) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [canSkipParsons](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1301) | `CODING` |
| `ParsonsPanel -> 跳过，直接编码` | [ParsonsPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/ParsonsPanel.vue#L80) | [handleParsonsSkip](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L899) | [canSkipParsons](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1301) 透传到 [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L112) 再传给 [ParsonsPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/ParsonsPanel.vue#L108) | `CODING` |
| `代码伴读` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L151) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) -> `triggerCodeCompanion` | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [isWorkflowActionAllowed](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L475) | `CODING` |
| `错误诊断` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L153) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [canRequestDiagnosis](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1277) | `ERROR_FEEDBACK` |
| `重新审题` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L155) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [isWorkflowActionAllowed](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L475) | `READING` |
| `重新梳理思路` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L156) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) -> [handleAgentSend](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L940) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [canStartIdeate](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1291) | `IDEATING` |
| `继续编码` / `返回编码` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L157) / [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L163) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [isWorkflowActionAllowed](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L475) | `CODING` |
| `AC 复盘` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L158) + [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L266) | [handleTriggerAgent](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L968) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [isWorkflowActionAllowed](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L475) | `AC_REVIEW` |
| `迁移练习` / `重新生成迁移题` 快捷项 | [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L159) / [workflowStateMachine.js](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L162) | [handleAgentRequestTransfer](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1072) | [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115) + [canRequestTransfer](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1320) | `TRANSFER` |
| `AI 对话助手` 工具栏按钮 | [CodeEditorPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/CodeEditorPanel.vue#L42) | [toggleAIChat](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L610) | [canOpenAiChat](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1271) 透传到 [CodeEditorPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/CodeEditorPanel.vue#L221) | `CODING` |
| `智能诊断` 工具栏按钮 | [CodeEditorPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/CodeEditorPanel.vue#L16) | [requestSmartDiagnosis](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L624) | [canRequestDiagnosis](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1277) 透传到 [CodeEditorPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/CodeEditorPanel.vue#L225) | `ERROR_FEEDBACK` |
| 聊天输入框发送 | [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L245) | [handleAgentSend](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L940) | [canUseChatInput](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1282) 透传到 [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L405) | `CHAT` |
| `ProblemGuideCard -> 思路分析` | [ProblemGuideCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/ProblemGuideCard.vue#L55) | [handleShowWarmup](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1013) -> [handleAgentSend](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L940) | [canStartIdeate](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1291) 经 [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L70) 透传到 [ProblemGuideCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/ProblemGuideCard.vue#L89) | `IDEATING` |
| `IdeateAnalysisCard -> 生成骨架代码` | [IdeateAnalysisCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/IdeateAnalysisCard.vue#L51) | [handleAgentRequestSkeleton](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1020) | [canRequestSkeleton](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1296) 经 [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L99) 透传到 [IdeateAnalysisCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/IdeateAnalysisCard.vue#L74) | `SCAFFOLDING` |
| `CodeCompanionCard -> 看程序怎么跑` | [CodeCompanionCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/CodeCompanionCard.vue#L28) | [handleRequestExecutionTrace](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L919) | [canRequestExecutionTrace](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1308) 经 [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L122) 透传到 [CodeCompanionCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/CodeCompanionCard.vue#L43) | `CODING` 或 `ERROR_FEEDBACK` |
| `ErrorDiagnosisCard -> 看程序怎么一步步跑` | [ErrorDiagnosisCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/ErrorDiagnosisCard.vue#L181) | [handleRequestExecutionTrace](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L919) | [canRequestExecutionTrace](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1308) 经 [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L129) 透传到 [ErrorDiagnosisCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/ErrorDiagnosisCard.vue#L196) | `CODING` 或 `ERROR_FEEDBACK` |
| `PostACCard -> 优秀解法` / `进阶引导` | [PostACCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/PostACCard.vue#L8) | [handleAgentRequestLevel](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1058) | [canRequestAdvancedAcReview](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1311) 经 [UnifiedAgentPanel.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue#L431) 透传到 [PostACCard.vue](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/cards/PostACCard.vue#L124) | `AC_REVIEW` |

### 统一判定收口点

如果联调时需要判断“为什么这个入口没显示/点了没反应”，优先看下面 4 个收口点：

1. [quickActions](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L115)
   说明：聊天栏欢迎区和底部快捷项的第一层来源。
2. [buildWorkflowActionPayload](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L421)
   说明：把前端入口动作转成用于合法性校验的 payload。
3. [isWorkflowEventAllowed](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/workflowStateMachine.js#L486)
   说明：真正和后端迁移规则对齐的核心判断。
4. [Problem.vue 里的 `can*` 计算属性](/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/Problem.vue#L1271)
   说明：编辑器工具栏、聊天输入框和卡片级按钮的统一能力来源。
