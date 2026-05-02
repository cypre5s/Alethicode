# Tutor Workflow Contract

> 本文档固化 AI 导学助手 LangGraph workflow 的全部契约。
> Python tutor-graph 服务、Java Gateway、前端 Problem 页共同遵守此契约。

## 1. 业务 Phase

| Phase | 说明 |
|---|---|
| `READING` | 审题 / 导读 |
| `IDEATING` | 思路分析 |
| `CODING` | 编码中 |
| `ERROR_FEEDBACK` | 错误诊断 |
| `AC_REVIEW` | AC 复盘 |
| `TRANSFER` | 迁移练习 |

## 2. Client Event

| Event | 是否改变 Phase | 说明 |
|---|---|---|
| `READING` | 是 | 触发导读 |
| `IDEATING` | 是 | 触发思路分析 |
| `CODING` | 是 | 进入编码 |
| `ERROR_FEEDBACK` | 是 | 提交 WA 后诊断 |
| `AC_REVIEW` | 是 | AC 后复盘 |
| `TRANSFER` | 是 | 迁移练习 |
| `CHAT` | 否 | 自由对话 |
| `AGENT_FEEDBACK` | 否 | 系统反馈 |
| `KNOWLEDGE_REVIEW` | 否 | 知识点回顾 |

## 3. Phase 合法迁移

```
READING -> READING, IDEATING, CODING, ERROR_FEEDBACK, AC_REVIEW
IDEATING -> IDEATING, CODING, ERROR_FEEDBACK, AC_REVIEW
CODING -> CODING, ERROR_FEEDBACK, AC_REVIEW
ERROR_FEEDBACK -> ERROR_FEEDBACK, READING, IDEATING, CODING, AC_REVIEW
AC_REVIEW -> AC_REVIEW, TRANSFER
TRANSFER -> TRANSFER, CODING, ERROR_FEEDBACK, AC_REVIEW
```

辅助事件 `CHAT`、`AGENT_FEEDBACK`、`KNOWLEDGE_REVIEW` 在任何 Phase 下均允许，且不改变 Phase。

## 4. Runtime Event (server_event)

| server_event | 说明 |
|---|---|
| `TASK_QUEUED` | run 已入队 |
| `TASK_STARTED` | run 开始执行 |
| `TASK_PROGRESS` | 中间进度 |
| `TOOL_CALL_STARTED` | 工具调用开始 |
| `TOOL_CALL_COMPLETED` | 工具调用完成 |
| `CARD_GENERATED` | 卡片已生成 |
| `APPROVAL_REQUESTED` | 需要人工确认 |
| `APPROVAL_RESOLVED` | 人工确认已处理 |
| `TASK_INTERRUPTED` | run 被中断 |
| `TASK_RESTORING` | 正在从 checkpoint 恢复 |
| `TASK_COMPLETED` | run 正常完成 |
| `TASK_FAILED` | run 失败 |
| `TASK_EXPIRED` | run 超时 |

## 5. Failure Bucket

| failure_bucket | 说明 |
|---|---|
| `INSUFFICIENT_EVIDENCE` | 缺少必要证据 |
| `CONFLICTING_EVIDENCE` | 证据冲突 |
| `OUT_OF_SCOPE` | 超出范围 |
| `SCHEMA_VIOLATION` | 输出不符 schema |
| `TOOL_EXECUTION_FAILED` | 工具调用失败 |
| `APPROVAL_TIMEOUT` | 审批超时 |
| `SYSTEM_ERROR` | 系统错误 |

## 6. 卡片输出 Key 映射

| Event | node_outputs key | 前端卡片 type |
|---|---|---|
| `READING` | `problem_guide` | `problem_guide` |
| `IDEATING` | `ideate` | `ideate_analysis` |
| `CODING` | `execution_trace_explainer` (可选) | `execution_trace_explainer` |
| `ERROR_FEEDBACK` | `error_diagnosis` | `error_diagnosis` |
| `AC_REVIEW` | `post_ac` | `post_ac` |
| `TRANSFER` | `transfer` | `transfer_problem` |
| `CHAT` | `chat` | `ai_reply` |
| `KNOWLEDGE_REVIEW` | `knowledge_review` | `knowledge_review` |

卡片 JSON Schema 存放于 `cards/*.schema.json`。

## 7. Fail-Fast 错误码

| HTTP 状态码 | 场景 |
|---|---|
| `401` | 未登录 |
| `403` | 越权（session/problem 不属于当前用户） |
| `404` | 资源不存在 |
| `409` | 同一 session 已有 active run / idempotency_key 冲突 |
| `422` | 非法 transition / 缺少必要参数 / 语言缺失 |

## 8. 旧路径冻结声明

以下旧 API 路径在迁移完成后**删除**，不再接受新功能开发：

- `GET /api/ai/workflow/session`
- `POST /api/ai/workflow/session`
- `DELETE /api/ai/workflow/session`
- `POST /api/ai/workflow/event`
- `GET /api/ai/workflow/checkpoint`
- `POST /api/ai/workflow/checkpoint/restore`
- `POST /api/ai/workflow/interrupt`
- `/ws/workflow/{sessionId}`
