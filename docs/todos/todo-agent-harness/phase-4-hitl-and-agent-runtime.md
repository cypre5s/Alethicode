# Phase 4 + Phase 5：Harness 主体落地与 HITL 扩展

**适用界面**：两者共用
**阶段属性**：Harness 本体（兼具业务自治能力建设）

## Phase 4：Harness Engineering 主体落地

### 目标

把评测、追踪、回放、门禁真正接到工程主流程上，形成"改一处、测一处、判一处"的闭环。

### 实现流程

#### 统一 trace 模型

- `AgentTrace`
- `RetrievalTrace`
- `ToolTraceEntry`
- `TraceGradeResult`

#### 统一 runtime contract

- `session_id`、`task_id`、`checkpoint_id`、`trace_id`
- `runtime_state`、`client_event`、`server_event`
- `approval_state`、`failure_bucket`

#### 统一 lifecycle state model

| 状态 | 含义 | 允许迁出到 | 持久化要求 |
|------|------|-----------|-----------|
| `QUEUED` | 任务已创建，尚未执行 | RUNNING, EXPIRED, FAILED | session_id, task_id, trace_id, create_time |
| `RUNNING` | agent 正在执行主循环 | WAITING_TOOL, WAITING_HUMAN_APPROVAL, INTERRUPTED, FAILED, COMPLETED | 当前 step、输入快照、上下文快照 |
| `WAITING_TOOL` | 等待工具结果 | RUNNING, FAILED, INTERRUPTED | tool_name, tool_args, tool_call_id, started_at |
| `WAITING_HUMAN_APPROVAL` | 等待人工审批 | RUNNING, EXPIRED, FAILED | pending_human_action, checkpoint_id, approval_payload |
| `INTERRUPTED` | 被用户或系统中断 | RESTORING, FAILED, EXPIRED | 中断原因、中断时刻、最近安全输出 |
| `RESTORING` | 从 checkpoint 恢复状态 | RUNNING, FAILED, EXPIRED | source_checkpoint_id, source_trace_id, restore_token |
| `FAILED` | 执行失败且不再继续 | 终态 | failure_bucket, error_reason, 最后有效上下文 |
| `COMPLETED` | 任务成功完成 | 终态 | 最终输出, trace 收尾, 完成时间 |
| `EXPIRED` | 超时过期 | 终态 | 过期原因, 过期时间, 是否允许替代 |

#### 状态迁移规则

1. `QUEUED -> RUNNING`：仅由 runtime scheduler 或恢复流程触发
2. `RUNNING -> WAITING_TOOL`：仅在记录 tool_call_id 后
3. `WAITING_TOOL -> RUNNING`：仅在工具结果写入 observation 后
4. `RUNNING -> WAITING_HUMAN_APPROVAL`：仅在 checkpoint 已落盘后
5. `WAITING_HUMAN_APPROVAL -> RUNNING`：仅在审批结果落盘后
6. `INTERRUPTED -> RESTORING`：仅由用户续跑、管理端恢复或系统自恢复触发
7. `RESTORING -> RUNNING`：仅在 checkpoint、trace、上下文三者重新绑定成功后
8. 任意活跃态 -> `FAILED`：必须写入结构化失败原因
9. 任意等待态 -> `EXPIRED`：必须有明确 TTL

#### Recovery contract

| 场景 | 恢复动作 | 约束 |
|------|---------|------|
| 进程重启恢复 RUNNING 任务 | 进入 RESTORING，重建 context 再进入 RUNNING | 不丢 trace_id，不重复执行已完成工具调用 |
| 人工审批后恢复 | 从 WAITING_HUMAN_APPROVAL → RESTORING → RUNNING | 保留审批记录与审批前上下文 |
| 用户手动续跑 INTERRUPTED | 从 INTERRUPTED → RESTORING → RUNNING | "这是恢复执行"写入 trace |
| stale task 清理 | 等待态/中断态 → EXPIRED | 只能新建替代任务 |
| orphan task 处理 | 标记 EXPIRED 或 FAILED | 必须进入 failure report |

#### 升级 TraceGradeService

- schema correctness
- pedagogy fit
- retrieval sufficiency
- grounding soundness
- answer leakage
- action appropriateness
- interruption safety

#### 升级 TutorEvalHarness / QaEvalHarness

- dataset-aware：按 sample 跑
- 输出 failure bucket
- trace 评分
- 回放入口

#### 灰度门禁

- 离线 harness 不达标不允许 gray
- 新能力必须带 dataset
- 新策略必须带 grader
- 新灰度必须有明确阈值

#### 分离两套 grader

- 导学 grader：pedagogy fit, answer leakage, scaffold quality, action appropriateness
- QA grader：retrieval recall, grounding accuracy, refusal correctness, citation precision

#### Client/Runtime Integration

- Web 前端通过稳定 workflow API / QA API 与 harness 交互
- WebSocket 只承担运行时事件推送，不承载业务判定
- 管理端、回放入口、灰度入口复用同一套 runtime identifiers

### 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/contract/RuntimeState.java`
- `backend/src/main/java/com/alethicode/service/aitutor/contract/RuntimeContract.java`
- `backend/src/main/java/com/alethicode/service/aitutor/contract/ServerEvent.java`
- `backend/src/main/java/com/alethicode/service/aitutor/contract/FailureBucket.java`
- `backend/src/main/java/com/alethicode/service/aitutor/contract/RecoveryReason.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/TutorEvalHarness.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/TraceGradeService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/rollout/RolloutPolicyService.java`
- `backend/src/main/java/com/alethicode/websocket/WorkflowRealtimeSupport.java`

---

## Phase 5：Human-in-the-Loop 扩展与可控自治

### 目标

把当前 `pending_human_action + checkpoint + interrupt` 从点状功能扩展为可控自治框架。

### 实现流程

1. 扩展审批点定义：
   - `confirm_scaffold`
   - `confirm_transfer`
   - `confirm_memory_save`
   - `confirm_high_risk_tool_use`
   - `confirm_retrieval_override`
2. 在导学链路补高风险暂停条件：
   - 可能泄题
   - 大跨度 phase 跳转
   - 记忆保存
   - critic 连续失败
3. 在 QA 链路补受控追问分支：
   - 证据不足时不强答
   - 请求用户缩小范围
   - 请求指定章节/文档
4. 加入 stopping conditions：
   - 最大迭代数
   - 重复工具调用次数
   - critic fail 次数
   - 超时阈值
5. checkpoint 标签规范化：
   - phase
   - context source
   - evidence source
   - approval state
6. `orchestrator-workers` 只用在复杂错误诊断，不扩散到 QA 自由对话。
7. `evaluator-optimizer` 只用在高价值节点：ERROR_FEEDBACK、AC_REVIEW、grounded QA refinement。
8. QA 的 HITL 边界：
   - 只允许"缩小问题范围 / 指定章节 / 继续拒答"的受控交互
   - 不引入教学 phase 式人工审批流
9. Lifecycle/Recovery 规则：
   - `RUNNING` 超时后进入 `FAILED` 或 `INTERRUPTED`
   - `WAITING_HUMAN_APPROVAL` 超过阈值后进入 `EXPIRED`
   - `RESTORING` 必须带原始 `checkpoint_id / trace_id`
   - 恢复后禁止丢失审批状态与上下文来源
10. Client integration 边界：
    - Problem 页只消费导学 workflow runtime 事件
    - QA 页只消费 retrieval-grounded QA runtime 事件
    - 管理端只通过 admin/runtime 入口做审批、恢复、回放

### 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/contract/PendingHumanAction.java`
- `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- `backend/src/main/java/com/alethicode/controller/AITutorWorkflowController.java`
- `frontend/src/pages/oj/views/problem/`
- `frontend/src/pages/oj/views/languagepack/`

### 测试

- 高风险工具调用会进入审批而不是直接执行。
- critic 连续失败会中断，不会死循环。
- checkpoint 恢复后状态与审批信息一致。
- QA 在证据不足时稳定引导缩小问题范围。
- 任务恢复后 `checkpoint_id / trace_id / approval_state` 关联不丢失。
- Problem 页与 QA 页不会消费对方的 runtime 事件协议。

### 验收标准

- Agent 行为更强，但不会更失控。
- 用户和教师都能看懂"为什么停在这里"。
- 复杂诊断可以拆 worker，但不会把系统变成不可控多 Agent 网络。
- 自治能力与可调试性同时提升。
- 生命周期状态、恢复动作、前端消费协议三者保持一致。
