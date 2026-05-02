# Agent + Harness 前端遗留执行计划

## 目标

在不改业务边界、不引入兼容性分支、不把 QA 误并入 Tutor workflow 的前提下，补齐以下 3 个前端遗留项：

- Problem 页 runtime 事件消费
- QA 页 runtime 事件消费
- 恢复态 / 失败态 / 审批态前端 UI

## 当前结论

基于当前仓库代码，以上 3 项**全部已实现并通过单测验收**（共 67 个新增单测）。

| 遗留项 | 当前结论 | 实现方式 |
|------|------|------|
| 前端 Problem 页 runtime 事件消费 | **已完成** | workflowStateMachine.js WebSocket 主分支切换为 runtime_event，新增 _handleRuntimeEvent + runtimeContext 状态跟踪。 |
| 前端 QA 页 runtime 事件消费 | **已完成** | 后端新增 QaWebSocketHandler + sendMessageAsync 异步 dispatch；前端新增 QA WebSocket + _handleQaRuntimeEvent。 |
| 恢复态 / 失败态 / 审批态前端 UI | **已完成** | UnifiedAgentPanel 新增审批态/恢复态/失败态 banner；LanguagePackQaPage 新增 QUEUED/RUNNING/FAILED/EXPIRED banner。 |

## 边界与前提

### 业务边界

- Problem 页只消费做题导学 workflow runtime。
- QA 页只消费 retrieval-grounded QA runtime。
- QA 页不进入教学式 checkpoint / 审批流，不复用 Tutor phase UI。

### 前置依赖

- Problem 页可以直接开工，因为后端 `/ws/workflow/{sessionId}` 已有 `runtime_event` 推送。
- QA 页不能单靠前端完成，因为当前后端还没有 QA runtime 通道；前端阶段必须明确依赖后端补齐异步 dispatch 和 runtime snapshot。
- 若要支持“刷新页面后仍能恢复 runtime 状态”，后端还需要把 `task_id / checkpoint_id / trace_id / runtime_state / approval_state / failure_bucket` 带入 session snapshot；当前 `workflowGetSession` 返回体里尚未稳定暴露这些字段。

### 约束

- 不保留旧的 `node_start / result` WebSocket 协议作为长期兼容路径。
- 不做“Problem 页统一组件顺手兼容 QA 页审批流”之类的跨域设计。
- 只抽一层最小共用：前端 runtime contract 归一化；状态 UI 仍各自在 Problem 页、QA 页本地实现。

---

## Phase 0：前端 Runtime Contract 冻结

### 目标

先把前端要消费的 runtime 字段和页面归属冻结，避免 Problem 页和 QA 页后续各写一套协议解释逻辑。

### 实现动作

1. 新增 `frontend/src/utils/runtimeContract.js`。
2. 在该文件中只做两件事：
   - 归一化服务端 snake_case 字段为前端统一对象：
     - `sessionId`
     - `taskId`
     - `checkpointId`
     - `traceId`
     - `runtimeState`
     - `clientEvent`
     - `serverEvent`
     - `approvalState`
     - `failureBucket`
     - `timestamp`
     - `data`
   - 提供最小判断函数：
     - `isTerminalRuntimeState`
     - `isBlockingRuntimeState`
     - `isApprovalRuntimeState`
3. 明确页面允许消费的状态子集：
   - Problem 页：允许消费 `RUNNING / WAITING_TOOL / WAITING_HUMAN_APPROVAL / INTERRUPTED / RESTORING / FAILED / COMPLETED / EXPIRED`
   - QA 页：只允许消费 `QUEUED / RUNNING / FAILED / COMPLETED / EXPIRED`
4. QA 页若收到 `WAITING_HUMAN_APPROVAL / RESTORING` 等 Tutor 专属状态，直接 fail-fast 报错，不做“静默兼容”。
5. 新增单测 `frontend/tests/unit/runtime-contract.spec.js`，固定字段映射和状态判断。

### 主要落点

- `frontend/src/utils/runtimeContract.js`
- `frontend/tests/unit/runtime-contract.spec.js`

### 验收标准

- 前端只有一套 runtime 字段解释规则。
- Problem 页和 QA 页能明确拒绝不属于本业务域的 runtime 状态。
- 后续页面接 runtime 时不再手写 snake_case 解析。

---

## Phase 1：Problem 页 Runtime 事件消费收口

### 目标

让 Problem 页正式切到 `runtime_event` 协议，不再依赖已经漂移的旧 WebSocket 消息类型。

### 实现动作

1. 改造 `frontend/src/pages/oj/views/problem/workflowStateMachine.js`：
   - 新增 `runtimeContext` 本地状态，至少包含：
     - `sessionId`
     - `taskId`
     - `checkpointId`
     - `traceId`
     - `runtimeState`
     - `serverEvent`
     - `approvalState`
     - `failureBucket`
     - `lastError`
     - `updatedAt`
2. 在 `_connectWorkflowWs().onmessage` 中删除旧的 `node_start / result` 主分支，改为：
   - `runtime_event` -> `_handleRuntimeEvent(msg)`
   - `cancelled` -> 保留
3. 新增 `_handleRuntimeEvent(msg)`，按 `server_event` 分流：
   - `TASK_STARTED`
     - 进入 `agentLoading = true`
     - 写入 `runtimeContext`
     - 显示“任务开始执行”系统提示
   - `TASK_COMPLETED`
     - 先写入 `runtimeContext`
     - 如果 payload 里带 `data`，直接复用现有 `_handleWsResult(msg.data)`，不要再等旧 `result` 消息
   - `TASK_FAILED`
     - 写入 `failureBucket`
     - 停止 loading
     - 从 `msg.data.error` 提取错误并进入错误 UI
   - `TASK_INTERRUPTED / TASK_RESTORING / APPROVAL_REQUESTED / APPROVAL_RESOLVED / TASK_EXPIRED`
     - 只更新 runtime 状态，不擅自伪造业务卡片
4. 改造 `dispatchWorkflowEvent(...)` 的异步路径：
   - `status === dispatched` 后仅等待 `runtime_event`
   - 不再把“最终有 `result` 消息”作为隐含前提
5. 改造 `resetWorkflowContext()`、`clearWorkflow()`、`restoreCheckpoint()` 等流程，把 `runtimeContext` 一并重置或刷新。
6. 若后端后续在 `workflowGetSession` 中补回 runtime snapshot，则在 `_applySessionSnapshot(...)` 中顺手恢复 `runtimeContext`；若还没有该字段，当前页面只恢复已有业务快照，不猜测 runtime 状态。

### 主要落点

- `frontend/src/pages/oj/views/problem/workflowStateMachine.js`
- `frontend/src/pages/oj/views/problem/Problem.vue`
- `frontend/tests/unit/workflow-runtime-event-contract.spec.js`
- `frontend/tests/unit/workflow-state-machine-ws-recovery-contract.spec.js`

### 验收标准

- Problem 页在异步模式下，仅靠 `runtime_event` 就能完成开始、完成、失败三条主链路。
- `TASK_COMPLETED` 携带的 `data` 能正确渲染现有导学卡片，不再依赖 `msg.type === 'result'`。
- 代码中不再把 `node_start / result` 当作 workflow WebSocket 主协议。
- `cancelled` 仍然可用，不回归。

---

## Phase 2：Problem 页恢复态 / 失败态 / 审批态 UI 落地

### 目标

把 runtime 状态真正呈现在 Problem 页，而不是只留在内部状态对象里。

### 实现动作

1. 在 `frontend/src/pages/oj/views/problem/Problem.vue` 中把以下信息传给 `UnifiedAgentPanel.vue`：
   - `runtimeContext`
   - `pendingHumanAction`
   - 最近 checkpoint 列表
   - 可触发的审批动作入口
2. 改造 `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`，在面板头部或输入区上方新增状态区，明确区分以下 3 类状态：
   - 恢复态 `RESTORING`
   - 失败态 `FAILED`
   - 审批态 `WAITING_HUMAN_APPROVAL`
3. 审批态 UI：
   - 展示当前 `pendingHumanAction`
   - 提供“确认 / 拒绝”按钮
   - 通过页面现有 `handleInterrupt('confirm') / handleInterrupt('reject')` 接线
   - 审批未决时禁用普通输入框和普通 quick actions
4. 恢复态 UI：
   - 展示“正在从 checkpoint 恢复”
   - 若已拿到 `checkpointId`，直接显示
   - 禁止重复发起新的 workflow event，直到恢复完成或失败
5. 失败态 UI：
   - 展示失败原因和 `failureBucket`
   - 提供最小闭环操作：
     - 恢复最近 checkpoint
     - 清空当前 workflow 后重开
   - 不新增旁路逻辑，不做“自动降级到普通聊天”
6. 完成态与审批已解决态：
   - 只显示轻量状态提示或时间线消息
   - 不额外创造新的业务分支
7. 若后端暂未推送 `APPROVAL_REQUESTED / TASK_RESTORING`，这一阶段必须与后端同步补事件，否则 UI 只能做静态壳，不能算完成。

### 主要落点

- `frontend/src/pages/oj/views/problem/Problem.vue`
- `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- `frontend/tests/unit/workflow-state-machine-restore-cache.spec.js`
- `frontend/tests/unit/problem-runtime-ui-contract.spec.js`

### 验收标准

- 用户能在 Problem 页一眼区分“正在恢复 / 等待审批 / 已失败”。
- 审批按钮真实调用现有 interrupt 接口，不是纯展示。
- 恢复态期间不能重复提交 workflow action。
- 失败后至少有一条明确恢复路径，不需要刷新页面猜状态。

---

## Phase 3：QA 页 Runtime 事件消费接线

### 目标

把 QA 页从“同步请求 + 刷消息列表”升级为“异步 dispatch + runtime 驱动”，但仍保持 grounded QA 业务边界。

### 实现动作

1. 先补前置依赖：
   - QA 后端需要提供独立 runtime 通道，字段对齐 `RuntimeContract`
   - QA 发送消息接口需要支持异步 dispatch，至少返回：
     - `status`
     - `session_id`
     - `task_id`
   - QA 需要有可重取的 runtime snapshot，供刷新页面或切换会话后恢复状态
2. 在 `frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue` 中新增本地状态：
   - `qaRuntimeContext`
   - `qaPendingQuestion`
   - `qaWsConnection`
3. 改造 `sendQuestion()`：
   - 保留 OJ 问题拦截
   - 不再以“POST 立即返回完整 assistant answer”为主流程
   - 改为“提交问题 -> 获取 dispatch 结果 -> 进入 runtime 监听 -> 完成后再刷新消息列表”
4. 建立 QA 页的 runtime 消费逻辑：
   - `TASK_STARTED`：进入发送中 / 检索中状态
   - `TASK_COMPLETED`：刷新消息列表，自动定位新 assistant 消息，并打开第一条 citation
   - `TASK_FAILED`：记录失败信息，停止 loading
   - `TASK_EXPIRED`：提示任务超时，允许用户重新发送
5. 会话切换和页面卸载时必须：
   - 关闭旧连接
   - 清空旧 runtimeContext
   - 不把 A 会话的 runtime 状态污染到 B 会话
6. QA 页若收到 Tutor 专属状态（如 `WAITING_HUMAN_APPROVAL / RESTORING`），直接 fail-fast，说明后端 contract 越界。

### 主要落点

- `frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue`
- `frontend/src/utils/runtimeContract.js`
- `frontend/src/utils/websocketUrl.js`
- `frontend/tests/unit/language-pack-qa-runtime-contract.spec.js`

### 验收标准

- QA 页发送问题后能看到明确的运行中状态，而不是静默卡住。
- QA 页只消费 QA 自己的 runtime，不复用 Tutor workflow ws。
- 切换 session 后不会收到旧 session 的 runtime 残留更新。
- QA 页收到越界状态时直接暴露问题，不做静默吞掉。

---

## Phase 4：QA 页状态 UI 收口与前端回归

### 目标

补齐 QA 页可见状态，并把 Problem / QA 两条前端 runtime 链路一起回归。

### 实现动作

1. 在 `LanguagePackQaPage.vue` 的 header 或 composer 上方新增 QA 状态区，最少覆盖：
   - `QUEUED`
   - `RUNNING`
   - `FAILED`
   - `COMPLETED`
   - `EXPIRED`
2. QA 状态 UI 约束：
   - `RUNNING` 时禁用发送按钮和 pack/session 切换
   - `FAILED` 时显示失败原因，并允许“用原问题重试”
   - `COMPLETED` 后清空 `qaPendingQuestion`
   - 不新增 checkpoint / approval / restore UI
3. 为 Problem 页和 QA 页补自动化验证：
   - Problem 单测：runtime_event 能驱动开始、完成、失败、审批态
   - QA 单测：dispatch 后进入运行态，完成后刷新消息，失败后进入失败 UI
   - E2E：补一条 runtime 回归链路，覆盖“发送请求 -> 看到运行态 -> 收到完成态 -> 页面结果落地”
4. 更新前端 mock / replacement 配置，确保本地测试能注入 runtime_event，而不是继续依赖旧 `result` 消息。

### 主要落点

- `frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue`
- `frontend/tests/unit/workflow-runtime-event-contract.spec.js`
- `frontend/tests/unit/language-pack-qa-runtime-contract.spec.js`
- `frontend/tests/e2e/oj-runtime-regression.spec.js`
- `frontend/tests/e2e/language-pack-qa-runtime.spec.js`
- `frontend/tests/e2e/support/replacementConfig.js`

### 验收标准

- Problem 页和 QA 页都能看到明确的 runtime 可视状态。
- QA 页没有引入 Tutor 风格审批/恢复 UI。
- 自动化测试覆盖新 runtime_event 协议，而不是继续绑定旧消息结构。
- 运行时协议、页面状态、用户可操作按钮三者保持一致。

---

## 最终验收口径

当下面这些问题都能直接回答，并且页面行为与答案一致时，这批前端遗留项才算真正完成：

1. Problem 页收到 `runtime_event` 后，能不能不靠旧 `result` 消息完成全链路渲染？
   - 能

2. Problem 页能不能明确展示恢复态、失败态、审批态，并且按钮都真实接后端？
   - 能

3. QA 页发问后，用户能不能看到明确的运行中 / 失败 / 完成状态？
   - 能

4. QA 页会不会误消费 Tutor workflow 的审批或 checkpoint 语义？
   - 不会

5. 页面刷新或 session 切换后，runtime 状态会不会串会话、串业务域？
   - 不会

## 建议执行顺序

1. `Phase 0` — **已完成**
2. `Phase 1` — **已完成**
3. `Phase 2` — **已完成**
4. ~~等 QA 后端 runtime contract 到位~~ — **已补齐**（QaWebSocketHandler + sendMessageAsync）
5. `Phase 3` — **已完成**
6. `Phase 4` — **已完成**

## 未验证前提

- ~~QA runtime 通道的最终接口路径和推送方式目前仓库里还没有现成实现~~ — **已落地**：/ws/qa/{sessionId} + ?async=true + RuntimeContract 推送。
- Problem 页若要在“刷新页面后”完整恢复 runtime 状态，仍依赖后端把 runtime snapshot 补进 session 查询返回体；当前 _applySessionSnapshot 已支持恢复 runtime 字段，但后端 workflowGetSession 尚未稳定输出。
