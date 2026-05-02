# Skeleton LangGraph 迁移设计

> **文档编号**：ALETH-PLAN-2026-0426-P4
> **优先级**：P1
> **作者**：AI Coding Assistant
> **创建日期**：2026-04-26

> **一句话目标**：将“骨架代码生成”彻底迁入 LangGraph，新增显式 `SKELETON` 事件与统一 `skeleton_code` 卡片契约，并删除所有 `ai/ideate/*` 老链路。

---

## 一、设计动机

当前骨架生成仍走旧链路 `/api/ai/ideate/skeleton`，它直接调用后端 LLM 服务，与 LangGraph 工作流完全分离，带来三个结构性问题：

1. 会话上下文割裂：骨架生成拿不到 LangGraph 内的 learner state、checkpoint、事件轨迹。
2. 故障链路重复：旧接口独立调 LLM，出现 SSL 握手失败或超时时会直接报 `500`，而前端又需要额外维护一套错误处理。
3. 前后端契约分叉：工作流恢复、消息回放、WS 增量只认 workflow 输出；骨架卡却来自旁路 API。

第一性原理上，骨架生成属于“导学过程中的一种受控教学动作”，应与审题、思路分析、纠错、复盘一样，纳入同一条工作流链路。

---

## 二、现状盘点

### 2.1 旧链路

- 前端按钮：`Problem.vue -> api.ideateGetSkeleton()`
- 前端接口：`frontend/src/pages/oj/api/aiTutor.js`
- 后端入口：
  - `POST /api/ai/ideate/skeleton`
  - `POST /api/ai/ideate/analyze`
  - `POST /api/ai/ideate/inserted`
- 后端实现：`AITutorWorkflowAdminServiceImpl.ideateSkeleton()`

### 2.2 新链路已有基础

- Tutor workflow 已支持事件驱动：`/api/ai/tutor-workflow-sessions/{sessionId}/runs`
- LangGraph `IDEATING` 节点内部已经存在隐式分支：`thought_text == "__generate_skeleton__"`
- 前端 runtime 已支持从 `node_outputs` 重建卡片，且已支持 `skeleton_code` 类型展示

### 2.3 关键问题

- 目前 `SKELETON` 不是显式事件，而是 `IDEATING` 的“魔法参数分支”
- `ideate` 与 `skeleton_code` 的职责边界不清晰
- 老接口仍是生产路径，无法真正切到单链路

---

## 三、设计目标与非目标

### 3.1 目标

1. 新增显式 workflow 事件 `SKELETON`
2. LangGraph 新增独立 `skeleton` 节点，统一产出 `node_outputs["skeleton_code"]`
3. 前端“骨架代码”按钮、消息展示、checkpoint 恢复、WS 回放全部切到新链路
4. 删除 `/api/ai/ideate/analyze`、`/api/ai/ideate/skeleton`、`/api/ai/ideate/inserted`
5. 删除所有基于 `__generate_skeleton__` 的隐式分支

### 3.2 非目标

1. 不新增新的业务 phase；`SKELETON` 是事件，不重做整套 phase FSM
2. 不保留兼容 API，不做双写，不保留旧返回结构
3. 不顺手扩展新的教学模式，只解决骨架迁移与老链路删除

---

## 四、方案结论

采用“显式 `SKELETON` 事件 + 独立 LangGraph 节点 + 删除所有老链路”的单链路方案。

不采用以下方案：

1. 保留 `IDEATING + "__generate_skeleton__"`：语义脏，恢复与观测难以收敛
2. 旧接口改成后端转发 workflow：只是换入口，不是删除老链路
3. 新增独立 phase：过度设计，当前只需独立事件即可

---

## 五、详细设计

### 5.1 事件与状态机

- 新增 workflow 事件：`SKELETON`
- `SKELETON` 允许在导学相关 phase 触发，至少包括：
  - `READING`
  - `IDEATING`
  - `CODING`
  - `ERROR_FEEDBACK`
- `SKELETON` 不改变主 phase，只表示“在当前上下文请求一张骨架卡”

### 5.2 LangGraph 节点设计

- 新增 `services/tutor-graph/app/nodes/skeleton.py`
- 输入：
  - `workflow_context`
  - `learner_state`
  - `language`
  - 当前题目上下文
- 输出：
  - `node_outputs["skeleton_code"] = { description, skeleton, teaching_goal, checkpoint_prompt, mentor_role, reflection_prompt }`

规则：

1. `ideating.py` 只负责思路分析，删除骨架生成分支
2. `skeleton.py` 专门负责骨架生成
3. projection / schema validation / message rebuild 一律以 `skeleton_code` 为唯一骨架产物键

### 5.3 后端契约

- 新增 `WorkflowEvent.SKELETON`
- 更新 transition / action policy / projection 映射
- Java 侧 workflow projection 对 `SKELETON -> CardType.SKELETON_CODE`
- 删除以下老入口：
  - `AITutorController.ideateAnalyze`
  - `AITutorController.ideateSkeleton`
  - `AITutorController.ideateInserted`
- 删除对应 domain service 入口和后端旧测试

### 5.4 前端迁移

统一原则：

1. 前端不再调用任何 `ai/ideate/*`
2. 骨架按钮只发 workflow 事件 `SKELETON`
3. 骨架卡只来源于 workflow runtime / checkpoint / WS 回放

改动点：

1. `frontend/src/pages/oj/api/aiTutor.js`
   - 删除 `ideateAnalyze`
   - 删除 `ideateGetSkeleton`
   - 删除 `ideateMarkInserted`
2. `Problem.vue`
   - `handleAgentRequestSkeleton()` 改为 `dispatchWorkflowEvent('SKELETON')`
   - 移除旧接口超时逻辑和旁路消息逻辑
3. `workflowStateMachine.js`
   - 增加 `SKELETON` 事件映射
   - 统一从 `node_outputs.skeleton_code` 重建卡片
4. `UnifiedAgentPanel.vue` / `SkeletonCodeCard.vue`
   - 保持展示契约不变，仅切换数据来源

### 5.5 插入编辑器后的同步

旧的 `/api/ai/ideate/inserted` 删除后，插入骨架代码不再走专门接口。

统一策略：

- 插入编辑器属于前端本地编辑动作
- 后续同步继续依赖既有 `code-snapshot` / runtime 行为采样
- 不再为“已插入骨架”保留一条专门 HTTP API

---

## 六、受影响文件范围

### 6.1 LangGraph

- `services/tutor-graph/app/graph/state.py`
- `services/tutor-graph/app/graph/builder.py`
- `services/tutor-graph/app/graph/transitions.py`
- `services/tutor-graph/app/nodes/actions.py`
- `services/tutor-graph/app/nodes/ideating.py`
- `services/tutor-graph/app/nodes/skeleton.py`（新增）
- 相关测试

### 6.2 后端

- `backend/src/main/java/com/alethicode/controller/AITutorController.java`
- `backend/src/main/java/com/alethicode/service/aitutor/AITutorWorkflowDomainService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/impl/AITutorWorkflowDomainServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/contract/WorkflowEvent.java`
- `backend/src/main/java/com/alethicode/service/aitutor/policy/TransitionPolicy.java`
- `backend/src/main/java/com/alethicode/service/aitutor/policy/TutorActionPolicy.java`
- 相关测试

### 6.3 前端

- `frontend/src/pages/oj/api/aiTutor.js`
- `frontend/src/pages/oj/views/problem/Problem.vue`
- `frontend/src/pages/oj/views/problem/workflowStateMachine.js`
- 相关单测

---

## 七、验收标准

1. 浏览器点击“骨架代码”后，不再请求 `/api/ai/ideate/skeleton`
2. workflow run 请求事件类型为 `SKELETON`
3. LangGraph 产出 `node_outputs["skeleton_code"]`
4. checkpoint 恢复后骨架卡仍能正确重建
5. 仓库中不再存在 `/api/ai/ideate/analyze`、`/api/ai/ideate/skeleton`、`/api/ai/ideate/inserted` 生产入口
6. 相关前端、后端、tutor_graph 定向测试全部通过

---

## 八、第一性原理自检

1. 是否消除了双链路？
   - 是。骨架生成只走 workflow / LangGraph。
2. 是否引入了补丁式兼容？
   - 否。直接删老接口，不保留转发壳。
3. 是否过度设计？
   - 否。只新增显式事件与独立节点，不新增 phase、不新增表。
4. 是否逻辑闭合？
   - 是。触发、生成、展示、恢复、插入后同步、删除老链路都已覆盖。
