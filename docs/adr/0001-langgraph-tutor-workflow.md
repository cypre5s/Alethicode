# ADR-0001: AI 导学 workflow 从 Java 自研状态机迁移到独立 LangGraph 服务

- **Status**: Accepted
- **Date**: 2026-04-21
- **Authors**: Alethicode core team
- **Stakeholders**: 后端 / 前端 / SRE / 产品

## 背景

`AITutorWorkflowAdminServiceImpl` 同时承担了 session CRUD、phase 状态迁移、
evidence pack 组装、checkpoint、human interrupt、以及 transfer problem 等强
副作用，单类逼近 1400 行。我们需要：

- 接入真正的 AI agent runtime（checkpoint / HITL / streaming updates）
- 把状态机、prompt 编排、evidence 组装从业务 service 里拆出来
- 保留现有卡片语义，不打破 Problem 页的前端

## 约束

- LangGraph 官方运行时只有 Python / JS。
- 不能把 Alethicode 主业务表交给 LangGraph 直接写（权限 / 合规）。
- 旧 `/api/ai/workflow/*` 要能在一次迁移后删除，不保留长期兼容分支。
- 学生生产请求不能做双跑 A/B（会放大成本 / 不可审计）。

## 选项分析

| 选项 | 优 | 劣 | 决策相关 |
|------|----|----|----------|
| 继续纯 Java，自研图引擎 | 无新进程 | 自研图引擎 = 又一个 runtime 要维护；不等于"采用 LangGraph" | 排除 |
| Java 内嵌 LangGraph4j（社区 port） | 无新进程 | 非官方 runtime；checkpoint / HITL / stream 都要再造 | 排除 |
| **新增 Python `tutor_graph` 服务** | 官方 LangGraph；checkpoint/interrupt/stream 原生 | 多进程、跨语言、需要新的部署条目 | **选中** |
| 改为云托管（LangGraph Cloud） | 省运维 | 学生代码 / 学情数据出境 / PIPL 违规 | 排除 |

## 决策

新增独立 `services/tutor-graph/` 服务（FastAPI + LangGraph），作为 AI 导学 workflow 的
**唯一** runtime。Java 侧只做：

1. REST / WebSocket 门面（`/api/ai/tutor-workflow-sessions/**` 等）
2. Internal tool API（`/internal/ai-tutor/*`）供 tutor_graph 读业务数据 / 写
   projection / 触发强副作用
3. Projection 表（`ai_tutor_workflow_session` / `ai_tutor_workflow_event` /
   `ai_tutor_side_effect_log`）

LangGraph checkpointer 采用 Postgres `AsyncPostgresSaver`，和 Alethicode 主库
隔离 schema，以免 Flyway 与 LangGraph 自有 schema 冲突。

## 后果

**正面**
- LangGraph checkpoint / interrupt / streaming 原生可用
- Java 侧回归到清晰的分层（Controller → Service → Domain）
- Prompt 编排集中在 `services/tutor-graph/app/nodes/*`，可单文件替换 / 回放
- 强副作用（`createTransferProblem`）走 Java internal API，权限校验仍在 Java 侧

**负面 / 绑定**
- 部署多一个 Python 进程（单 worker，见 ADR-0005）
- 跨语言调用需要 W3C traceparent 才能关联（已接入 OpenTelemetry）
- 合规审计多一个被审对象
- 国内网络下 langchain/LangGraph 更新要走阿里云 PyPI mirror

## 后续

- 观察 `tutor_graph` 的 p99 延迟、CPU、内存 3 个月
- 若需要横向扩展：考虑 LangGraph Agent Server + Redis 共享 `_active_runs`
  （见 ADR-0005）
- 如果 LangGraph4j 未来成熟到官方支持水平，可反思是否合并
