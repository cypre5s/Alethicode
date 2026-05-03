# Alethicode 重构 TODO — 巨石文件拆分路线图

> 最后更新：2026-04-21。按本文件推进即可覆盖主线的代码健康度债务。
>
> 读者：下一位接手的后端 / 前端 / 架构负责人。
>
> 每完成一项，请在本文件中将对应 `[ ]` 改为 `[x]`，并同步补写 ADR 与 CHANGELOG。

## 零、为什么要做这件事

全量扫描显示仓库存在多个明显超出单文件合理规模的"巨石"。它们带来四个具体的日常损失：

1. **每次 PR 必然带来大面积 diff**，Code Review 被拖慢到 3-5 倍
2. **测试无法精准分层**——一个单元测试类动辄 1000+ 行，覆盖率工具误判
3. **新人 onboard 痛苦**——改一个 bug 要读 3000 行才敢下手
4. **Sonar / 认知复杂度** 指标持续红灯，合规审查（等保 / Sonar Quality Gate）卡点

已有 ADR-0001 明确标注 `AITutorWorkflowAdminServiceImpl` 为"全项目最大债务"，本 TODO 是其落地路径。

## 一、总览（优先级矩阵）

| 级别 | 文件 | 行数 | 目标拆成 | 预估 | 建议季度 |
|-----|------|------|---------|------|---------|
| 🔴 P0 | `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java` | 3789 | 5 个 service | 2-3 周 | 2026-Q3 |
| 🔴 P0 | `frontend/src/pages/oj/views/problem/Problem.vue` | 3481 | 6 组件 + 3 composable | 3-4 周 | 2026-Q3 |
| 🔴 P1 | `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java` | 2087 | 5 个 service | 1-2 周 | 2026-Q3 末 |
| 🔴 P1 | `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue` | 2088 | 7-9 子组件 | 2-3 周 | 2026-Q3 末 |
| 🔴 P1 | `frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue` | 2293 | 4-5 子组件 | 2 周 | 2026-Q4 初 |
| 🟠 P2 | `backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java` | 2767 | 3-4 service | 1-2 周 | 2026-Q4 |
| 🟠 P2 | `backend/src/main/java/com/alethicode/service/impl/AdminProblemCommandServiceImpl.java` | 1973 | 已有 domain 子包，继续搬 | 1 周 | 2026-Q4 |
| 🟠 P2 | `backend/src/main/java/com/alethicode/service/impl/AccountServiceImpl.java` | 1593 | 4 个 service | 1 周 | 2026-Q4 |
| 🟠 P2 | `frontend/src/pages/admin/views/general/LanguagePackInit.vue` | 1859 | 5 个阶段组件 | 1-2 周 | 2026-Q4 |
| 🟠 P2 | `frontend/src/pages/oj/views/user/LearnerNotebook.vue` | 1589 | 4 子组件 | 1 周 | 2026-Q4 |
| 🟠 P2 | `frontend/src/pages/oj/views/problem/workflowStateMachine.js` | 1489 | 5 个模块 | 1 周 | 2026-Q4 |
| 🟡 P3 | `ExampleExtractionServiceImpl.java` | 1769 | 3 service | 1 周 | 2027-Q1 |
| 🟡 P3 | `ClassroomAiProblemService.java` | 1714 | 3 service | 1 周 | 2027-Q1 |
| 🟡 P3 | `KcExtractionServiceImpl.java` | 1196 | 3 service | 0.5 周 | 2027-Q1 |
| 🟡 P3 | `LanguagePackQaServiceImpl.java` | 1165 | 3 service（已有部分拆） | 0.5 周 | 2027-Q1 |
| 🟡 P3 | `ProblemGenerationServiceImpl.java` | 1033 | 3 service（已有部分拆） | 0.5 周 | 2027-Q1 |

## 二、通用执行规则（每次拆分前先读）

1. **先写 characterization tests**：把当前类的行为锁成测试再动刀，避免"重构引入回归"
2. **提前基线压测**：P0 / P1 类属于热路径，先跑一次 `deploy/loadtest/k6/*.js`，记录 p95 / p99 / 吞吐
3. **保留 @Transactional 边界**：Spring AOP 只对 `public` 方法代理；拆出的新 class 记得把要保持事务的方法声明为 public，并确认调用方是从 bean 外部调用
4. **一个 PR 只拆一个类**：merge 冲突风险随类大小指数增长
5. **每次拆完写 ADR**：命名 `docs/adr/NNNN-split-<filename>.md`，记录旧 → 新的方法映射表
6. **CHANGELOG 同步**：加 `[后端/重构]` / `[前端/重构]` 条目
7. **不允许同时做功能变更**：拆分期禁止修逻辑；功能变更放下一个 PR
8. **保留 `git blame` 上下文**：使用 `git mv` + 分步提交；必要时 `.git-blame-ignore-revs`
9. **外部调用方保持兼容**：新 class 由门面（facade）聚合，接口不变；业务类无感知
10. **拆分完成 DoD**：
    - 原文件行数 ≤ 300 行，或者完全删除
    - 新类每个 ≤ 500 行
    - 单测覆盖率不下降
    - 基线压测 p95 不劣化 > 5%
    - ADR 已写
    - CHANGELOG 已记

---

## 三、P0 — AITutorWorkflowAdminServiceImpl（3789 行）

### 3.1 现状

- 路径：`backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- 行数：3789（全项目最大 Java 类）
- 实现的接口：`com.alethicode.service.aitutor.AITutorWorkflowDomainService`（及一些非本域接口）
- 依赖：`LearnerProfileProjector`、`MasteryService`、`LearnerMemoryService`、`CrossCourseProfileService`、`CoursewareRetrievalService`、`SimilarErrorRetrievalService`、`EvidencePackAssembler`、`TutorActionPolicy`、LangGraph 相关 client、若干 Admin KC / misconception 服务

### 3.2 职责混杂的痛点

当前类同时承担：

1. Session CRUD（create / get / delete）
2. Phase / event 迁移校验
3. Evidence pack 组装
4. LLM 节点输出（调 `AiModelGateway`）
5. 卡片 schema 校验
6. Checkpoint 保存 / 恢复
7. Trace / eval / rollout（bandit / OPE / governance）
8. Human interrupt
9. Transfer problem 草稿 + 落库（现在部分已走 `tutor_graph`）
10. Admin KC / misconception 查询转发
11. 部分 agent 指标统计

第 ADR-0001 已说明大部分 "workflow 执行"职责应迁移到 LangGraph；但 Admin 层查询 / 评估 / 治理仍然留在 Java，需要进一步拆分。

### 3.3 目标拆分

```
AITutorWorkflowAdminServiceImpl (Facade, <=300 行)
├── AITutorAdminSessionService       (session CRUD, <=500 行)
├── AITutorAdminEventService         (event + transition 校验, <=500 行)
├── AITutorAdminEvidenceService      (evidence pack 组装 + card schema, <=500 行)
├── AITutorAdminRolloutService       (bandit / OPE / governance, <=500 行)
└── AITutorAdminTraceService         (trace / eval / 指标, <=500 行)
```

现有独立的 `EvidencePackAssembler` / `TutorActionPolicy` 继续保留。

### 3.4 拆分步骤

- [ ] 1. 给当前 Impl 的每个 public method 加 `characterization test`（用实际 prompt fixture 跑通 → 断言输出 JSON 结构）
- [ ] 2. 统计 method × domain 映射表：每个 public method 按上述 5 个 domain 打标签（可以放在 ADR）
- [ ] 3. 新建 5 个 service 接口（`com.alethicode.service.aitutor.admin.*Service`），每个有空实现
- [ ] 4. 按 domain 把 method 从 Impl 搬进对应新 service；保留 Impl 作为门面，内部 delegate
- [ ] 5. `EvidencePackAssembler` 的构造 new 改为 Spring 注入
- [ ] 6. 跑全量后端测试 + tutor workflow 集成测试
- [ ] 7. 把 Impl 内残留的 workflow **执行层**方法确认已被 `tutor_graph` 替代后，标 `@Deprecated` 或删除
- [ ] 8. ADR + CHANGELOG
- [ ] 9. SonarQube 重新跑一次，确认 cognitive complexity 降下来

### 3.5 依赖 / 风险

- **事务边界**：原 Impl 大量 `@Transactional` 方法；拆分后新 service 必须显式 `@Transactional` 且保持 propagation 兼容
- **循环依赖**：5 个 service 之间可能互相调用（trace 要记录 evidence 结果）；如有循环，用 `ObjectProvider<T>` 延迟注入
- **测试 mock 数量翻倍**：以前 mock 一个 Impl，现在要 mock 5 个——在测试 base class 里建好 `@MockBean` 组合
- **性能**：纯拆分不应影响性能；但多一层门面方法调用可能 ns 级开销，忽略不计
- **ADR-0001 依赖**：拆分前确认 LangGraph workflow 真的接管了对应流程，否则会把死代码也拆成 5 份

### 3.6 工作量预估

- characterization test：3-4 天
- 实际拆分 + delegate：5-7 天
- 测试联调：2-3 天
- ADR + review：1-2 天
- 合计：**2-3 周**，1 个 senior 工程师主导

---

## 四、P0 — frontend Problem.vue（3481 行）

### 4.1 现状

- 路径：`frontend/src/pages/oj/views/problem/Problem.vue`
- 行数：3481（全项目最大 Vue 组件）
- 负责：单一学生做题页，同时驱动 `UnifiedAgentPanel.vue`

### 4.2 职责混杂的痛点

当前一个 SFC 同时持有：

1. 编辑器 wrapper（CodeMirror 5/6 切换、字体 / 主题 / 快捷键）
2. 题目展示（Markdown / LaTeX / 图片）
3. 提交表单 + 提交状态 + 重试
4. 导学 Agent Panel 的父级数据源（驱动 `UnifiedAgentPanel.vue` 2088 行）
5. WebSocket / 判题实时回显
6. `runtime_event` 消费（通过 workflowStateMachine.js 混入）
7. Checkpoint / Interrupt UI 开关
8. 移动端布局切换
9. 多语言切换（Python3/C++/Java…）
10. 快捷键 / 无障碍

一个 bug 修复经常要跳读 3400 行，合并冲突高频。

### 4.3 目标拆分

```
Problem.vue (容器 orchestrator, <=300 行，只做 slot + data source 注入)
├── <ProblemStatement />        <- 题面 / 样例 / 描述 / 提示
├── <ProblemEditor />           <- CodeMirror wrapper + 语言切换 + 字体
├── <ProblemSubmissionPanel />  <- 提交按钮 + 历史 + 状态机
├── <ProblemAgentHost />        <- UnifiedAgentPanel 的新家（配合本 P0 同步拆分）
├── <ProblemMobileLayout />     <- 移动端专用布局
└── composable:
    ├── useProblemData.js        <- 拉题目元数据、语言包、KC
    ├── useSubmission.js         <- 提交生命周期 / 判题 / 历史
    └── useWorkflow.js           <- 从 workflowStateMachine 收敛的业务封装
```

### 4.4 拆分步骤

- [ ] 1. 在 `frontend/src/pages/oj/views/problem/components/` 新建 5 个空组件 + 3 个 composable
- [ ] 2. 先把 `<template>` 拆成 5 个 `<component />`，每个 props 先全传（"暴力 props"）
- [ ] 3. 把对应的 `data()` / `computed` / `methods` 一点点迁进新组件
- [ ] 4. 把跨组件共享的逻辑移到 composable（`useSubmission` 先行，因为独立性最强）
- [ ] 5. `workflowStateMachine.js` 不动逻辑，只把 data / methods 拆到 composable 化的 `useWorkflow.js` 里包一层
- [ ] 6. 移动端布局单独拆成 `ProblemMobileLayout.vue`，用 `v-if="isMobile"` 在容器里切换
- [ ] 7. 每一阶段都跑 `npm test`；E2E smoke 保留
- [ ] 8. ADR + CHANGELOG

### 4.5 风险 / 缓解

- **响应式丢失**：Vue 2 / 3 的 `reactive` / `ref` 在跨 composable 时可能失去响应；改时用 `toRefs` 确保解构后仍响应
- **CSS 作用域**：scoped style 可能因组件拆分而失效；每个新组件自带 scoped
- **快捷键 / 焦点**：原来在 `mounted` 绑定 document；拆分时用 `@keydown.stop` 或 composable 统一管理
- **测试覆盖**：Vitest 单测 + Playwright E2E 都要跑；保留原 visual regression 截图对比
- **跟 `UnifiedAgentPanel.vue` 同步拆分**：两者耦合深，建议同一 sprint 两个 PR 前后脚合并

### 4.6 工作量

- 准备 + 测试基线：2-3 天
- 组件拆分：2 周
- Composable 抽离：3-5 天
- 回归测试 + bug 修：1 周
- 合计：**3-4 周**，1 个前端高级 + 1 个 reviewer

---

## 五、P1 — SubmissionServiceImpl（2087 行）

### 5.1 现状
- 路径：`backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`
- 行数：2087
- 已有半独立领域：`submission/SubmissionCommandDomainService` / `SubmissionQueryDomainService` / `SubmissionJudgeDispatchDomainService`，但 Impl 仍承担主要逻辑

### 5.2 职责混杂
1. 创建 submission（包含 quota / frequency check）
2. 更新 submission result（从 judge server 回调）
3. 判题 dispatcher（发到 Judge Server / 重试）
4. Submission 查询 / 分页 / 过滤
5. Rejudge 批量
6. 分析（AC 率 / 语言分布 / 错误聚类）
7. Webhook 通知

### 5.3 目标拆分
```
SubmissionServiceImpl (门面 <=300 行)
├── SubmissionCreateService       - quota / frequency / anti-spam + insert
├── SubmissionJudgeDispatchService - 发判题 + retry / failover
├── SubmissionResultApplyService   - judge server 回调 → 更新 DB + 触发事件
├── SubmissionQueryService         - 列表 / 详情 / 聚合
├── SubmissionRejudgeService       - 批量重判
└── SubmissionAnalyticsService     - AC 率 / 语言分布（纯只读）
```

### 5.4 拆分步骤
- [ ] 1. 跑基线 k6（`submission-surge.js`）记录 p95
- [ ] 2. characterization test：各 public method 用真实 DB 数据做 fixture
- [ ] 3. 按上述 6 个目标 service 建接口
- [ ] 4. 按 method → service 分配；老方法留 facade 调新实现
- [ ] 5. `@Transactional` 标注保持原 propagation
- [ ] 6. 跑 k6 + 集成测试；对比 p95 ≤ 5% 回归
- [ ] 7. ADR + CHANGELOG

### 5.5 风险
- **热路径**：提交路径每秒多次触发；拆分多一层调用，验证 p95 不退化
- **事务**：`createSubmission` 要在同一事务里调 `judgeDispatch`，拆分后得保持 `REQUIRED`
- **Judge Server 回调**：顺序必须 `update → trigger event`；不要拆到两个服务导致 race

### 5.6 工作量
1-2 周，1 senior。

---

## 六、P1 — UnifiedAgentPanel.vue（2088 行）

### 6.1 现状
- 路径：`frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- 行数：2088
- 负责 AI 导学面板所有渲染

### 6.2 职责混杂
1. 7 种卡片渲染（problem_guide / ideate_analysis / execution_trace_explainer / error_diagnosis / post_ac / transfer_problem / knowledge_review / ai_reply）
2. 对话气泡 + 聊天输入
3. Quick action 按钮（题目导读 / 错误诊断 / 迁移练习…）
4. Checkpoint 列表 + 恢复 UI
5. Interrupt 确认 UI
6. Plan / Steering UI（已半废）
7. Agent 头像 + 角色切换

### 6.3 目标拆分
```
UnifiedAgentPanel.vue (容器 <=300 行)
├── AgentPanelCards/              <- 每种卡片一个文件，<=400 行
│   ├── ProblemGuideCard.vue
│   ├── IdeateAnalysisCard.vue
│   ├── ExecutionTraceExplainerCard.vue
│   ├── ErrorDiagnosisCard.vue   (已有，检查是否要同步调整)
│   ├── PostAcCard.vue
│   ├── TransferProblemCard.vue
│   └── KnowledgeReviewCard.vue
├── AgentPanelChat.vue            <- 对话气泡 + 输入
├── AgentPanelQuickActions.vue    <- 按钮组 + 可用 action 过滤
├── AgentPanelCheckpoint.vue      <- checkpoint 列表 + 恢复
└── AgentPanelInterrupt.vue       <- interrupt 确认
```

### 6.4 拆分步骤
- [ ] 1. 每种卡片一个 snapshot test（Vitest + happy-dom）
- [ ] 2. 卡片按类型抽出；容器只负责路由到对应组件
- [ ] 3. `CARD_TYPES` 改成卡片组件的 dispatch map（key 对应 type）
- [ ] 4. Chat / QuickActions / Checkpoint / Interrupt 独立
- [ ] 5. 建一个 `AgentPanelCardRegistry.ts`（或 .js）统一导出，保证新卡片类型能"注册式"加
- [ ] 6. Storybook / Chromatic 视觉回归（可选）
- [ ] 7. ADR + CHANGELOG

### 6.5 工作量
2-3 周。最好跟 `Problem.vue` 同一 sprint 或错开一个 sprint。

---

## 七、P1 — LanguagePackQaPage.vue（2293 行）

### 7.1 现状
- 路径：`frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue`
- 2293 行

### 7.2 职责
1. QA 对话
2. 知识索引 / 章节导航
3. 引用 citation 展示（pdf / slide 跳转）
4. 管理员模式（编辑 / 删除 QA）
5. 筛选 / 搜索
6. 历史 / pinned
7. 多语言切换

### 7.3 目标
```
LanguagePackQaPage.vue (<=300)
├── LanguagePackQaChat.vue
├── LanguagePackQaIndex.vue       <- 章节 / KC 导航
├── LanguagePackQaCitation.vue    <- PDF / slide 跳转
├── LanguagePackQaFilters.vue     <- 搜索 / tag 过滤
└── LanguagePackQaHistory.vue     <- 历史 / pinned
```

### 7.4 工作量
2 周。

---

## 八、P2 — AITutorServiceImpl（2767 行）

### 8.1 现状
- 路径：`backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java`
- 2767 行
- 已有 domain 接口：`AITutorSessionDomainService` / `AITutorAnalyticsDomainService` / `AITutorKnowledgeDomainService`，但 Impl 仍承担主要逻辑

### 8.2 职责
1. AI Tutor 主会话（非 LangGraph 路径）
2. 状态机（legacy，预期被 `AITutorWorkflowAdminServiceImpl` 或 tutor_graph 替代）
3. 指标 / 评估
4. 学情画像聚合（delegate 给 `LearnerProfileProjector`）
5. 题库推荐

### 8.3 目标
```
AITutorServiceImpl (facade <=300)
├── AITutorConversationService     <- 对话驱动
├── AITutorMetricsService          <- 指标 / 评估
├── AITutorRecommendationService   <- 题库推荐
└── AITutorLearnerAdapter          <- 调 LearnerProfileProjector 的门面
```

### 8.4 步骤要点
- [ ] 先确认哪些方法已被 LangGraph workflow 或 `AITutorWorkflowAdminServiceImpl` 替代 → 标 `@Deprecated` 走 sunset
- [ ] 剩余按上述分类
- [ ] 注意 `AITutorServiceImpl` 的 `StateMachine` 相关方法应 **删除**，不再拆分

### 8.5 工作量
1-2 周。

---

## 九、P2 — AccountServiceImpl（1593 行）

### 9.1 职责
1. 注册 / 登录 / 登出
2. 密码重置 / 改密
3. 邮箱 / 短信验证码
4. 档案 / 头像
5. 密钥管理（API key / SSH key）
6. 三方登录（保留 hook）

### 9.2 目标
```
AccountServiceImpl (facade <=300)
├── AccountAuthService           <- 注册 / 登录 / 登出（已有 AccountAuthDomainService，可合并）
├── AccountCredentialService     <- 密码 / 验证码 / MFA
├── AccountProfileService        <- 档案 / 头像（已有 AccountProfileDomainService）
└── AccountKeyService            <- API key / SSH key / 三方 OAuth
```

### 9.3 工作量
1 周。注意 session / CSRF / cookie 处理保持原 security filter 不变。

---

## 十、P2 — AdminProblemCommandServiceImpl（1973 行）

### 10.1 现状
已有 `adminproblemcommand/` 子包的 domain service（Mutation / Import / Export / Fps）；Impl 仍承担协调 + 部分直接实现。

### 10.2 目标
- 把 Impl 中残留的 **直接** CRUD 逻辑按 domain 搬进 `AdminProblemMutationDomainServiceImpl` 等
- Impl 退化为 thin facade，只做 dispatch

### 10.3 工作量
1 周。

---

## 十一、P2 — 前端中型组件（3 个并行）

### 11.1 LanguagePackInit.vue（1859 行）
按上传 → 文档解析 → KC 抽取 → 审核 → 发布的 **5 个阶段** 拆分：

```
LanguagePackInit.vue (<=300)
├── UploadStep.vue
├── DocumentParseStep.vue
├── KcReviewStep.vue
├── ContentReviewStep.vue
└── PublishStep.vue
```

工作量：1-2 周

### 11.2 LearnerNotebook.vue（1589 行）
```
LearnerNotebook.vue (<=300)
├── NotebookList.vue
├── NotebookDetail.vue
├── NotebookEditor.vue
└── NotebookInsights.vue
```
工作量：1 周

### 11.3 workflowStateMachine.js（1489 行）
非 SFC，但属于单文件混合逻辑：

```
workflowStateMachine.js (facade mixin, <=200)
├── workflowApi.js          <- API 调用封装
├── workflowWebSocket.js    <- WS 连接 / 重连 / watchdog
├── workflowActions.js      <- action 策略（filterWorkflowActions 等）
├── workflowCache.js        <- 已有
└── workflowSessionLifecycle.js  <- session init / restore / delete
```

工作量：1 周

---

## 十二、P3 — Language Pack / Classroom / Example

这些类虽超过 1000 行，但领域内聚性尚可，可在 2027-Q1 根据业务发展按需拆分：

- [ ] `ExampleExtractionServiceImpl` (1769) → `ExampleExtract` / `ExampleVectorize` / `ExampleDeduplicate`
- [ ] `ClassroomAiProblemService` (1714) → `ClassroomAiProblemGeneration` / `Review` / `Stats`
- [ ] `KcExtractionServiceImpl` (1196) → `KcExtract` / `KcPrerequisite`（已独立）/ `KcChapterMap`
- [ ] `LanguagePackQaServiceImpl` (1165) → `LanguagePackQaOrchestrator` / `AnswerSynthesis`（已独立）/ `Citation`
- [ ] `ProblemGenerationServiceImpl` (1033) → `ProblemGenerationOrchestrator` / `ProblemValidation`（已独立）/ `JudgeCheck`（已独立）

---

## 十三、每次拆分 Definition of Done

一次巨石拆分 PR 合并前必须全部满足：

- [ ] 原文件行数 ≤ 300（facade）或被完全删除
- [ ] 新类每个 ≤ 500 行
- [ ] `characterization test` 100% 通过
- [ ] 单测覆盖率不降
- [ ] 基线 k6 压测 p95 回归 ≤ 5%
- [ ] 事务边界 / propagation 手工 review
- [ ] 循环依赖用 `jdeps` 或 IDE 扫描确认无
- [ ] SonarQube cognitive complexity 全部 ≤ 15
- [ ] ADR `docs/adr/NNNN-split-<file>.md` 已写
- [ ] CHANGELOG 有 `[后端/重构]` 或 `[前端/重构]` 条目
- [ ] `git blame` 过滤（`.git-blame-ignore-revs` 追加本次重构 commit hash）

---

## 十四、全局进度追踪

| 文件 | Owner | Status | PR | 预计完成 |
|------|-------|--------|----|---------|
| AITutorWorkflowAdminServiceImpl | TBD | Not started | - | 2026-Q3 |
| Problem.vue | TBD | Not started | - | 2026-Q3 |
| SubmissionServiceImpl | TBD | Not started | - | 2026-Q3 末 |
| UnifiedAgentPanel.vue | TBD | Not started | - | 2026-Q3 末 |
| LanguagePackQaPage.vue | TBD | Not started | - | 2026-Q4 初 |
| AITutorServiceImpl | TBD | Not started | - | 2026-Q4 |
| AccountServiceImpl | TBD | Not started | - | 2026-Q4 |
| AdminProblemCommandServiceImpl | TBD | Not started | - | 2026-Q4 |
| LanguagePackInit.vue | TBD | Not started | - | 2026-Q4 |
| LearnerNotebook.vue | TBD | Not started | - | 2026-Q4 |
| workflowStateMachine.js | TBD | Not started | - | 2026-Q4 |
| ExampleExtractionServiceImpl | TBD | Not started | - | 2027-Q1 |
| ClassroomAiProblemService | TBD | Not started | - | 2027-Q1 |
| KcExtractionServiceImpl | TBD | Not started | - | 2027-Q1 |
| LanguagePackQaServiceImpl | TBD | Not started | - | 2027-Q1 |
| ProblemGenerationServiceImpl | TBD | Not started | - | 2027-Q1 |

---

## 十五、下一位工程师的阅读路径

1. 读本文件（全局路线）
2. 读 `docs/adr/0001-langgraph-tutor-workflow.md`（为什么 AITutorWorkflowAdminServiceImpl 会长成今天这样）
3. 读 `docs/adr/0002-spring-ai-gateway.md`（LlmClient → AiModelGateway 的拆分范式）
4. 读 `docs/adr/0003-ai-runtime-integration-handoff.md`（今天已完成的收口全貌）
5. 读 `docs/release-notes/2026-04-21-upgrade-handbook.md`（部署清单 + 运维指引）
6. 按 P0 开始

每完成一个 item，把 "Status" 从 Not started 改成 In progress → In review → Done，并填写 PR 链接。
