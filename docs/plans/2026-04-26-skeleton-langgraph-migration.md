# Skeleton LangGraph Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move skeleton generation fully into LangGraph with an explicit `SKELETON` workflow event and delete all legacy `ai/ideate/*` paths.

**Architecture:** Introduce a dedicated `SKELETON` event and LangGraph node that emits `skeleton_code`, route all frontend triggers through tutor workflow runtime, then remove legacy backend and frontend API surfaces. Verification must prove both the new workflow contract and the absence of old routes.

**Tech Stack:** FastAPI LangGraph service, Spring Boot backend, Vue 2 frontend, pytest, JUnit, Vitest/Jest-style frontend tests

---

### Task 1: 写迁移设计文档

**Files:**
- Create: `docs/plans/2026-04-26-skeleton-langgraph-migration-design.md`
- Create: `docs/plans/2026-04-26-skeleton-langgraph-migration.md`

**Step 1: 写设计文档**

写明：
- `SKELETON` 为显式事件
- `skeleton_code` 为唯一骨架卡契约
- 删除全部 `ai/ideate/*`

**Step 2: 写实现计划**

列出 LangGraph、后端、前端、测试、CHANGELOG 的执行顺序。

**Step 3: 检查文档**

Run: `sed -n '1,220p' docs/plans/2026-04-26-skeleton-langgraph-migration-design.md`

Expected: 文档内容完整，路径与事件命名统一。

### Task 2: 先写 LangGraph 失败测试

**Files:**
- Modify: `services/tutor-graph/app/tests/test_transitions.py`
- Modify: `services/tutor-graph/app/tests/test_actions_policy.py`
- Modify: `services/tutor-graph/app/tests/test_card_schemas.py`
- Create: `services/tutor-graph/app/tests/test_skeleton_node.py`

**Step 1: 写失败测试**

覆盖：
- `SKELETON` 是合法事件
- `SKELETON` 不切主 phase
- `skeleton` 节点输出 `skeleton_code`
- `ideating` 节点不再处理 `__generate_skeleton__`

**Step 2: 运行并确认失败**

Run: `pytest services/tutor-graph/app/tests/test_skeleton_node.py services/tutor-graph/app/tests/test_transitions.py services/tutor-graph/app/tests/test_actions_policy.py services/tutor-graph/app/tests/test_card_schemas.py -q`

Expected: 因缺少 `SKELETON` 实现或旧行为仍存在而失败。

### Task 3: 实现 LangGraph `SKELETON` 事件

**Files:**
- Modify: `services/tutor-graph/app/graph/state.py`
- Modify: `services/tutor-graph/app/graph/builder.py`
- Modify: `services/tutor-graph/app/graph/transitions.py`
- Modify: `services/tutor-graph/app/nodes/actions.py`
- Modify: `services/tutor-graph/app/nodes/ideating.py`
- Create: `services/tutor-graph/app/nodes/skeleton.py`

**Step 1: 最小实现**

- 新增 `SKELETON` 事件
- 新增 `skeleton` 节点
- builder 将 `SKELETON` 路由到 `skeleton`
- `ideating.py` 删除骨架分支，只保留思路分析

**Step 2: 运行定向测试**

Run: `pytest services/tutor-graph/app/tests/test_skeleton_node.py services/tutor-graph/app/tests/test_transitions.py services/tutor-graph/app/tests/test_actions_policy.py services/tutor-graph/app/tests/test_card_schemas.py -q`

Expected: 全部通过。

### Task 4: 先写后端失败测试

**Files:**
- Modify: `backend/src/test/java/com/alethicode/integration/AITutorWorkflowStateMachineIntegrationTest.java`
- Modify: `backend/src/test/java/com/alethicode/service/aitutor/policy/TransitionPolicyTest.java`
- Modify: `backend/src/test/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImplTest.java`
- Modify or Delete: `backend/src/test/java/com/alethicode/controller/AITutorControllerSkeletonContractTest.java`

**Step 1: 写失败测试**

覆盖：
- `SKELETON` 成为合法 workflow 事件
- action policy 下发“骨架代码”按钮走 `SKELETON`
- `IDEATING` 不再通过 `__generate_skeleton__` 分支返回骨架
- 老接口契约测试改成“接口已删除”

**Step 2: 运行并确认失败**

Run: `mvn -q -Dtest=AITutorWorkflowStateMachineIntegrationTest,TransitionPolicyTest,AITutorWorkflowAdminServiceImplTest,AITutorControllerSkeletonContractTest test`

Expected: 因新事件未落地、旧接口仍存在而失败。

### Task 5: 实现后端事件契约并删除老接口

**Files:**
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/contract/WorkflowEvent.java`
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/policy/TransitionPolicy.java`
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/policy/TutorActionPolicy.java`
- Modify: `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- Modify: `backend/src/main/java/com/alethicode/controller/AITutorController.java`
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/AITutorWorkflowDomainService.java`
- Modify: `backend/src/main/java/com/alethicode/service/aitutor/impl/AITutorWorkflowDomainServiceImpl.java`

**Step 1: 实现最小代码**

- 新增 `SKELETON`
- 老 `ideate*` 接口与 service 方法直接删除
- workflow 投影与动作策略改为显式 `SKELETON`

**Step 2: 运行后端定向测试**

Run: `mvn -q -Dtest=AITutorWorkflowStateMachineIntegrationTest,TransitionPolicyTest,AITutorWorkflowAdminServiceImplTest,AITutorControllerSkeletonContractTest test`

Expected: 全部通过。

### Task 6: 先写前端失败测试

**Files:**
- Modify: `frontend/tests/unit/workflow-private-ai-contract.spec.js`
- Modify: `frontend/tests/unit/workflow-state-machine-restore-cache.spec.js`
- Create or Modify: `frontend/tests/unit/problem-skeleton-workflow-contract.spec.js`

**Step 1: 写失败测试**

覆盖：
- 点击骨架按钮发出 `SKELETON`
- 不再调用 `ai/ideate/skeleton`
- 恢复与回放从 `node_outputs.skeleton_code` 重建
- API 模块不再导出 `ideateGetSkeleton` / `ideateMarkInserted`

**Step 2: 运行并确认失败**

Run: `npm --prefix frontend test -- --runInBand frontend/tests/unit/problem-skeleton-workflow-contract.spec.js frontend/tests/unit/workflow-private-ai-contract.spec.js frontend/tests/unit/workflow-state-machine-restore-cache.spec.js`

Expected: 因旧接口仍被调用或缺少 `SKELETON` 事件而失败。

### Task 7: 实现前端单链路迁移

**Files:**
- Modify: `frontend/src/pages/oj/api/aiTutor.js`
- Modify: `frontend/src/pages/oj/views/problem/Problem.vue`
- Modify: `frontend/src/pages/oj/views/problem/workflowStateMachine.js`

**Step 1: 最小实现**

- 删除 `ideateAnalyze` / `ideateGetSkeleton` / `ideateMarkInserted`
- `handleAgentRequestSkeleton()` 改为 workflow 事件
- 恢复与回放统一走 `skeleton_code`

**Step 2: 运行前端定向测试**

Run: `npm --prefix frontend test -- --runInBand frontend/tests/unit/problem-skeleton-workflow-contract.spec.js frontend/tests/unit/workflow-private-ai-contract.spec.js frontend/tests/unit/workflow-state-machine-restore-cache.spec.js`

Expected: 全部通过。

### Task 8: 更新变更日志

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: 用中文追加记录**

写明：
- `SKELETON` 迁入 LangGraph
- 删除 `ai/ideate/*`
- 前端骨架按钮切到 workflow

**Step 2: 检查格式**

Run: `git diff --check -- CHANGELOG.md`

Expected: 无格式错误。

### Task 9: 全链路验证与代码审查

**Files:**
- Review only

**Step 1: 跑全部定向验证**

Run:
- `pytest services/tutor-graph/app/tests/test_skeleton_node.py services/tutor-graph/app/tests/test_transitions.py services/tutor-graph/app/tests/test_actions_policy.py services/tutor-graph/app/tests/test_card_schemas.py -q`
- `mvn -q -Dtest=AITutorWorkflowStateMachineIntegrationTest,TransitionPolicyTest,AITutorWorkflowAdminServiceImplTest,AITutorControllerSkeletonContractTest test`
- `npm --prefix frontend test -- --runInBand frontend/tests/unit/problem-skeleton-workflow-contract.spec.js frontend/tests/unit/workflow-private-ai-contract.spec.js frontend/tests/unit/workflow-state-machine-restore-cache.spec.js`

Expected: 全部通过。

**Step 2: 做代码审查**

重点检查：
- 是否残留 `ai/ideate/*`
- 是否还存在 `__generate_skeleton__`
- 是否出现旧/新双契约并存

**Step 3: 输出结果**

报告：
- 实际改动
- 验证结果
- 残余风险（如果有）
